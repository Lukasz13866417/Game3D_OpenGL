package com.example.game3d_opengl.game.terrain_api.terrainutil;

import android.opengl.GLES20;
import android.os.Build;

import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * A ring-buffer (deque) of (left,right) points rendered as a triangle strip ribbon.
 *
 * VBO layout: per pair two vec3 vertices: [Li.xyz, Ri.xyz]
 * EBO (indices): [L0, R0, L1, R1, ..., L(n-1), R(n-1)] -> GL_TRIANGLE_STRIP
 *
 * CPU mirror stores all pairs so geometry can be restored after EGL context loss.
 * ES 3.x mapping path (glMapBufferRange) is used opportunistically; ES 2.0 falls back
 * to glBufferSubData, which is fine for 24-byte updates per push.
 */
public class TerrainLandscapeRenderer implements GPUResourceOwner {

    // ---- Tunables -----------------------------------------------------------

    /** Default maximum number of (L,R) pairs stored in the deque. */
    private static final int DEFAULT_CAPACITY_PAIRS = 2048;

    /** If true, when the deque is full, pushBack will evict the oldest pair (popFront). */
    private static final boolean EVICT_OLDEST_ON_OVERFLOW = true;

    // ---- Constants ----------------------------------------------------------

    private static final int FLOATS_PER_VERTEX = 4;            // vec3 position + mask
    private static final int BYTES_PER_FLOAT   = 4;
    private static final int BYTES_PER_VERTEX  = FLOATS_PER_VERTEX * BYTES_PER_FLOAT; // 12
    private static final int VERTICES_PER_PAIR = 2;            // [left, right]
    private static final int BYTES_PER_PAIR    = VERTICES_PER_PAIR * BYTES_PER_VERTEX;// 24
    private static final int BYTES_PER_SHORT   = 2;

    // ---- GL Objects (created only when a GL context is current) --------------

    private int vboId = 0; // vertex buffer for positions+mask (vec4)
    private int eboId = 0; // element/index buffer (GL_UNSIGNED_SHORT)

    // ---- Ring buffer bookkeeping -------------------------------------------

    private final int capacityPairs; // <= 32767 (2*capacity fits in 16-bit indices)
    private int headPair = 0;        // index (0..capacityPairs-1) of the logical front
    private int sizePairs = 0;       // number of pairs currently stored

    // ---- CPU mirror (survives context loss) --------------------------------

    private final ByteBuffer  cpuMirrorBB; // capacityPairs * BYTES_PER_PAIR
    private final FloatBuffer cpuMirrorFB; // view of cpuMirrorBB as floats

    // ---- Scratch buffers (for uploads / index rebuilds) ---------------------

    private final ByteBuffer  pairUploadScratch;         // 24 bytes
    private final FloatBuffer pairUploadScratchFloats;   // 6 floats
    private final ShortBuffer indicesScratch;            // up to 2*capacity indices

    // ---- Caps / dirty flags -------------------------------------------------

    private boolean canMapES3 = false;  // set after we have a current GL context
    private boolean indicesDirty = true;

    private final InfillShaderArgs.VS vsArgs = new InfillShaderArgs.VS();
    private final InfillShaderArgs.FS fsArgs = new InfillShaderArgs.FS();

    // ---- Constructors (no GL calls here!) ----------------------------------

    public TerrainLandscapeRenderer() {
        this(DEFAULT_CAPACITY_PAIRS);
    }

    public TerrainLandscapeRenderer(int capacityPairs) {
        if (capacityPairs <= 0) throw new IllegalArgumentException("capacityPairs must be > 0");
        if (capacityPairs > 32767) throw new IllegalArgumentException("capacityPairs must be <= 32767");

        this.capacityPairs = capacityPairs;

        // CPU mirror
        cpuMirrorBB = ByteBuffer.allocateDirect(capacityPairs * BYTES_PER_PAIR).order(ByteOrder.nativeOrder());
        cpuMirrorFB = cpuMirrorBB.asFloatBuffer();

        // Scratch for pair uploads (ES2 path)
        pairUploadScratch = ByteBuffer.allocateDirect(BYTES_PER_PAIR).order(ByteOrder.nativeOrder());
        pairUploadScratchFloats = pairUploadScratch.asFloatBuffer();

        // Scratch for indices
        indicesScratch = ByteBuffer
                .allocateDirect(capacityPairs * VERTICES_PER_PAIR * BYTES_PER_SHORT)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
    }

    // ---- Public API ---------------------------------------------------------

    /** Append a new (L,R) pair to the back. Evicts front if at capacity and eviction enabled. */
    public void pushBack(Vector3D newLeft, Vector3D newRight) {
        if (sizePairs == capacityPairs) {
            if (EVICT_OLDEST_ON_OVERFLOW) popFront(); else return;
        }
        int pairIndex = (headPair + sizePairs) % capacityPairs;

        // 1) CPU mirror (authoritative source)
        writePairIntoCpuMirror(pairIndex, newLeft, newRight);

        // 2) GPU cache (if GL buffers exist, update them too)
        if (vboId != 0) {
            writePairIntoVbo(pairIndex, newLeft, newRight, /*maskLeft=*/1f, /*maskRight=*/1f);
        }

        sizePairs++;
        indicesDirty = true;
    }

