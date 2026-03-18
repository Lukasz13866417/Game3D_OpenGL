package com.example.game3d_opengl.game.terrain.terrain_api.terrainutil;

import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.opengl.GLES20;

import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * A ring-buffer (deque) of (left,right) points rendered as a triangle strip ribbon.
 * Will be used to draw terrain landscape, which is basically a winding road of tiles.
 * New tiles can be appended to back (far end) or popped from front (near end) or back (far end)
 * VBO layout: per pair two vec3 vertices: [Li.xyz, Ri.xyz]
 * EBO (indices): [L0, R0, L1, R1, ..., L(n-1), R(n-1)] -> GL_TRIANGLE_STRIP
 * CPU mirror stores all pairs so geometry can be restored after EGL context loss.
 * ES 3.x mapping path (glMapBufferRange) is used opportunistically;
 */
public class TerrainLandscapeRenderer implements GPUResourceOwner {

    // ---- Tunables -----------------------------------------------------------

    /** Default maximum number of (L,R) pairs stored in the deque. */
    private static final int DEFAULT_CAPACITY_PAIRS = 2048;

    private final InfillShaderArgs.VS vsArgs = new InfillShaderArgs.VS();
    private final InfillShaderArgs.FS fsArgs = new InfillShaderArgs.FS();
    private static final FColor DEFAULT_LIGHT_COLOR = new FColor(1f, 1f, 1f, 1f);

    // ---- Vertex layout (TerrainRibbonShaderPair) -----------------------------
    // per-vertex: [pos.xyz, normal.xyz, alpha] -> 7 floats
    private static final int FLOATS_PER_VERTEX = 7;
    private static final int BYTES_PER_FLOAT = 4;

    // ---- CPU-side geometry (preallocated ring buffers) -----------------------
    private static final class EdgeRingBuffer {
        private final int capacity;
        private int head = 0;  // index of first element
        private int size = 0;  // number of elements
        private int seqStart = 0; // sequence id of the head element
        private final float[] lx, ly, lz, rx, ry, rz, aL, aR;
        EdgeRingBuffer(int maxEdges) {
            this.capacity = maxEdges;
            this.lx = new float[maxEdges];
            this.ly = new float[maxEdges];
            this.lz = new float[maxEdges];
            this.rx = new float[maxEdges];
            this.ry = new float[maxEdges];
            this.rz = new float[maxEdges];
            this.aL = new float[maxEdges];
            this.aR = new float[maxEdges];
        }
        private int tailIndex() { return Math.floorMod(head + size, capacity); }
        private int lastIndex() { return Math.floorMod(head + size - 1, capacity); }
        private int indexOfOffset(int off) { return Math.floorMod(head + off, capacity); }
        private int indexOfSeq(int seqId) { return indexOfOffset(seqId - seqStart); }
        /**
         * @return true if pushing caused eviction of the oldest element (ring overwrite).
         */
        boolean pushBack(Vector3D left, Vector3D right, float alphaL, float alphaR) {
            int idx = tailIndex();
            lx[idx] = left.x; ly[idx] = left.y; lz[idx] = left.z;
            rx[idx] = right.x; ry[idx] = right.y; rz[idx] = right.z;
            aL[idx] = alphaL; aR[idx] = alphaR;
            boolean evicted = false;
            if (size < capacity) {
                size++;
            } else {
                // overwrite oldest (advance head and seqStart)
                head = Math.floorMod(head + 1, capacity);
                seqStart++;
                evicted = true;
            }
            return evicted;
        }
        void popBack() {
            if (size <= 0) return;
            size--;
        }
        void popFront() {
            if (size <= 0) return;
            head = Math.floorMod(head + 1, capacity);
            size--;
            seqStart++;
        }
        int getSize() { return size; }
        int getSeqStart() { return seqStart; }
        int getNextSeq() { return seqStart + size; }
        void getEdgeBySeq(int seqId, TmpEdge out) {
            int idx = indexOfSeq(seqId);
            out.lx = lx[idx]; out.ly = ly[idx]; out.lz = lz[idx];
            out.rx = rx[idx]; out.ry = ry[idx]; out.rz = rz[idx];
            out.aL = aL[idx]; out.aR = aR[idx];
        }

        void addOffset(float dx, float dy, float dz) {
            if (size <= 0) return;
            int idx = head;
            for (int i = 0; i < size; i++) {
                lx[idx] += dx;
                ly[idx] += dy;
                lz[idx] += dz;
                rx[idx] += dx;
                ry[idx] += dy;
                rz[idx] += dz;
                idx++;
                if (idx >= capacity) idx = 0;
            }
        }
    }

