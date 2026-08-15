package com.example.game3d_opengl.game.terrain.presentation;

import android.opengl.GLES20;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainVertexAppearance;
import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Incremental GPU cache derived solely from canonical {@link TerrainSegment} records.
 *
 * <p>Each solid segment owns one fixed six-vertex slot. Terrain streaming normally changes one
 * or two slots, so the next draw uploads only those slots instead of rebuilding and replacing
 * the complete retained terrain VBO.</p>
 */
final class CanonicalTerrainMeshRenderer implements GPUResourceOwner {
    private static final int FLOATS_PER_VERTEX = 8;
    private static final int VERTICES_PER_SEGMENT = 6;
    private static final int FLOATS_PER_SEGMENT =
            FLOATS_PER_VERTEX * VERTICES_PER_SEGMENT;
    private static final int BYTES_PER_FLOAT = 4;
    private static final int INITIAL_SLOT_CAPACITY = 64;
    /**
     * Adjacent track pieces are visually one ribbon, but an actual corner should remain a corner.
     * Forty-five degrees is above the steepest current boost-ramp-to-landing transition while still
     * preserving deliberately sharp terrain folds.
     */
    private static final double MIN_SMOOTH_NORMAL_DOT = Math.cos(Math.toRadians(45.0));
    private static final double NORMAL_EPSILON_SQUARED = 1.0e-24;
    private static final double HORIZONTAL_SHEET_EPSILON = 1.0e-6;
    private static final double CAMERA_SHEET_SIDE_EPSILON = 1.0e-6;
    private static final FColor DEFAULT_LIGHT_COLOR =
            FColor.CLR(1f, 1f, 1f, 1f);

    private final InfillShaderArgs.VS vsArgs = new InfillShaderArgs.VS();
    private final InfillShaderArgs.FS fsArgs = new InfillShaderArgs.FS();
    private final TerrainRibbonShaderPair shader;
    private final TreeMap<Long, Integer> segmentSlots =
            new TreeMap<Long, Integer>();
    /** Includes gaps because immediate canonical adjacency is part of the smoothing decision. */
    private final TreeMap<Long, TerrainSegment> retainedSegments =
            new TreeMap<Long, TerrainSegment>();
    private final BitSet dirtySlots = new BitSet();
    private final BitSet activeSlots = new BitSet();
    private final double[] nearNormalScratch = new double[3];
    private final double[] farNormalScratch = new double[3];
    private final double[] predecessorFarNormalScratch = new double[3];

    private FloatBuffer vertices;
    private int slotCapacity;
    private int nextUnusedSlot;
    private int highestUsedSlotExclusive;
    private int activeSolidSegmentCount;
    private int[] freeSlots = new int[INITIAL_SLOT_CAPACITY];
    private int freeSlotCount;
    private int vboId;
    private int blendedIndexBufferId;
    private boolean gpuStorageDirty = true;
    private boolean blendedIndexGpuStorageDirty = true;

    /*
     * Transparency order is derived without changing the retained vertex VBO. Maximal connected
     * sheets are kept atomic in the final order, then use a fixed canonical order internally.
     * Keeping disconnected stair platforms atomic is important: sorting every triangle globally
     * can alternate the two sheets and create a horizontal dark/light band inside their overlap.
     * All arrays and the direct index buffer are retained and reused by every frame.
     */
    private int[] topologySlots = new int[INITIAL_SLOT_CAPACITY];
    private int[] groupFirstSlot = new int[INITIAL_SLOT_CAPACITY];
    private int[] groupSlotCount = new int[INITIAL_SLOT_CAPACITY];
    private long[] groupFirstSegmentId = new long[INITIAL_SLOT_CAPACITY];
    private boolean[] groupTransparent = new boolean[INITIAL_SLOT_CAPACITY];
    private boolean[] groupHorizontal = new boolean[INITIAL_SLOT_CAPACITY];
    private double[] groupSheetY = new double[INITIAL_SLOT_CAPACITY];
    private int topologySlotCount;
    private int topologyGroupCount;
    private boolean blendTopologyDirty = true;
    private int[] sortedGroupIds = new int[INITIAL_SLOT_CAPACITY];
    private double[] groupVerticalDistance = new double[INITIAL_SLOT_CAPACITY];
    private byte[] groupSortCategory = new byte[INITIAL_SLOT_CAPACITY];
    private int[] blendedTriangleOrder = new int[INITIAL_SLOT_CAPACITY * 2];
    private int[] uploadedTriangleOrder = new int[INITIAL_SLOT_CAPACITY * 2];
    private IntBuffer blendedIndices = ByteBuffer.allocateDirect(
                    INITIAL_SLOT_CAPACITY * VERTICES_PER_SEGMENT * Integer.BYTES)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer();
    private int blendedIndexCapacity = INITIAL_SLOT_CAPACITY * VERTICES_PER_SEGMENT;
    private int blendedTriangleCount;
    private int uploadedTriangleCount = -1;
    /** Coordinate frame baked into retained vertex data for the lifetime of this gameplay run. */
    private Vec3 storageOrigin = Vec3.ZERO;
    private Vec3 renderOrigin = Vec3.ZERO;