    /** Remove newest pair at the back, if any. */
    public void popBack() {
        if (sizePairs == 0) return;
        sizePairs--;
        indicesDirty = true;
    }

    /** Remove oldest pair at the front, if any. */
    public void popFront() {
        if (sizePairs == 0) return;
        headPair = (headPair + 1) % capacityPairs;
        sizePairs--;
        indicesDirty = true;
    }

    public int getSize() { return sizePairs; }

    /**
     * Draw as a triangle strip ribbon using the currently bound InfillShaderPair.
     * Assumes the caller has bound the program and uploaded uniforms.
     */
    public void draw(FColor color, float[] vp) {
        if (sizePairs < 2) return;
        // TODO fail fast would be better but might require more changes.
        // Lazily create GL buffers on first use (also covers initial startup)
        if (vboId == 0 || eboId == 0) {
            reloadGPUResourcesRecursively();
            if (vboId == 0 || eboId == 0) return;
        }

        // Bind and configure the shader for this draw
        TerrainRibbonShaderPair shader = TerrainRibbonShaderPair.sharedShader;
        shader.setAsCurrentProgram();

        vsArgs.mvp   = vp;  // 16-length float array, column-major
        fsArgs.color = color;    // holds float[4] RGBA
        shader.setArgs(vsArgs, fsArgs);
        shader.transferArgsToGPU();

        // Issue the geometry draw with VBO/EBO
        drawInternal();
    }