    private static final class SubStripRingBuffer {
        private final int capacity;
        private final int[] startSeq;
        private final int[] count;
        private int head = 0;
        private int size = 0;
        SubStripRingBuffer(int maxStrips) {
            this.capacity = maxStrips;
            this.startSeq = new int[maxStrips];
            this.count = new int[maxStrips];
            for (int i = 0; i < maxStrips; i++) {
                startSeq[i] = 0;
                count[i] = 0;
            }
        }
        private int tailIndex() { return Math.floorMod(head + size, capacity); }
        private int lastIndex() { return Math.floorMod(head + size - 1, capacity); }
        private int indexOfOffset(int off) { return Math.floorMod(head + off, capacity); }
        int getSize() { return size; }
        int getStartSeqAt(int offset) { return startSeq[indexOfOffset(offset)]; }
        int getCountAt(int offset) { return count[indexOfOffset(offset)]; }
        void setStartSeqAtTail(int seq) { startSeq[tailIndex()] = seq; }
        void startNewStrip(int seq) {
            if (size > 0 && count[lastIndex()] == 0) {
                // Repoint the existing empty pending strip to current seq
                startSeq[lastIndex()] = seq;
                return;
            }
            if (size < capacity) {
                int idx = tailIndex();
                startSeq[idx] = seq;
                count[idx] = 0;
                size++;
            } else {
                // overwrite oldest
                head = Math.floorMod(head + 1, capacity);
                int idx = lastIndex();
                startSeq[idx] = seq;
                count[idx] = 0;
            }
        }
        void incLastCount() {
            assert size > 0;
            count[lastIndex()]++;
        }
        void decLastCountAndMaybePop() {
            assert size > 0;
            int idx = lastIndex();
            if (count[idx] > 0) count[idx]--;
            if (count[idx] == 0) {
                size--;
            }
        }
        /**
         * Consume one edge from the front-most strip.
         * Must be called whenever the edge ring buffer removes/evicts one element from the front.
         */
        void decFirstCountAndMaybePop() {
            assert size > 0;
            if (count[head] > 0) {
                count[head]--;
                // We removed the strip's first edge, so the strip's starting seq advances.
                startSeq[head]++;
            }
            if (count[head] == 0) {
                head = Math.floorMod(head + 1, capacity);
                size--;
            }
        }
    }

    // scratch struct to avoid object churn in draw()
    private static final class TmpEdge {
        float lx, ly, lz, rx, ry, rz, aL, aR;
    }

    private final EdgeRingBuffer edgeBuf;
    private final SubStripRingBuffer stripBuf;
    private final TmpEdge tmpCurr = new TmpEdge();
    private final TmpEdge tmpPrev = new TmpEdge();
    private final TmpEdge tmpNext = new TmpEdge();

    // ---- GPU resources -------------------------------------------------------
    private int vboId = 0;
    private FloatBuffer vertexBuffer = null;
    private int vertexCapacity = 0; // in vertices, not floats
    private final int[] drawFirst;
    private final int[] drawCount;
    private int drawRangesUsed = 0;

    // ---- Constructors (no GL calls here!) ----------------------------------

    public TerrainLandscapeRenderer() {
        this(DEFAULT_CAPACITY_PAIRS);
    }

    public TerrainLandscapeRenderer(int capacityPairs) {
        // Preallocate buffers
        edgeBuf = new EdgeRingBuffer(capacityPairs);
        stripBuf = new SubStripRingBuffer(capacityPairs);
        drawFirst = new int[capacityPairs];
        drawCount = new int[capacityPairs];
        ensureVertexCapacity(capacityPairs * 2); // two vertices per pair
    }

    // ---- Public API ---------------------------------------------------------

    /** Append a new (L,R) pair to the back. Evicts front if at capacity and eviction enabled. */
    public void pushBack(Vector3D newLeft, Vector3D newRight, float alphaL, float alphaR) {
        // Ensure we have a sub-strip open
        if (stripBuf.getSize() == 0 || stripBuf.getCountAt(stripBuf.getSize() - 1) == 0) {
            stripBuf.startNewStrip(edgeBuf.getNextSeq());
        }
        boolean evicted = edgeBuf.pushBack(newLeft, newRight, alphaL, alphaR);
        // If the edge ring evicted the oldest element (overwrite), keep strip metadata in sync.
        if (evicted && stripBuf.getSize() > 0) {
            stripBuf.decFirstCountAndMaybePop();
        }
        stripBuf.incLastCount();
    }