    private int fullRebuildCount;
    private int incrementalCommitCount;
    private int incrementallyUpdatedSegmentCount;

    CanonicalTerrainMeshRenderer() {
        this(null);
    }

    CanonicalTerrainMeshRenderer(TerrainRibbonShaderPair shader) {
        this.shader = shader;
        ensureSlotCapacity(INITIAL_SLOT_CAPACITY);
    }

    /** Full rebuild is reserved for initialization or replacement with an unrelated snapshot. */
    void rebuild(Iterable<TerrainSegment> segments, Vec3 nextRenderOrigin) {
        renderOrigin = nextRenderOrigin == null ? Vec3.ZERO : nextRenderOrigin;
        storageOrigin = renderOrigin;
        segmentSlots.clear();
        retainedSegments.clear();
        dirtySlots.clear();
        activeSlots.clear();
        nextUnusedSlot = 0;
        highestUsedSlotExclusive = 0;
        activeSolidSegmentCount = 0;
        freeSlotCount = 0;
        clearAllCpuSlots();

        for (TerrainSegment segment : segments) {
            retainedSegments.put(segment.id, segment);
            if (segment.solid) {
                registerSolidSegment(segment);
            }
        }
        for (TerrainSegment segment : retainedSegments.values()) {
            if (segment.solid) {
                writeDerivedSegment(segment, false);
            }
        }
        gpuStorageDirty = true;
        blendTopologyDirty = true;
        dirtySlots.clear();
        fullRebuildCount++;
    }

    /**
     * Changes the floating-point render frame without touching retained vertex data.
     *
     * <p>The old implementation called {@link #rebuild(Iterable, Vec3)} here. That needlessly
     * cleared and repopulated both maps, recomputed every segment digest and rewrote unused slots
     * whenever the authoritative simulation crossed its 500-unit render-origin threshold. On a
     * 120 Hz device that periodic CPU burst was large enough to miss a vsync.</p>
     *
     * <p>Vertices remain relative to the mesh's stable storage origin. The shader applies the
     * three-float {@code storageOrigin - renderOrigin} translation, so a simulation-origin change
     * is only a uniform update and cannot create a CPU or VBO upload burst.</p>
     */
    void setRenderOrigin(Vec3 nextRenderOrigin) {
        Vec3 safe = nextRenderOrigin == null ? Vec3.ZERO : nextRenderOrigin;
        if (renderOrigin.equals(safe)) {
            return;
        }
        renderOrigin = safe;
    }

    /** Applies a canonical commit without scanning or rebuilding unchanged retained segments. */
    void applyCommit(TerrainCommit commit) {
        if (commit == null) {
            throw new IllegalArgumentException("commit == null");
        }

        boolean retiredAnySegment = false;
        Iterator<Map.Entry<Long, TerrainSegment>> retiring =
                retainedSegments.headMap(
                        commit.retireBeforeSegmentId, false)
                        .entrySet()
                        .iterator();
        while (retiring.hasNext()) {
            Map.Entry<Long, TerrainSegment> entry = retiring.next();
            long segmentId = entry.getKey();
            retiring.remove();
            retiredAnySegment = true;
            if (entry.getValue().solid) {
                Integer slot = segmentSlots.remove(segmentId);
                if (slot != null) {
                    releaseSlot(slot.intValue());
                }
            }
        }

        for (TerrainSegment segment : commit.segmentUpserts) {
            TerrainSegment previousValue = retainedSegments.get(segment.id);
            long digest = segment.deterministicDigest();
            if (previousValue != null
                    && previousValue.deterministicDigest() == digest) {
                retainedSegments.put(segment.id, segment);
                continue;
            }

            retainedSegments.put(segment.id, segment);
            if (!segment.solid) {
                Integer oldSlot = segmentSlots.remove(segment.id);
                if (oldSlot != null) {
                    releaseSlot(oldSlot.intValue());
                }
            } else {
                Integer existingSlot = segmentSlots.get(segment.id);
                if (existingSlot == null) {
                    registerSolidSegment(segment);
                }
                writeDerivedSegment(segment, true);
                incrementallyUpdatedSegmentCount++;
            }

            // A segment's derived near-edge normal depends only on its immediate predecessor.
            // Rewriting this one canonical record therefore invalidates at most one other slot.
            Map.Entry<Long, TerrainSegment> successor =
                    retainedSegments.higherEntry(segment.id);
            if (successor != null
                    && successor.getKey().longValue() == segment.id + 1L
                    && successor.getValue().solid) {
                writeDerivedSegment(successor.getValue(), true);
            }
        }

        if (retiredAnySegment && !retainedSegments.isEmpty()) {
            TerrainSegment firstRetained = retainedSegments.firstEntry().getValue();
            if (firstRetained.solid) {
                writeDerivedSegment(firstRetained, true);
            }
        }
        blendTopologyDirty = true;
        incrementalCommitCount++;
    }