    // Does the VBO/EBO work; assumes the shader program is already current
    private void drawInternal() {
        ensureIndexBufferUpToDate();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        TerrainRibbonShaderPair.sharedShader.enableAndPointVertexAttribs();

        boolean cullEnabled = GLES20.glIsEnabled(GLES20.GL_CULL_FACE);
        if (cullEnabled) GLES20.glDisable(GLES20.GL_CULL_FACE);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, eboId);
        final int indexCount = sizePairs * VERTICES_PER_PAIR; // 2 per pair
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, indexCount, GLES20.GL_UNSIGNED_SHORT, 0);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        TerrainRibbonShaderPair.sharedShader.disableVertexAttribs();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        if (cullEnabled) GLES20.glEnable(GLES20.GL_CULL_FACE);
    }

    /** Mark a gap between the last and previous pair by setting both masks to 0 and updating VBO. */
    public void markGapBetweenLastTwoPairs() {
        if (sizePairs < 2) return;
        int lastRingIndex = (headPair + sizePairs - 1) % capacityPairs;
        int prevRingIndex = (headPair + sizePairs - 2 + capacityPairs) % capacityPairs;
        setPairMaskInternal(prevRingIndex, 0f, 0f);
        setPairMaskInternal(lastRingIndex, 0f, 0f);
    }

    private void setPairMaskInternal(int ringIndex, float maskL, float maskR) {
        final int floatsPerPair = FLOATS_PER_VERTEX * VERTICES_PER_PAIR; // 8
        final int base = ringIndex * floatsPerPair;
        // Update CPU mirror masks
        cpuMirrorFB.put(base + 3, maskL);
        cpuMirrorFB.put(base + 7, maskR);
        // Update GPU VBO for this pair if available
        if (vboId != 0) {
            uploadOnePairFromCpu(ringIndex);
        }
    }

    private void uploadOnePairFromCpu(int ringIndex) {
        final int byteOffset = ringIndex * BYTES_PER_PAIR;
        cpuMirrorBB.position(byteOffset);
        cpuMirrorBB.limit(byteOffset + BYTES_PER_PAIR);
        ByteBuffer slice = cpuMirrorBB.slice().order(ByteOrder.nativeOrder());
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, byteOffset, BYTES_PER_PAIR, slice);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        cpuMirrorBB.clear();
    }


    /** Delete GL buffers. Safe to call even if not created yet. Call on GL thread. */
    @Override
    public void cleanupGPUResourcesRecursively() {
        int[] id = new int[1];
        if (vboId != 0) { id[0] = vboId; GLES20.glDeleteBuffers(1, id, 0); vboId = 0; }
        if (eboId != 0) { id[0] = eboId; GLES20.glDeleteBuffers(1, id, 0); eboId = 0; }
    }

    @Override
    public void reloadGPUResourcesRecursively() {
        initGlBuffers();
        detectCaps();        // detect ES3 mapping availability

        // Repopulate VBO from the CPU mirror in at most two uploads (to handle wrap)
        if (sizePairs > 0) {
            int firstRun = Math.min(sizePairs, capacityPairs - headPair);
            uploadContiguousPairsToVbo(headPair, firstRun);
            int remaining = sizePairs - firstRun;
            if (remaining > 0) uploadContiguousPairsToVbo(0, remaining);
        }

        // Rebuild the index buffer for the current deque window
        indicesDirty = true;
        ensureIndexBufferUpToDate();
    }

    // ---- Internals ----------------------------------------------------------

    private void initGlBuffers() {
        // Create buffers
        int[] ids = new int[2];
        GLES20.glGenBuffers(2, ids, 0);
        vboId = ids[0];
        eboId = ids[1];

        // Allocate immutable sizes
        final int totalVertexBytes = capacityPairs * BYTES_PER_PAIR;
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, totalVertexBytes, null, GLES20.GL_DYNAMIC_DRAW);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, eboId);
        final int maxIndices = capacityPairs * VERTICES_PER_PAIR;
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, maxIndices * BYTES_PER_SHORT, null, GLES20.GL_STREAM_DRAW);

        // Unbind
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private void detectCaps() {
        // Avoid class-verify issues on old devices by checking API first.
        canMapES3 = false;
        if (Build.VERSION.SDK_INT >= 18) {
            String v = GLES20.glGetString(GLES20.GL_VERSION); // current GL context required
            if (v != null && v.startsWith("OpenGL ES 3")) {
                canMapES3 = true;
            }
        }
    }

    private void writePairIntoCpuMirror(int pairIndex, Vector3D L, Vector3D R) {
        final int floatOffset = pairIndex * (FLOATS_PER_VERTEX * VERTICES_PER_PAIR); // 8 floats per pair
        cpuMirrorFB.position(floatOffset);
        cpuMirrorFB.put(L.x).put(L.y).put(L.z).put(1f);
        cpuMirrorFB.put(R.x).put(R.y).put(R.z).put(1f);
    }

    private void uploadContiguousPairsToVbo(int startPair, int pairCount) {
        if (pairCount <= 0) return;
        final int byteOffset = startPair * BYTES_PER_PAIR;
        final int byteSize   = pairCount * BYTES_PER_PAIR;

        cpuMirrorBB.position(byteOffset);
        cpuMirrorBB.limit(byteOffset + byteSize);
        ByteBuffer slice = cpuMirrorBB.slice().order(ByteOrder.nativeOrder());

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, byteOffset, byteSize, slice);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        cpuMirrorBB.clear(); // restore for next use
    }

    private void writePairIntoVbo(int pairIndex, Vector3D L, Vector3D R, float maskL, float maskR) {
        final int byteOffset = pairIndex * BYTES_PER_PAIR;

        if (canMapES3) {
            // Use ES3 mapping ONLY on API >= 18 and GL 3.x devices.
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
            ByteBuffer mapped = ES3.mapRange(
                    GLES20.GL_ARRAY_BUFFER,
                    byteOffset,
                    BYTES_PER_PAIR,
                    ES3.WRITE | ES3.INVALIDATE_RANGE);
            if (mapped != null) {
                mapped.order(ByteOrder.nativeOrder()).asFloatBuffer()
                        .put(L.x).put(L.y).put(L.z).put(maskL)
                        .put(R.x).put(R.y).put(R.z).put(maskR)
                        .rewind();
                ES3.unmap(GLES20.GL_ARRAY_BUFFER);
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
                return;
            }
            // If mapping failed, fall back.
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }

        // ES 2.0-safe fallback: small subdata upload
        pairUploadScratchFloats.position(0);
        pairUploadScratchFloats.put(L.x).put(L.y).put(L.z).put(maskL)
                .put(R.x).put(R.y).put(R.z).put(maskR);
        pairUploadScratch.position(0); // bytes
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, byteOffset, BYTES_PER_PAIR, pairUploadScratch);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private void ensureIndexBufferUpToDate() {
        if (!indicesDirty) return;

        final int count = sizePairs * VERTICES_PER_PAIR; // 2 per pair
        indicesScratch.clear();

        // Build [L0,R0, L1,R1, ..., L(n-1),R(n-1)] in logical order
        for (int i = 0; i < sizePairs; i++) {
            int pair = (headPair + i) % capacityPairs;
            short leftIndex  = (short) (pair * VERTICES_PER_PAIR);
            short rightIndex = (short) (leftIndex + 1);
            indicesScratch.put(leftIndex).put(rightIndex);
        }
        indicesScratch.flip();

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, eboId);
        // Orphan then upload (avoids driver stalls on some devices)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, count * BYTES_PER_SHORT, null, GLES20.GL_STREAM_DRAW);
        if (count > 0) {
            GLES20.glBufferSubData(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0, count * BYTES_PER_SHORT, indicesScratch);
        }
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        indicesDirty = false;
    }

    // ---- ES3 helper (only loaded/used when API >= 18 and GL 3.x) ------------

    private static final class ES3 {
        static final int WRITE = android.opengl.GLES30.GL_MAP_WRITE_BIT;
        static final int INVALIDATE_RANGE = android.opengl.GLES30.GL_MAP_INVALIDATE_RANGE_BIT;

        static ByteBuffer mapRange(int target, int offset, int length, int access) {
            return (ByteBuffer) android.opengl.GLES30.glMapBufferRange(target, offset, length, access);
        }
        static void unmap(int target) {
            android.opengl.GLES30.glUnmapBuffer(target);
        }
    }
}