    /** Remove newest pair at the back, if any. */
    public void popBack() {
        assert edgeBuf.getSize() > 0;
        edgeBuf.popBack();
        stripBuf.decLastCountAndMaybePop();
    }

    /** Remove oldest pair at the front, if any. */
    public void popFront() {
        assert edgeBuf.getSize() > 0;
        edgeBuf.popFront();
        stripBuf.decFirstCountAndMaybePop();
    }

    public int getSize() { return edgeBuf.getSize(); }

    public void rebasePosition(Vector3D delta) {
        if (delta == null) return;
        float dx = delta.x;
        float dy = delta.y;
        float dz = delta.z;
        if (dx == 0f && dy == 0f && dz == 0f) return;
        edgeBuf.addOffset(dx, dy, dz);
    }

    /**
     * Draw as a triangle strip ribbon using the currently bound InfillShaderPair.
     * Assumes the caller has bound the program and uploaded uniforms.
     */
    public void draw(FColor color, float[] vp, LightSource light) {
        if (edgeBuf.getSize() <= 0 || stripBuf.getSize() <= 0) return;

        // Build CPU vertex array for all sub-strips, contiguous
        int totalVertices = edgeBuf.getSize() * 2;
        ensureVertexCapacity(totalVertices);
        vertexBuffer.position(0);

        // Prepare uniforms
        vsArgs.mvp = vp;
        fsArgs.color = color;
        if (light != null && light.position != null && light.color != null) {
            fsArgs.lightX = light.position.x;
            fsArgs.lightY = light.position.y;
            fsArgs.lightZ = light.position.z;
            fsArgs.lightColor = light.color;
        } else {
            fsArgs.lightX = fsArgs.lightY = fsArgs.lightZ = 0f;
            fsArgs.lightColor = DEFAULT_LIGHT_COLOR;
        }
        // We'll do a depth pre-pass first (opaque fragments only), then a blended color pass.
        // This prevents partially transparent fragments from writing depth and causing
        // visual "mixing"/halo artifacts at strip boundaries.
        fsArgs.isDepthPass = 0;

        // Flatten vertices and record draw ranges per sub-strip (no allocations)
        int writtenVertices = 0;
        drawRangesUsed = 0;
        int stripsToDraw = stripBuf.getSize();
        for (int si = 0; si < stripsToDraw; si++) {
            int startSeq = stripBuf.getStartSeqAt(si);
            int edgeCount = stripBuf.getCountAt(si);
            // Need at least 2 edges for a triangle strip segment.
            if (edgeCount < 2) continue;
            int firstForStrip = writtenVertices;

            for (int e = 0; e < edgeCount; e++) {
                int currSeq = startSeq + e;
                edgeBuf.getEdgeBySeq(currSeq, tmpCurr);

                // Compute normal using neighbor midpoints
                float segDX = 0f, segDY = 0f, segDZ = 1f; // fallback direction
                if (edgeCount >= 2) {
                    if (e == 0) {
                        edgeBuf.getEdgeBySeq(currSeq + 1, tmpNext);
                        float mid0x = (tmpCurr.lx + tmpCurr.rx) * 0.5f;
                        float mid0y = (tmpCurr.ly + tmpCurr.ry) * 0.5f;
                        float mid0z = (tmpCurr.lz + tmpCurr.rz) * 0.5f;
                        float mid1x = (tmpNext.lx + tmpNext.rx) * 0.5f;
                        float mid1y = (tmpNext.ly + tmpNext.ry) * 0.5f;
                        float mid1z = (tmpNext.lz + tmpNext.rz) * 0.5f;
                        segDX = mid1x - mid0x;
                        segDY = mid1y - mid0y;
                        segDZ = mid1z - mid0z;
                    } else {
                        edgeBuf.getEdgeBySeq(currSeq - 1, tmpPrev);
                        float mid0x = (tmpPrev.lx + tmpPrev.rx) * 0.5f;
                        float mid0y = (tmpPrev.ly + tmpPrev.ry) * 0.5f;
                        float mid0z = (tmpPrev.lz + tmpPrev.rz) * 0.5f;
                        float mid1x = (tmpCurr.lx + tmpCurr.rx) * 0.5f;
                        float mid1y = (tmpCurr.ly + tmpCurr.ry) * 0.5f;
                        float mid1z = (tmpCurr.lz + tmpCurr.rz) * 0.5f;
                        segDX = mid1x - mid0x;
                        segDY = mid1y - mid0y;
                        segDZ = mid1z - mid0z;
                    }
                }
                float acrossX = tmpCurr.rx - tmpCurr.lx;
                float acrossY = tmpCurr.ry - tmpCurr.ly;
                float acrossZ = tmpCurr.rz - tmpCurr.lz;
                // normal = across x segDir
                float nX = acrossY * segDZ - acrossZ * segDY;
                float nY = acrossZ * segDX - acrossX * segDZ;
                float nZ = acrossX * segDY - acrossY * segDX;
                float nLen = (float) Math.sqrt(nX * nX + nY * nY + nZ * nZ);
                if (nLen > 1e-6f) {
                    nX /= nLen; nY /= nLen; nZ /= nLen;
                } else {
                    nX = 0f; nY = 1f; nZ = 0f;
                }

                // Left vertex
                vertexBuffer.put(tmpCurr.lx).put(tmpCurr.ly).put(tmpCurr.lz);
                vertexBuffer.put(nX).put(nY).put(nZ).put(tmpCurr.aL);
                // Right vertex
                vertexBuffer.put(tmpCurr.rx).put(tmpCurr.ry).put(tmpCurr.rz);
                vertexBuffer.put(nX).put(nY).put(nZ).put(tmpCurr.aR);
                writtenVertices += 2;
            }

            if (edgeCount >= 2) {
                drawFirst[drawRangesUsed] = firstForStrip;
                drawCount[drawRangesUsed] = writtenVertices - firstForStrip;
                drawRangesUsed++;
            }
        }

        if (writtenVertices == 0 || drawRangesUsed == 0) {
            GLES20.glDisable(GLES20.GL_BLEND);
            return;
        }

        // Upload to GPU VBO
        if (vboId == 0) {
            int[] ids = new int[1];
            GLES20.glGenBuffers(1, ids, 0);
            vboId = ids[0];
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        vertexBuffer.position(0);
        GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                writtenVertices * FLOATS_PER_VERTEX * BYTES_PER_FLOAT,
                vertexBuffer,
                GLES20.GL_DYNAMIC_DRAW
        );

        // Bind shader and attributes
        TerrainRibbonShaderPair shader = TerrainRibbonShaderPair.sharedShader;
        shader.setAsCurrentProgram();
        shader.enableAndPointVertexAttribs();

        // ---- Pass 1: depth pre-pass (write depth only for fully opaque fragments) ----
        // NOTE: This relies on the fragment shader discarding when vAlpha < 1.0 and isDepthPass==1.
        GLES20.glDisable(GLES20.GL_BLEND);
        // Use the default depth compare for the pre-pass.
        GLES20.glDepthFunc(GLES20.GL_LESS);
        GLES20.glDepthMask(true);
        GLES20.glColorMask(false, false, false, false);
        fsArgs.isDepthPass = 1;
        shader.setArgs(vsArgs, fsArgs);
        shader.transferUniformArgsToGPU();
        for (int i = 0; i < drawRangesUsed; i++) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, drawFirst[i], drawCount[i]);
        }

        // ---- Pass 2: color pass (blend, but don't write depth) ----
        GLES20.glColorMask(true, true, true, true);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        // IMPORTANT: after writing depth in pass 1, drawing the same geometry again needs
        // a <= test, otherwise GL_LESS will reject equal-depth fragments and everything
        // opaque becomes invisible.
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        fsArgs.isDepthPass = 0;
        shader.setArgs(vsArgs, fsArgs);
        shader.transferUniformArgsToGPU();
        for (int i = 0; i < drawRangesUsed; i++) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, drawFirst[i], drawCount[i]);
        }

        // Cleanup / restore state
        shader.disableVertexAttribs();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDepthMask(true);
        GLES20.glColorMask(true, true, true, true);
        GLES20.glDepthFunc(GLES20.GL_LESS);
    }


    public void newStrip(){
        stripBuf.startNewStrip(edgeBuf.getNextSeq());
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (vboId != 0) {
            int[] ids = new int[]{vboId};
            GLES20.glDeleteBuffers(1, ids, 0);
            vboId = 0;
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        // Recreate program and VBO
        TerrainRibbonShaderPair.sharedShader.reloadProgram();
        if (vboId == 0 && vertexCapacity > 0) {
            int[] ids = new int[1];
            GLES20.glGenBuffers(1, ids, 0);
            vboId = ids[0];
        }
    }

    // ---- Helpers -------------------------------------------------------------
    private void ensureVertexCapacity(int requiredVertices) {
        if (requiredVertices <= vertexCapacity && vertexBuffer != null) return;
        vertexCapacity = Math.max(requiredVertices, 64);
        ByteBuffer bb = ByteBuffer.allocateDirect(vertexCapacity * FLOATS_PER_VERTEX * BYTES_PER_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
    }

}