    int vertexCount() {
        return activeSolidSegmentCount * VERTICES_PER_SEGMENT;
    }

    int fullRebuildCount() {
        return fullRebuildCount;
    }

    int incrementalCommitCount() {
        return incrementalCommitCount;
    }

    int incrementallyUpdatedSegmentCount() {
        return incrementallyUpdatedSegmentCount;
    }

    float renderLocalCoordinate(long segmentId, int vertex, int axis) {
        Integer slot = segmentSlots.get(segmentId);
        if (slot == null) {
            throw new IllegalArgumentException("Unknown solid segment " + segmentId);
        }
        if (vertex < 0 || vertex >= VERTICES_PER_SEGMENT
                || axis < 0 || axis >= 3) {
            throw new IllegalArgumentException("Invalid vertex coordinate");
        }
        float stored = vertices.get(
                slot.intValue() * FLOATS_PER_SEGMENT
                        + vertex * FLOATS_PER_VERTEX
                        + axis);
        if (axis == 0) {
            return stored + (float) (storageOrigin.x - renderOrigin.x);
        }
        if (axis == 1) {
            return stored + (float) (storageOrigin.y - renderOrigin.y);
        }
        return stored + (float) (storageOrigin.z - renderOrigin.z);
    }

    void draw(
            FColor color,
            float[] vp,
            LightSource light,
            float cameraEyeY) {
        int drawVertexCount =
                highestUsedSlotExclusive * VERTICES_PER_SEGMENT;
        if (activeSolidSegmentCount == 0 || drawVertexCount == 0 || vp == null) {
            return;
        }
        ensureVbo();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        uploadDirtyVertices();

        vsArgs.mvp = vp;
        vsArgs.positionOffsetX =
                (float) (storageOrigin.x - renderOrigin.x);
        vsArgs.positionOffsetY =
                (float) (storageOrigin.y - renderOrigin.y);
        vsArgs.positionOffsetZ =
                (float) (storageOrigin.z - renderOrigin.z);
        fsArgs.color = color;
        if (light != null && light.position != null && light.color != null) {
            fsArgs.lightX = light.position.x;
            fsArgs.lightY = light.position.y;
            fsArgs.lightZ = light.position.z;
            fsArgs.lightColor = light.color;
        } else {
            fsArgs.lightX = 0f;
            fsArgs.lightY = 0f;
            fsArgs.lightZ = 0f;
            fsArgs.lightColor = DEFAULT_LIGHT_COLOR;
        }

        if (shader == null) {
            throw new IllegalStateException(
                    "Canonical terrain mesh has no GL-context shader");
        }
        shader.setAsCurrentProgram();
        shader.enableAndPointVertexAttribs();

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDepthFunc(GLES20.GL_LESS);
        GLES20.glDepthMask(true);
        GLES20.glColorMask(false, false, false, false);
        fsArgs.isDepthPass = 1;
        shader.setArgs(vsArgs, fsArgs);
        shader.transferUniformArgsToGPU();
        GLES20.glDrawArrays(
                GLES20.GL_TRIANGLES, 0, drawVertexCount);

        int blendedIndexCount = prepareBlendedTriangleOrder(cameraEyeY);
        ensureBlendedIndexBuffer();
        GLES20.glBindBuffer(
                GLES20.GL_ELEMENT_ARRAY_BUFFER, blendedIndexBufferId);
        if (preparedOrderDiffersFromUploaded()) {
            blendedIndices.position(0);
            blendedIndices.limit(blendedIndexCount);
            GLES20.glBufferSubData(
                    GLES20.GL_ELEMENT_ARRAY_BUFFER,
                    0,
                    blendedIndexCount * Integer.BYTES,
                    blendedIndices);
            recordPreparedOrderAsUploaded();
        }

        GLES20.glColorMask(true, true, true, true);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(
                GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        fsArgs.isDepthPass = 0;
        shader.setArgs(vsArgs, fsArgs);
        shader.transferUniformArgsToGPU();
        GLES20.glDrawElements(
                GLES20.GL_TRIANGLES,
                blendedIndexCount,
                GLES20.GL_UNSIGNED_INT,
                0);

        shader.disableVertexAttribs();
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDepthMask(true);
        GLES20.glColorMask(true, true, true, true);
        GLES20.glDepthFunc(GLES20.GL_LESS);
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (vboId != 0) {
            int[] ids = new int[]{vboId};
            GLES20.glDeleteBuffers(1, ids, 0);
            vboId = 0;
        }
        if (blendedIndexBufferId != 0) {
            int[] ids = new int[]{blendedIndexBufferId};
            GLES20.glDeleteBuffers(1, ids, 0);
            blendedIndexBufferId = 0;
        }
        gpuStorageDirty = true;
        blendedIndexGpuStorageDirty = true;
        uploadedTriangleCount = -1;
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        vboId = 0;
        blendedIndexBufferId = 0;
        gpuStorageDirty = true;
        blendedIndexGpuStorageDirty = true;
        uploadedTriangleCount = -1;
    }

    private void registerSolidSegment(TerrainSegment segment) {
        int slot = allocateSlot();
        segmentSlots.put(segment.id, slot);
        activeSlots.set(slot);
        activeSolidSegmentCount++;
    }

    private int allocateSlot() {
        if (freeSlotCount > 0) {
            return freeSlots[--freeSlotCount];
        }
        ensureSlotCapacity(nextUnusedSlot + 1);
        int result = nextUnusedSlot++;
        highestUsedSlotExclusive = Math.max(
                highestUsedSlotExclusive, nextUnusedSlot);
        return result;
    }

    private void releaseSlot(int slot) {
        clearSlot(slot);
        activeSlots.clear(slot);
        dirtySlots.set(slot);
        if (freeSlotCount >= freeSlots.length) {
            freeSlots = Arrays.copyOf(
                    freeSlots, Math.max(1, freeSlots.length * 2));
        }
        freeSlots[freeSlotCount++] = slot;
        activeSolidSegmentCount--;
    }

    private void writeDerivedSegment(TerrainSegment segment, boolean dirty) {
        Integer slotValue = segmentSlots.get(segment.id);
        if (slotValue == null) {
            throw new IllegalStateException(
                    "No render slot for solid segment " + segment.id);
        }
        computeEdgeNormal(segment, false, nearNormalScratch);
        computeEdgeNormal(segment, true, farNormalScratch);

        TerrainSegment predecessor = immediatePredecessor(segment);
        if (canSmoothFrom(predecessor, segment, nearNormalScratch)) {
            nearNormalScratch[0] = predecessorFarNormalScratch[0];
            nearNormalScratch[1] = predecessorFarNormalScratch[1];
            nearNormalScratch[2] = predecessorFarNormalScratch[2];
        }

        int slot = slotValue.intValue();
        int cursor = slot * FLOATS_PER_SEGMENT;
        cursor = putVertex(cursor, segment.nearLeft,
                segment.nearLeftAppearance, nearNormalScratch);
        cursor = putVertex(cursor, segment.nearRight,
                segment.nearRightAppearance, nearNormalScratch);
        cursor = putVertex(cursor, segment.farRight,
                segment.farRightAppearance, farNormalScratch);
        cursor = putVertex(cursor, segment.nearLeft,
                segment.nearLeftAppearance, nearNormalScratch);
        cursor = putVertex(cursor, segment.farRight,
                segment.farRightAppearance, farNormalScratch);
        putVertex(cursor, segment.farLeft,
                segment.farLeftAppearance, farNormalScratch);
        if (dirty) {
            dirtySlots.set(slot);
        }
    }

    private TerrainSegment immediatePredecessor(TerrainSegment segment) {
        Map.Entry<Long, TerrainSegment> entry =
                retainedSegments.lowerEntry(segment.id);
        if (entry == null || entry.getKey().longValue() + 1L != segment.id) {
            return null;
        }
        return entry.getValue();
    }

    private boolean canSmoothFrom(
            TerrainSegment predecessor,
            TerrainSegment segment,
            double[] segmentNearNormal) {
        if (predecessor == null
                || !predecessor.solid
                || !segment.connectedToPrevious
                || !predecessor.farLeft.equals(segment.nearLeft)
                || !predecessor.farRight.equals(segment.nearRight)) {
            return false;
        }
        computeEdgeNormal(predecessor, true, predecessorFarNormalScratch);
        double dot = predecessorFarNormalScratch[0] * segmentNearNormal[0]
                + predecessorFarNormalScratch[1] * segmentNearNormal[1]
                + predecessorFarNormalScratch[2] * segmentNearNormal[2];
        return dot >= MIN_SMOOTH_NORMAL_DOT;
    }

    /**
     * Uses the local ribbon frame instead of either triangle's diagonal: across-edge × centerline.
     * This keeps both triangles of a trapezoidal segment on the same smooth normal field.
     */
    private static void computeEdgeNormal(
            TerrainSegment segment, boolean farEdge, double[] destination) {
        Vec3 left = farEdge ? segment.farLeft : segment.nearLeft;
        Vec3 right = farEdge ? segment.farRight : segment.nearRight;
        double acrossX = right.x - left.x;
        double acrossY = right.y - left.y;
        double acrossZ = right.z - left.z;
        double nearMidX = (segment.nearLeft.x + segment.nearRight.x) * 0.5;
        double nearMidY = (segment.nearLeft.y + segment.nearRight.y) * 0.5;
        double nearMidZ = (segment.nearLeft.z + segment.nearRight.z) * 0.5;
        double farMidX = (segment.farLeft.x + segment.farRight.x) * 0.5;
        double farMidY = (segment.farLeft.y + segment.farRight.y) * 0.5;
        double farMidZ = (segment.farLeft.z + segment.farRight.z) * 0.5;
        double tangentX = farMidX - nearMidX;
        double tangentY = farMidY - nearMidY;
        double tangentZ = farMidZ - nearMidZ;

        double normalX = acrossY * tangentZ - acrossZ * tangentY;
        double normalY = acrossZ * tangentX - acrossX * tangentZ;
        double normalZ = acrossX * tangentY - acrossY * tangentX;
        double lengthSquared =
                normalX * normalX + normalY * normalY + normalZ * normalZ;
        if (!Double.isFinite(lengthSquared)
                || lengthSquared <= NORMAL_EPSILON_SQUARED) {
            destination[0] = 0.0;
            destination[1] = 1.0;
            destination[2] = 0.0;
            return;
        }
        double inverseLength = 1.0 / Math.sqrt(lengthSquared);
        normalX *= inverseLength;
        normalY *= inverseLength;
        normalZ *= inverseLength;
        if (normalY < 0.0) {
            normalX = -normalX;
            normalY = -normalY;
            normalZ = -normalZ;
        }
        destination[0] = normalX;
        destination[1] = normalY;
        destination[2] = normalZ;
    }

    private int putVertex(
            int cursor,
            Vec3 position,
            TerrainVertexAppearance appearance,
            double[] normal) {
        vertices.put(cursor++, (float) (position.x - storageOrigin.x));
        vertices.put(cursor++, (float) (position.y - storageOrigin.y));
        vertices.put(cursor++, (float) (position.z - storageOrigin.z));
        vertices.put(cursor++, (float) normal[0]);
        vertices.put(cursor++, (float) normal[1]);
        vertices.put(cursor++, (float) normal[2]);
        vertices.put(cursor++, appearance.alpha);
        vertices.put(cursor++, appearance.brightness);
        return cursor;
    }

    private void clearSlot(int slot) {
        int start = slot * FLOATS_PER_SEGMENT;
        int end = start + FLOATS_PER_SEGMENT;
        for (int i = start; i < end; i++) {
            vertices.put(i, 0f);
        }
    }

    private void clearAllCpuSlots() {
        for (int i = 0; i < vertices.capacity(); i++) {
            vertices.put(i, 0f);
        }
    }

    private void ensureVbo() {
        if (vboId != 0) {
            return;
        }
        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        vboId = ids[0];
        gpuStorageDirty = true;
    }

    private void ensureBlendedIndexBuffer() {
        if (blendedIndexBufferId == 0) {
            int[] ids = new int[1];
            GLES20.glGenBuffers(1, ids, 0);
            blendedIndexBufferId = ids[0];
            blendedIndexGpuStorageDirty = true;
        }
        GLES20.glBindBuffer(
                GLES20.GL_ELEMENT_ARRAY_BUFFER, blendedIndexBufferId);
        if (blendedIndexGpuStorageDirty) {
            GLES20.glBufferData(
                    GLES20.GL_ELEMENT_ARRAY_BUFFER,
                    blendedIndexCapacity * Integer.BYTES,
                    null,
                    GLES20.GL_DYNAMIC_DRAW);
            blendedIndexGpuStorageDirty = false;
            uploadedTriangleCount = -1;
        }
    }

    private void uploadDirtyVertices() {
        if (gpuStorageDirty) {
            FloatBuffer upload = vertices.duplicate();
            upload.position(0);
            upload.limit(vertices.capacity());
            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER,
                    vertices.capacity() * BYTES_PER_FLOAT,
                    upload,
                    GLES20.GL_DYNAMIC_DRAW);
            gpuStorageDirty = false;
            dirtySlots.clear();
            return;
        }

        int first = dirtySlots.nextSetBit(0);
        while (first >= 0) {
            int endExclusive = dirtySlots.nextClearBit(first);
            int startFloat = first * FLOATS_PER_SEGMENT;
            int endFloat = endExclusive * FLOATS_PER_SEGMENT;
            FloatBuffer upload = vertices.duplicate();
            upload.position(startFloat);
            upload.limit(endFloat);
            GLES20.glBufferSubData(
                    GLES20.GL_ARRAY_BUFFER,
                    startFloat * BYTES_PER_FLOAT,
                    (endFloat - startFloat) * BYTES_PER_FLOAT,
                    upload);
            dirtySlots.clear(first, endExclusive);
            first = dirtySlots.nextSetBit(endExclusive);
        }
    }

    private void ensureSlotCapacity(int requiredSlots) {
        if (vertices != null && requiredSlots <= slotCapacity) {
            return;
        }
        int nextCapacity = Math.max(
                requiredSlots,
                Math.max(INITIAL_SLOT_CAPACITY, slotCapacity * 2));
        FloatBuffer replacement = ByteBuffer.allocateDirect(
                        nextCapacity * FLOATS_PER_SEGMENT * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        if (vertices != null) {
            FloatBuffer old = vertices.duplicate();
            old.position(0);
            old.limit(vertices.capacity());
            replacement.put(old);
        }
        replacement.position(0);
        vertices = replacement;
        slotCapacity = nextCapacity;
        if (freeSlots.length < nextCapacity) {
            freeSlots = Arrays.copyOf(freeSlots, nextCapacity);
        }
        gpuStorageDirty = true;
        dirtySlots.clear();
        ensureBlendOrderCapacity(nextCapacity);
    }

    /**
     * Builds the one-draw index order for the blended color pass.
     *
     * <p>The returned count is an index count, not a triangle count. This method performs no heap
     * allocation once slot capacity is established and deliberately contains no GL calls so its
     * ordering invariants can be covered by local JVM tests.</p>
     */
    int prepareBlendedTriangleOrder(float cameraEyeY) {
        if (!Float.isFinite(cameraEyeY)) {
            throw new IllegalArgumentException("cameraEyeY must be finite");
        }
        rebuildBlendTopologyIfNeeded();
        blendedTriangleCount = 0;
        if (topologyGroupCount == 0) {
            blendedIndices.clear();
            blendedIndices.limit(0);
            return 0;
        }

        for (int group = 0; group < topologyGroupCount; group++) {
            sortedGroupIds[group] = group;
            if (!groupTransparent[group]) {
                // Opaque color must be present before translucent color is composited over it.
                groupSortCategory[group] = 0;
                groupVerticalDistance[group] = 0.0;
            } else if (groupHorizontal[group]) {
                double renderLocalSheetY =
                        groupSheetY[group] - renderOrigin.y;
                double signedDistance = renderLocalSheetY - cameraEyeY;
                groupVerticalDistance[group] = Math.abs(signedDistance);
                if (signedDistance < -CAMERA_SHEET_SIDE_EPSILON) {
                    // All sheets below the eye that a downward ray can hit.
                    groupSortCategory[group] = 1;
                } else if (signedDistance > CAMERA_SHEET_SIDE_EPSILON) {
                    // All sheets above the eye that an upward ray can hit. Keeping this a separate
                    // category gives opposite-side sheets a deterministic order; no forward ray
                    // can intersect one sheet from each category.
                    groupSortCategory[group] = 2;
                } else {
                    groupSortCategory[group] = 3;
                }
            } else {
                // Current gameplay stairs never take this path. A stable canonical fallback is
                // preferable to a projected-centroid mean, which can swap while the camera moves.
                // Future intersecting/non-monotonic transparent sheets need geometry splitting,
                // a painter DAG, or OIT rather than another approximate scalar depth key.
                groupSortCategory[group] = 4;
                groupVerticalDistance[group] = 0.0;
            }
        }
        quickSortGroups(0, topologyGroupCount - 1);

        blendedIndices.clear();
        // The depth prepass has already established opaque visibility, but it did not write color.
        // Emit every fully opaque triangle first so a later alpha=1 fragment can never overwrite a
        // nearer translucent layer. A triangle is transparent if any of its vertices is.
        for (int i = 0; i < topologySlotCount; i++) {
            int slot = topologySlots[i];
            int firstTriangle = slot * 2;
            if (!triangleHasTransparentVertex(firstTriangle)) {
                appendPreparedTriangle(firstTriangle);
            }
            if (!triangleHasTransparentVertex(firstTriangle + 1)) {
                appendPreparedTriangle(firstTriangle + 1);
            }
        }
        for (int sortedGroup = 0;
             sortedGroup < topologyGroupCount;
             sortedGroup++) {
            int group = sortedGroupIds[sortedGroup];
            int first = groupFirstSlot[group];
            int end = first + groupSlotCount[group];
            // Connected sheet triangles share only edges in current terrain. Canonical descending
            // segment order is static across camera motion and physical slot recycling.
            for (int i = end - 1; i >= first; i--) {
                int firstTriangle = topologySlots[i] * 2;
                if (triangleHasTransparentVertex(firstTriangle)) {
                    appendPreparedTriangle(firstTriangle);
                }
                if (triangleHasTransparentVertex(firstTriangle + 1)) {
                    appendPreparedTriangle(firstTriangle + 1);
                }
            }
        }
        int indexCount = blendedTriangleCount * 3;
        blendedIndices.flip();
        return indexCount;
    }

    int blendedTriangleCount() {
        return blendedTriangleCount;
    }

    int blendedTriangleAt(int orderIndex) {
        if (orderIndex < 0 || orderIndex >= blendedTriangleCount) {
            throw new IndexOutOfBoundsException("orderIndex=" + orderIndex);
        }
        return blendedTriangleOrder[orderIndex];
    }

    private void rebuildBlendTopologyIfNeeded() {
        if (!blendTopologyDirty) {
            return;
        }
        topologySlotCount = 0;
        topologyGroupCount = 0;
        TerrainSegment previousSolid = null;
        boolean previousTransparent = false;
        for (TerrainSegment segment : retainedSegments.values()) {
            if (!segment.solid) {
                previousSolid = null;
                continue;
            }
            Integer slotValue = segmentSlots.get(segment.id);
            if (slotValue == null || !activeSlots.get(slotValue.intValue())) {
                throw new IllegalStateException(
                        "Missing active render slot for segment " + segment.id);
            }
            boolean transparent = hasTransparentVertex(segment);
            boolean continuesPreviousSheet = previousSolid != null
                    && segment.id == previousSolid.id + 1L
                    && segment.connectedToPrevious
                    && previousSolid.farLeft.equals(segment.nearLeft)
                    && previousSolid.farRight.equals(segment.nearRight)
                    && transparent == previousTransparent;
            if (!continuesPreviousSheet) {
                groupFirstSlot[topologyGroupCount] = topologySlotCount;
                groupSlotCount[topologyGroupCount] = 0;
                groupFirstSegmentId[topologyGroupCount] = segment.id;
                groupTransparent[topologyGroupCount] = transparent;
                groupHorizontal[topologyGroupCount] = true;
                groupSheetY[topologyGroupCount] = segment.nearLeft.y;
                topologyGroupCount++;
            }
            int group = topologyGroupCount - 1;
            if (groupHorizontal[group]
                    && !segmentLiesOnY(segment, groupSheetY[group])) {
                groupHorizontal[group] = false;
            }
            topologySlots[topologySlotCount++] = slotValue.intValue();
            groupSlotCount[group]++;
            previousSolid = segment;
            previousTransparent = transparent;
        }
        blendTopologyDirty = false;
    }

    private static boolean hasTransparentVertex(TerrainSegment segment) {
        return segment.nearLeftAppearance.alpha < 1f
                || segment.nearRightAppearance.alpha < 1f
                || segment.farLeftAppearance.alpha < 1f
                || segment.farRightAppearance.alpha < 1f;
    }

    private boolean triangleHasTransparentVertex(int triangleId) {
        int firstVertex = triangleId * 3;
        for (int vertex = 0; vertex < 3; vertex++) {
            int alphaOffset = (firstVertex + vertex) * FLOATS_PER_VERTEX + 6;
            if (vertices.get(alphaOffset) < 1f) {
                return true;
            }
        }
        return false;
    }

    private void appendPreparedTriangle(int triangleId) {
        blendedTriangleOrder[blendedTriangleCount++] = triangleId;
        int firstVertex = triangleId * 3;
        blendedIndices.put(firstVertex);
        blendedIndices.put(firstVertex + 1);
        blendedIndices.put(firstVertex + 2);
    }

    boolean preparedOrderDiffersFromUploaded() {
        if (uploadedTriangleCount != blendedTriangleCount) {
            return true;
        }
        for (int i = 0; i < blendedTriangleCount; i++) {
            if (uploadedTriangleOrder[i] != blendedTriangleOrder[i]) {
                return true;
            }
        }
        return false;
    }

    void recordPreparedOrderAsUploaded() {
        System.arraycopy(
                blendedTriangleOrder,
                0,
                uploadedTriangleOrder,
                0,
                blendedTriangleCount);
        uploadedTriangleCount = blendedTriangleCount;
    }

    private static boolean segmentLiesOnY(
            TerrainSegment segment, double expectedY) {
        return Math.abs(segment.nearLeft.y - expectedY)
                        <= HORIZONTAL_SHEET_EPSILON
                && Math.abs(segment.nearRight.y - expectedY)
                        <= HORIZONTAL_SHEET_EPSILON
                && Math.abs(segment.farLeft.y - expectedY)
                        <= HORIZONTAL_SHEET_EPSILON
                && Math.abs(segment.farRight.y - expectedY)
                        <= HORIZONTAL_SHEET_EPSILON;
    }

    private void quickSortGroups(int low, int high) {
        while (low < high) {
            int i = low;
            int j = high;
            int pivot = sortedGroupIds[(low + high) >>> 1];
            while (i <= j) {
                while (groupComesBefore(sortedGroupIds[i], pivot)) i++;
                while (groupComesBefore(pivot, sortedGroupIds[j])) j--;
                if (i <= j) {
                    int swap = sortedGroupIds[i];
                    sortedGroupIds[i] = sortedGroupIds[j];
                    sortedGroupIds[j] = swap;
                    i++;
                    j--;
                }
            }
            if (j - low < high - i) {
                if (low < j) quickSortGroups(low, j);
                low = i;
            } else {
                if (i < high) quickSortGroups(i, high);
                high = j;
            }
        }
    }

    private boolean groupComesBefore(int left, int right) {
        int leftCategory = groupSortCategory[left];
        int rightCategory = groupSortCategory[right];
        if (leftCategory != rightCategory) {
            return leftCategory < rightCategory;
        }
        int comparison;
        if (leftCategory == 1 || leftCategory == 2) {
            // For two horizontal planes on the same side of the camera and any ray intersecting
            // both, t=(sheetY-eyeY)/rayY. Therefore descending vertical distance is the exact
            // painter order everywhere in their overlap, not a centroid approximation.
            comparison = Double.compare(
                    groupVerticalDistance[right],
                    groupVerticalDistance[left]);
        } else {
            comparison = 0;
        }
        if (comparison != 0) {
            return comparison < 0;
        }
        // Higher canonical ids are farther ahead in the current streamed track. The tie-break is
        // stable across slot recycling and also handles sheets on the eye plane deterministically.
        return groupFirstSegmentId[left] > groupFirstSegmentId[right];
    }

    private void ensureBlendOrderCapacity(int requiredSlots) {
        if (topologySlots.length < requiredSlots) {
            topologySlots = Arrays.copyOf(topologySlots, requiredSlots);
            groupFirstSlot = Arrays.copyOf(groupFirstSlot, requiredSlots);
            groupSlotCount = Arrays.copyOf(groupSlotCount, requiredSlots);
            groupFirstSegmentId = Arrays.copyOf(
                    groupFirstSegmentId, requiredSlots);
            groupTransparent = Arrays.copyOf(
                    groupTransparent, requiredSlots);
            groupHorizontal = Arrays.copyOf(
                    groupHorizontal, requiredSlots);
            groupSheetY = Arrays.copyOf(groupSheetY, requiredSlots);
            sortedGroupIds = Arrays.copyOf(sortedGroupIds, requiredSlots);
            groupVerticalDistance = Arrays.copyOf(
                    groupVerticalDistance, requiredSlots);
            groupSortCategory = Arrays.copyOf(
                    groupSortCategory, requiredSlots);
        }
        int requiredTriangles = requiredSlots * 2;
        if (blendedTriangleOrder.length < requiredTriangles) {
            blendedTriangleOrder = Arrays.copyOf(
                    blendedTriangleOrder, requiredTriangles);
            uploadedTriangleOrder = Arrays.copyOf(
                    uploadedTriangleOrder, requiredTriangles);
        }
        int requiredIndices = requiredSlots * VERTICES_PER_SEGMENT;
        if (blendedIndexCapacity < requiredIndices) {
            blendedIndices = ByteBuffer.allocateDirect(
                            requiredIndices * Integer.BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asIntBuffer();
            blendedIndexCapacity = requiredIndices;
            blendedIndexGpuStorageDirty = true;
        }
        blendTopologyDirty = true;
    }
}
