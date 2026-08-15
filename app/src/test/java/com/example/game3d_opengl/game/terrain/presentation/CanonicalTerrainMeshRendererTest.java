package com.example.game3d_opengl.game.terrain.presentation;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.SurfaceProperties;
import com.example.game3d.core.terrain.StreamingTerrainGenerator;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.TerrainState;
import com.example.game3d.core.terrain.TerrainVertexAppearance;
import com.example.game3d.core.terrain.TrackBuilder;

import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CanonicalTerrainMeshRendererTest {
    private static final int FLOATS_PER_VERTEX = 8;
    private static final int FLOATS_PER_SEGMENT = 6 * 8;
    private static final int NORMAL_OFFSET = 3;

    @Test
    public void streamingCommitsUpdateOnlyChangedSlots() {
        TerrainSnapshot complete = new TrackBuilder(6.0)
                .straight(4.0)
                .straight(4.0)
                .straight(4.0)
                .buildSnapshot();
        TerrainSegment first = complete.segments.get(0);
        TerrainSegment second = complete.segments.get(1);
        TerrainSegment third = complete.segments.get(2);
        CanonicalTerrainMeshRenderer mesh =
                new CanonicalTerrainMeshRenderer();

        mesh.rebuild(
                new TerrainSnapshot(
                        0L, 1L, 0L,
                        java.util.Arrays.asList(first, second)).segments,
                Vec3.ZERO);

        assertEquals(12, mesh.vertexCount());
        assertEquals(1, mesh.fullRebuildCount());
        assertEquals(0, mesh.incrementalCommitCount());

        mesh.applyCommit(new TerrainCommit(
                0L, 1L, 2L, 0L,
                Collections.singletonList(third)));

        assertEquals(18, mesh.vertexCount());
        assertEquals(1, mesh.fullRebuildCount());
        assertEquals(1, mesh.incrementalCommitCount());
        assertEquals(1, mesh.incrementallyUpdatedSegmentCount());

        mesh.applyCommit(new TerrainCommit(
                1L, 2L, 2L, 1L,
                Collections.<TerrainSegment>emptyList()));

        assertEquals(12, mesh.vertexCount());
        assertEquals(1, mesh.fullRebuildCount());
        assertEquals(2, mesh.incrementalCommitCount());

        mesh.applyCommit(new TerrainCommit(
                2L, 3L, 2L, 1L,
                Collections.singletonList(second)));

        assertEquals(12, mesh.vertexCount());
        assertEquals(
                "an identical upsert must not dirty or rewrite its slot",
                1,
                mesh.incrementallyUpdatedSegmentCount());
    }

    @Test
    public void renderOriginChangeShiftsExistingSlotsWithoutFullRebuild() {
        TerrainSnapshot terrain = new TrackBuilder(6.0)
                .straight(4.0)
                .straight(4.0)
                .buildSnapshot();
        CanonicalTerrainMeshRenderer mesh =
                new CanonicalTerrainMeshRenderer();

        mesh.rebuild(terrain.segments, Vec3.ZERO);
        TerrainSegment first = terrain.segments.get(0);
        float oldX = mesh.renderLocalCoordinate(first.id, 0, 0);
        float oldY = mesh.renderLocalCoordinate(first.id, 0, 1);
        float oldZ = mesh.renderLocalCoordinate(first.id, 0, 2);

        mesh.setRenderOrigin(new Vec3(20.0, -30.0, -500.0));

        assertEquals(12, mesh.vertexCount());
        assertEquals(1, mesh.fullRebuildCount());
        assertEquals(0, mesh.incrementalCommitCount());
        assertEquals(oldX - 20.0f,
                mesh.renderLocalCoordinate(first.id, 0, 0), 1.0e-5f);
        assertEquals(oldY + 30.0f,
                mesh.renderLocalCoordinate(first.id, 0, 1), 1.0e-5f);
        assertEquals(oldZ + 500.0f,
                mesh.renderLocalCoordinate(first.id, 0, 2), 1.0e-5f);
    }

    @Test
    public void incrementalCommitsMatchAFullRebuildOfTheFinalCanonicalState()
            throws Exception {
        TerrainSnapshot authored = new TrackBuilder(6.0)
                .straight(4.0)
                .straight(4.0)
                .straight(4.0)
                .straight(4.0)
                .buildSnapshot();
        TerrainSegment first = authored.segments.get(0);
        TerrainSegment second = authored.segments.get(1);
        TerrainSegment third = authored.segments.get(2);
        TerrainSegment fourth = authored.segments.get(3);
        TerrainSegment changedSecond = liftedWithNewAppearance(second);
        TerrainSegment thirdAsGap = withSolidity(third, false);
        TerrainSnapshot initial = new TerrainSnapshot(
                0L, 2L, 0L, Arrays.asList(first, second, third));
        TerrainState finalState = new TerrainState(initial);
        CanonicalTerrainMeshRenderer incremental =
                new CanonicalTerrainMeshRenderer();
        incremental.rebuild(initial.segments, Vec3.ZERO);

        int retiredFirstSlot = slotFor(incremental, first.id);
        int removedThirdSlot = slotFor(incremental, third.id);
        TerrainCommit removeAndChange = new TerrainCommit(
                0L, 1L, 2L, 1L,
                Arrays.asList(changedSecond, thirdAsGap));
        incremental.applyCommit(removeAndChange);
        finalState.apply(removeAndChange);

        assertEquals(6, incremental.vertexCount());
        assertSlotContainsOnlyZeros(incremental, retiredFirstSlot);
        assertSlotContainsOnlyZeros(incremental, removedThirdSlot);

        TerrainCommit append = new TerrainCommit(
                1L, 2L, 3L, 1L,
                Collections.singletonList(fourth));
        incremental.applyCommit(append);
        finalState.apply(append);

        CanonicalTerrainMeshRenderer rebuilt =
                new CanonicalTerrainMeshRenderer();
        rebuilt.rebuild(finalState.segments(), Vec3.ZERO);

        assertEquals(12, incremental.vertexCount());
        assertEquals(segmentSlots(rebuilt).keySet(),
                segmentSlots(incremental).keySet());
        for (Long segmentId : segmentSlots(rebuilt).keySet()) {
            assertSegmentDataEquals(rebuilt, incremental, segmentId);
        }
        assertEquals(1, incremental.fullRebuildCount());
        assertEquals(2, incremental.incrementalCommitCount());
        assertEquals(
                "one changed segment plus one appended segment should be rewritten",
                2,
                incremental.incrementallyUpdatedSegmentCount());
    }

    @Test
    public void connectedPitchChangeUsesOneContinuousUnitNormalAtTheSeam()
            throws Exception {
        TerrainSegment flat = stripSegment(0L, 0.0, 0.0, false);
        TerrainSegment ramp = stripSegment(1L, 0.0, 0.5, true);
        CanonicalTerrainMeshRenderer mesh =
                meshWith(flat, ramp);

        float[] flatFarLeft = normal(mesh, flat.id, 5);
        float[] flatFarRight = normal(mesh, flat.id, 2);
        float[] rampNearLeft = normal(mesh, ramp.id, 0);
        float[] rampNearRight = normal(mesh, ramp.id, 1);
        float[] rampFar = normal(mesh, ramp.id, 5);

        assertNormalEquals(flatFarLeft, rampNearLeft);
        assertNormalEquals(flatFarRight, rampNearRight);
        assertNormalEquals(rampNearLeft, normal(mesh, ramp.id, 3));
        assertNormalEquals(rampFar, normal(mesh, ramp.id, 2));
        assertNormalEquals(rampFar, normal(mesh, ramp.id, 4));
        assertUnitFinite(flatFarLeft);
        assertUnitFinite(rampNearLeft);
        assertUnitFinite(rampFar);
        assertTrue("the ramp must still transition away from the flat seam",
                vectorDistance(rampNearLeft, rampFar) > 0.1);
    }

    @Test
    public void disconnectedOrGeometricallySplitSegmentsKeepTheirOwnNearNormal()
            throws Exception {
        TerrainSegment flat = stripSegment(0L, 0.0, 0.0, false);
        TerrainSegment disconnected =
                stripSegment(1L, 0.0, 0.5, false);
        CanonicalTerrainMeshRenderer disconnectedMesh =
                meshWith(flat, disconnected);

        assertNormalEquals(
                normal(disconnectedMesh, disconnected.id, 0),
                normal(disconnectedMesh, disconnected.id, 5));
        assertTrue(vectorDistance(
                normal(disconnectedMesh, flat.id, 5),
                normal(disconnectedMesh, disconnected.id, 0)) > 0.1);

        TerrainSegment split = segment(
                1L,
                new Vec3(-1.0, 0.0, -0.001),
                new Vec3(1.0, 0.0, -0.001),
                new Vec3(-1.0, 0.5, -1.0),
                new Vec3(1.0, 0.5, -1.0),
                true);
        CanonicalTerrainMeshRenderer splitMesh =
                meshWith(flat, split);

        assertNormalEquals(
                normal(splitMesh, split.id, 0),
                normal(splitMesh, split.id, 5));
        assertTrue("connected metadata must not bridge a geometric crack",
                vectorDistance(
                        normal(splitMesh, flat.id, 5),
                        normal(splitMesh, split.id, 0)) > 0.1);
    }

    @Test
    public void sharpConnectedFoldRemainsHardButRampLandingAngleIsSmoothed()
            throws Exception {
        TerrainSegment flat = stripSegment(0L, 0.0, 0.0, false);
        TerrainSegment sharp = stripSegment(1L, 0.0, 2.0, true);
        CanonicalTerrainMeshRenderer sharpMesh =
                meshWith(flat, sharp);

        assertNormalEquals(
                normal(sharpMesh, sharp.id, 0),
                normal(sharpMesh, sharp.id, 5));
        assertTrue(vectorDistance(
                normal(sharpMesh, flat.id, 5),
                normal(sharpMesh, sharp.id, 0)) > 0.5);

        // 0.48/1.0 is about 25.6 degrees: the steepest current boost ramp is pi/7.
        TerrainSegment launch = stripSegment(0L, -0.48, 0.0, false);
        TerrainSegment landing = stripSegment(1L, 0.0, 0.0, true);
        CanonicalTerrainMeshRenderer landingMesh =
                meshWith(launch, landing);

        assertNormalEquals(
                normal(landingMesh, launch.id, 5),
                normal(landingMesh, landing.id, 0));
        assertTrue("the landing must fade back to its own flat normal",
                vectorDistance(
                        normal(landingMesh, landing.id, 0),
                        normal(landingMesh, landing.id, 5)) > 0.1);
    }

    @Test
    public void predecessorChangeAndRetirementMatchFreshRebuilds()
            throws Exception {
        TerrainSegment first = stripSegment(0L, 0.0, 0.0, false);
        TerrainSegment second = stripSegment(1L, 0.0, 0.5, true);
        TerrainSegment third = stripSegment(2L, 0.5, 0.8, true);
        TerrainSnapshot initial = new TerrainSnapshot(
                0L, 2L, 0L, Arrays.asList(first, second, third));
        TerrainState state = new TerrainState(initial);
        CanonicalTerrainMeshRenderer incremental =
                new CanonicalTerrainMeshRenderer();
        incremental.rebuild(initial.segments, Vec3.ZERO);
        float[] oldSecondNear = normal(incremental, second.id, 0);

        TerrainSegment changedFirst =
                stripSegment(0L, -0.4, 0.0, false);
        TerrainCommit change = new TerrainCommit(
                0L, 1L, 2L, 0L,
                Collections.singletonList(changedFirst));
        incremental.applyCommit(change);
        state.apply(change);

        assertTrue("changing a predecessor must rewrite its successor's near normal",
                vectorDistance(
                        oldSecondNear,
                        normal(incremental, second.id, 0)) > 0.1);
        assertMatchesFreshRebuild(incremental, state);

        TerrainCommit retire = new TerrainCommit(
                1L, 2L, 2L, 1L,
                Collections.<TerrainSegment>emptyList());
        incremental.applyCommit(retire);
        state.apply(retire);

        assertNormalEquals(
                normal(incremental, second.id, 0),
                normal(incremental, second.id, 5));
        assertMatchesFreshRebuild(incremental, state);
    }

    @Test
    public void adjacentNonCoplanarUpsertsMatchFreshRebuild()
            throws Exception {
        TerrainSegment first = stripSegment(0L, 0.0, 0.0, false);
        TerrainSegment second = stripSegment(1L, 0.0, 0.2, true);
        TerrainSegment third = stripSegment(2L, 0.2, 0.4, true);
        TerrainSnapshot initial = new TerrainSnapshot(
                0L, 2L, 0L, Arrays.asList(first, second, third));
        TerrainState state = new TerrainState(initial);
        CanonicalTerrainMeshRenderer incremental =
                new CanonicalTerrainMeshRenderer();
        incremental.rebuild(initial.segments, Vec3.ZERO);

        TerrainSegment changedFirst =
                stripSegment(0L, -0.3, 0.1, false);
        TerrainSegment changedSecond =
                stripSegment(1L, 0.1, 0.65, true);
        TerrainSegment changedThird =
                stripSegment(2L, 0.65, 0.9, true);
        TerrainCommit change = new TerrainCommit(
                0L, 1L, 2L, 0L,
                Arrays.asList(
                        changedFirst, changedSecond, changedThird));

        incremental.applyCommit(change);
        state.apply(change);

        assertMatchesFreshRebuild(incremental, state);
        assertNormalEquals(
                normal(incremental, changedFirst.id, 5),
                normal(incremental, changedSecond.id, 0));
        assertNormalEquals(
                normal(incremental, changedSecond.id, 5),
                normal(incremental, changedThird.id, 0));
    }

    @Test
    public void mixedYawAndPitchTrapezoidsKeepContinuousFiniteNormals()
            throws Exception {
        TerrainSegment first = segment(
                0L,
                new Vec3(-1.0, 0.0, 1.0),
                new Vec3(1.0, 0.0, 1.0),
                new Vec3(-0.8, 0.25, -0.3),
                new Vec3(1.0, 0.25, 0.3),
                false);
        TerrainSegment second = segment(
                1L,
                first.farLeft,
                first.farRight,
                new Vec3(-0.3, 0.65, -1.5),
                new Vec3(1.1, 0.65, -0.1),
                true);
        CanonicalTerrainMeshRenderer mesh = meshWith(first, second);

        float[] firstFar = normal(mesh, first.id, 5);
        float[] secondNear = normal(mesh, second.id, 0);
        float[] secondFar = normal(mesh, second.id, 5);

        assertNormalEquals(firstFar, secondNear);
        assertUnitFinite(firstFar);
        assertUnitFinite(secondNear);
        assertUnitFinite(secondFar);
    }

    @Test
    public void freedSlotReuseDoesNotChangeBackToFrontBlendedOrder()
            throws Exception {
        TerrainSegment near = translucentFlatSegment(
                0L, 0.0, 0.0, -1.0, false);
        TerrainSegment middle = translucentFlatSegment(
                1L, 0.0, -10.0, -11.0, false);
        TerrainSegment far = translucentFlatSegment(
                2L, 0.0, -20.0, -21.0, false);
        CanonicalTerrainMeshRenderer incremental =
                meshWith(near, middle);
        int retiredNearSlot = slotFor(incremental, near.id);

        incremental.applyCommit(new TerrainCommit(
                0L, 1L, 2L, 1L, Collections.singletonList(far)));
        CanonicalTerrainMeshRenderer rebuilt = meshWith(middle, far);

        assertEquals(
                "the append must really exercise a recycled physical slot",
                retiredNearSlot,
                slotFor(incremental, far.id));
        assertTrue(
                "a fresh rebuild must use a different physical layout",
                slotFor(incremental, far.id) != slotFor(rebuilt, far.id));

        incremental.prepareBlendedTriangleOrder(5.0f);
        rebuilt.prepareBlendedTriangleOrder(5.0f);
        long[] incrementalOrder = orderedSegmentIds(incremental);
        long[] rebuiltOrder = orderedSegmentIds(rebuilt);

        assertLongArrayEquals(rebuiltOrder, incrementalOrder);
        assertLongArrayEquals(new long[]{2L, 2L, 1L, 1L}, incrementalOrder);
    }

    @Test
    public void disconnectedTransparentSheetsNeverInterleaveTheirTriangles()
            throws Exception {
        TerrainSegment lowerNear = translucentFlatSegment(
                0L, 0.0, -100.0, -90.0, false);
        TerrainSegment lowerFar = translucentFlatSegment(
                1L, 0.0, -90.0, -10.0, true);
        TerrainSegment raisedNear = translucentFlatSegment(
                2L, 2.0, -70.0, -60.0, false);
        TerrainSegment raisedFar = translucentFlatSegment(
                3L, 2.0, -60.0, -50.0, true);
        CanonicalTerrainMeshRenderer mesh = meshWith(
                lowerNear, lowerFar, raisedNear, raisedFar);

        mesh.prepareBlendedTriangleOrder(4.0f);
        long[] order = orderedSegmentIds(mesh);

        // A global triangle-centroid sort would alternate these depth ranges. The renderer instead
        // keeps each maximal connected stair sheet atomic, so their overlap cannot acquire a dark
        // middle band from an order flip.
        boolean firstSheetRaised = order[0] >= 2L;
        int sheetTransitions = 0;
        boolean previousRaised = firstSheetRaised;
        for (long segmentId : order) {
            boolean raised = segmentId >= 2L;
            if (raised != previousRaised) {
                sheetTransitions++;
                previousRaised = raised;
            }
        }
        assertEquals(1, sheetTransitions);
        assertEquals(8, order.length);
    }

    @Test
    public void releasedUnreusedSlotsAreExcludedFromBlendedIndexOrder()
            throws Exception {
        TerrainSegment first = translucentFlatSegment(
                0L, 0.0, 0.0, -1.0, false);
        TerrainSegment second = translucentFlatSegment(
                1L, 0.0, -2.0, -3.0, false);
        CanonicalTerrainMeshRenderer mesh = meshWith(first, second);

        mesh.applyCommit(new TerrainCommit(
                0L, 1L, 1L, 1L,
                Collections.<TerrainSegment>emptyList()));
        int indexCount = mesh.prepareBlendedTriangleOrder(5.0f);

        assertEquals(6, indexCount);
        assertEquals(2, mesh.blendedTriangleCount());
        assertLongArrayEquals(new long[]{1L, 1L}, orderedSegmentIds(mesh));
    }

    @Test
    public void horizontalSheetOrderStaysExactWhileCameraMovesAboveOrBelowBoth()
            throws Exception {
        TerrainSegment lower = translucentFlatSegment(
                0L, -2.0, -4.0, -5.0, false);
        TerrainSegment upper = translucentFlatSegment(
                1L, 0.0, -20.0, -21.0, false);
        CanonicalTerrainMeshRenderer mesh = meshWith(lower, upper);
        // Projected centroids claim that the upper sheet is farther, but every camera position
        // above both planes sees the lower plane farther along any ray intersecting both.
        float[] eyePositionsAbove = new float[]{0.0001f, 0.01f, 1f, 100f};
        for (float eyeY : eyePositionsAbove) {
            mesh.prepareBlendedTriangleOrder(eyeY);
            assertLongArrayEquals(
                    new long[]{0L, 0L, 1L, 1L},
                    orderedSegmentIds(mesh));
        }

        // Once the camera is below both, the physically farther plane is the upper one. The order
        // reverses exactly once the two sheets are on the other common side of the eye.
        float[] eyePositionsBelow = new float[]{-2.0001f, -2.01f, -3f, -100f};
        for (float eyeY : eyePositionsBelow) {
            mesh.prepareBlendedTriangleOrder(eyeY);
            assertLongArrayEquals(
                    new long[]{1L, 1L, 0L, 0L},
                    orderedSegmentIds(mesh));
        }
    }

    @Test
    public void horizontalSheetOrderIgnoresLongitudinalCentroidRank()
            throws Exception {
        TerrainSegment lower = translucentFlatSegment(
                0L, -1.0, -3.0, -4.0, false);
        TerrainSegment upper = translucentFlatSegment(
                1L, 1.0, -30.0, -31.0, false);
        CanonicalTerrainMeshRenderer mesh = meshWith(lower, upper);
        mesh.prepareBlendedTriangleOrder(3.0f);
        long[] firstOrder = orderedSegmentIds(mesh);
        mesh.prepareBlendedTriangleOrder(3.1f);
        long[] movedCameraOrder = orderedSegmentIds(mesh);

        assertLongArrayEquals(firstOrder, movedCameraOrder);
        assertLongArrayEquals(
                new long[]{0L, 0L, 1L, 1L},
                movedCameraOrder);
    }

    @Test
    public void horizontalSheetDistanceUsesTheSameRenderLocalFrameAsCameraEye()
            throws Exception {
        TerrainSegment lower = translucentFlatSegment(
                0L, 998.0, -3.0, -4.0, false);
        TerrainSegment upper = translucentFlatSegment(
                1L, 1000.0, -8.0, -9.0, false);
        CanonicalTerrainMeshRenderer mesh = meshWith(lower, upper);
        mesh.setRenderOrigin(new Vec3(0.0, 1000.0, 0.0));
        mesh.prepareBlendedTriangleOrder(3.0f);
        long[] firstRenderFrame = orderedSegmentIds(mesh);

        // Same absolute camera Y (1003), represented after a different floating-origin rebase.
        mesh.setRenderOrigin(new Vec3(0.0, 900.0, 0.0));
        mesh.prepareBlendedTriangleOrder(103.0f);
        long[] rebasedRenderFrame = orderedSegmentIds(mesh);

        assertLongArrayEquals(firstRenderFrame, rebasedRenderFrame);
        assertLongArrayEquals(
                new long[]{0L, 0L, 1L, 1L},
                rebasedRenderFrame);
    }

    @Test
    public void fullyOpaqueTrianglePrecedesTransparentTriangleInMixedSegment()
            throws Exception {
        TerrainVertexAppearance opaque = TerrainVertexAppearance.DEFAULT;
        TerrainVertexAppearance translucent =
                new TerrainVertexAppearance(0.5f, 1.0f);
        TerrainSegment mixed = new TerrainSegment(
                0L,
                new Vec3(-1.0, 0.0, 0.0),
                new Vec3(1.0, 0.0, 0.0),
                new Vec3(-1.0, 0.0, -1.0),
                new Vec3(1.0, 0.0, -1.0),
                true,
                false,
                SurfaceProperties.NORMAL,
                opaque,
                opaque,
                translucent,
                opaque,
                Collections.emptyList());
        CanonicalTerrainMeshRenderer mesh = meshWith(mixed);

        // Triangle 1 has the farther centroid, so a pure centroid sort would put it first. It has
        // one translucent vertex, however, while triangle 0 is fully opaque and must establish
        // scene color before any transparent compositing.
        mesh.prepareBlendedTriangleOrder(2.0f);

        assertEquals(2, mesh.blendedTriangleCount());
        assertEquals(0, mesh.blendedTriangleAt(0));
        assertEquals(1, mesh.blendedTriangleAt(1));
    }

    @Test
    public void unchangedPreparedOrderDoesNotRequestAnotherGpuUpload()
            throws Exception {
        TerrainSegment lower = translucentFlatSegment(
                0L, -2.0, -3.0, -4.0, false);
        TerrainSegment upper = translucentFlatSegment(
                1L, 0.0, -8.0, -9.0, false);
        CanonicalTerrainMeshRenderer mesh = meshWith(lower, upper);
        mesh.prepareBlendedTriangleOrder(2.0f);
        assertTrue(mesh.preparedOrderDiffersFromUploaded());
        mesh.recordPreparedOrderAsUploaded();

        mesh.prepareBlendedTriangleOrder(2.1f);
        assertTrue(
                "moving the eye without changing painter order must reuse the uploaded EBO",
                !mesh.preparedOrderDiffersFromUploaded());

        mesh.prepareBlendedTriangleOrder(-3.0f);
        assertTrue(
                "an actual painter-order reversal must upload the new EBO",
                mesh.preparedOrderDiffersFromUploaded());
    }

    @Test
    public void nonHorizontalTransparentFallbackUsesStableCanonicalOrder()
            throws Exception {
        TerrainVertexAppearance translucent =
                new TerrainVertexAppearance(0.5f, 1.0f);
        TerrainSegment earlier = new TerrainSegment(
                0L,
                new Vec3(-1.0, 0.0, -2.0),
                new Vec3(1.0, 0.0, -2.0),
                new Vec3(-1.0, 1.0, -3.0),
                new Vec3(1.0, 1.0, -3.0),
                true, false, SurfaceProperties.NORMAL,
                translucent, translucent, translucent, translucent,
                Collections.emptyList());
        TerrainSegment later = new TerrainSegment(
                1L,
                new Vec3(-1.0, 4.0, -20.0),
                new Vec3(1.0, 4.0, -20.0),
                new Vec3(-1.0, 5.0, -21.0),
                new Vec3(1.0, 5.0, -21.0),
                true, false, SurfaceProperties.NORMAL,
                translucent, translucent, translucent, translucent,
                Collections.emptyList());
        CanonicalTerrainMeshRenderer mesh = meshWith(earlier, later);

        mesh.prepareBlendedTriangleOrder(100.0f);
        long[] aboveOrder = orderedSegmentIds(mesh);
        mesh.prepareBlendedTriangleOrder(-100.0f);
        long[] belowOrder = orderedSegmentIds(mesh);

        assertLongArrayEquals(aboveOrder, belowOrder);
        assertLongArrayEquals(new long[]{1L, 1L, 0L, 0L}, belowOrder);
    }

    @Test
    public void authoredStairPlatformsAreSeparateConnectedTransparentSheets() {
        StreamingTerrainGenerator generator = new StreamingTerrainGenerator(
                6.0, 1.0, new Vec3(0.0, 0.0, 2.0));
        // Level one deterministically selects the seven-platform stair recipe.
        generator.enqueueGameplayLevel(1);
        generator.generateChunks(-1);
        List<TerrainSegment> segments = generator.snapshot().segments;

        int sheetCount = 0;
        int currentSheetLength = 0;
        double currentSheetY = Double.NaN;
        for (TerrainSegment segment : segments) {
            boolean translucent = segment.solid
                    && segment.nearLeftAppearance.alpha < 1f;
            if (!translucent) {
                if (currentSheetLength > 0) {
                    assertEquals(42, currentSheetLength);
                    currentSheetLength = 0;
                    currentSheetY = Double.NaN;
                }
                continue;
            }
            if (currentSheetLength == 0) {
                sheetCount++;
                assertTrue(
                        "a lifted stair platform must start a new sheet",
                        !segment.connectedToPrevious);
                currentSheetY = segment.nearLeft.y;
            } else {
                assertTrue(
                        "tiles within one stair platform must stay connected",
                        segment.connectedToPrevious);
            }
            assertEquals(currentSheetY, segment.nearLeft.y, 0.0);
            assertEquals(currentSheetY, segment.nearRight.y, 0.0);
            assertEquals(currentSheetY, segment.farLeft.y, 0.0);
            assertEquals(currentSheetY, segment.farRight.y, 0.0);
            currentSheetLength++;
        }
        if (currentSheetLength > 0) {
            assertEquals(42, currentSheetLength);
        }
        assertEquals(7, sheetCount);
    }

    private static TerrainSegment liftedWithNewAppearance(
            TerrainSegment source) {
        Vec3 lift = new Vec3(0.0, 1.25, 0.0);
        TerrainVertexAppearance changedAppearance =
                new TerrainVertexAppearance(0.65f, 0.4f);
        return new TerrainSegment(
                source.id,
                source.nearLeft.add(lift),
                source.nearRight.add(lift),
                source.farLeft.add(lift),
                source.farRight.add(lift),
                true,
                source.connectedToPrevious,
                source.surface,
                changedAppearance,
                source.nearRightAppearance,
                source.farLeftAppearance,
                source.farRightAppearance,
                source.addons);
    }

    private static TerrainSegment withSolidity(
            TerrainSegment source, boolean solid) {
        return new TerrainSegment(
                source.id,
                source.nearLeft,
                source.nearRight,
                source.farLeft,
                source.farRight,
                solid,
                source.connectedToPrevious,
                source.surface,
                source.nearLeftAppearance,
                source.nearRightAppearance,
                source.farLeftAppearance,
                source.farRightAppearance,
                source.addons);
    }

    private static CanonicalTerrainMeshRenderer meshWith(
            TerrainSegment... segments) {
        CanonicalTerrainMeshRenderer mesh =
                new CanonicalTerrainMeshRenderer();
        mesh.rebuild(Arrays.asList(segments), Vec3.ZERO);
        return mesh;
    }

    private static TerrainSegment stripSegment(
            long id, double nearY, double farY, boolean connected) {
        double nearZ = 1.0 - id;
        double farZ = -id;
        return segment(
                id,
                new Vec3(-1.0, nearY, nearZ),
                new Vec3(1.0, nearY, nearZ),
                new Vec3(-1.0, farY, farZ),
                new Vec3(1.0, farY, farZ),
                connected);
    }

    private static TerrainSegment translucentFlatSegment(
            long id,
            double y,
            double nearZ,
            double farZ,
            boolean connected) {
        TerrainVertexAppearance translucent =
                new TerrainVertexAppearance(0.5f, 1.0f);
        return new TerrainSegment(
                id,
                new Vec3(-1.0, y, nearZ),
                new Vec3(1.0, y, nearZ),
                new Vec3(-1.0, y, farZ),
                new Vec3(1.0, y, farZ),
                true,
                connected,
                SurfaceProperties.NORMAL,
                translucent,
                translucent,
                translucent,
                translucent,
                Collections.emptyList());
    }

    private static long[] orderedSegmentIds(
            CanonicalTerrainMeshRenderer mesh) throws Exception {
        Map<Long, Integer> slots = segmentSlots(mesh);
        long[] segmentBySlot = new long[64];
        Arrays.fill(segmentBySlot, -1L);
        for (Map.Entry<Long, Integer> entry : slots.entrySet()) {
            int slot = entry.getValue().intValue();
            if (slot >= segmentBySlot.length) {
                int oldLength = segmentBySlot.length;
                segmentBySlot = Arrays.copyOf(segmentBySlot, slot + 1);
                Arrays.fill(segmentBySlot, oldLength, segmentBySlot.length, -1L);
            }
            segmentBySlot[slot] = entry.getKey().longValue();
        }
        long[] result = new long[mesh.blendedTriangleCount()];
        for (int i = 0; i < result.length; i++) {
            int triangleId = mesh.blendedTriangleAt(i);
            result[i] = segmentBySlot[triangleId / 2];
        }
        return result;
    }

    private static void assertLongArrayEquals(long[] expected, long[] actual) {
        assertEquals("array length", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("array element " + i, expected[i], actual[i]);
        }
    }

    private static TerrainSegment segment(
            long id,
            Vec3 nearLeft,
            Vec3 nearRight,
            Vec3 farLeft,
            Vec3 farRight,
            boolean connected) {
        return new TerrainSegment(
                id,
                nearLeft,
                nearRight,
                farLeft,
                farRight,
                true,
                connected,
                SurfaceProperties.NORMAL,
                TerrainVertexAppearance.DEFAULT,
                TerrainVertexAppearance.DEFAULT,
                TerrainVertexAppearance.DEFAULT,
                TerrainVertexAppearance.DEFAULT,
                Collections.emptyList());
    }

    private static float[] normal(
            CanonicalTerrainMeshRenderer mesh, long segmentId, int vertex)
            throws Exception {
        int start = slotFor(mesh, segmentId) * FLOATS_PER_SEGMENT
                + vertex * FLOATS_PER_VERTEX + NORMAL_OFFSET;
        FloatBuffer data = vertices(mesh);
        return new float[]{
                data.get(start),
                data.get(start + 1),
                data.get(start + 2)
        };
    }

    private static void assertNormalEquals(float[] expected, float[] actual) {
        assertEquals(expected[0], actual[0], 0.0f);
        assertEquals(expected[1], actual[1], 0.0f);
        assertEquals(expected[2], actual[2], 0.0f);
    }

    private static void assertUnitFinite(float[] normal) {
        assertTrue(Float.isFinite(normal[0]));
        assertTrue(Float.isFinite(normal[1]));
        assertTrue(Float.isFinite(normal[2]));
        double length = Math.sqrt(
                normal[0] * normal[0]
                        + normal[1] * normal[1]
                        + normal[2] * normal[2]);
        assertEquals(1.0, length, 1.0e-6);
        assertTrue("terrain normals must face upward", normal[1] >= 0.0f);
    }

    private static double vectorDistance(float[] left, float[] right) {
        double dx = left[0] - right[0];
        double dy = left[1] - right[1];
        double dz = left[2] - right[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void assertMatchesFreshRebuild(
            CanonicalTerrainMeshRenderer incremental,
            TerrainState state) throws Exception {
        CanonicalTerrainMeshRenderer rebuilt =
                new CanonicalTerrainMeshRenderer();
        rebuilt.rebuild(state.segments(), Vec3.ZERO);
        assertEquals(segmentSlots(rebuilt).keySet(),
                segmentSlots(incremental).keySet());
        for (Long segmentId : segmentSlots(rebuilt).keySet()) {
            assertSegmentDataEquals(rebuilt, incremental, segmentId.longValue());
        }
    }

    private static void assertSegmentDataEquals(
            CanonicalTerrainMeshRenderer expected,
            CanonicalTerrainMeshRenderer actual,
            long segmentId) throws Exception {
        int expectedStart = slotFor(expected, segmentId) * FLOATS_PER_SEGMENT;
        int actualStart = slotFor(actual, segmentId) * FLOATS_PER_SEGMENT;
        FloatBuffer expectedVertices = vertices(expected);
        FloatBuffer actualVertices = vertices(actual);
        for (int offset = 0; offset < FLOATS_PER_SEGMENT; offset++) {
            assertEquals(
                    "segment " + segmentId + " float " + offset,
                    expectedVertices.get(expectedStart + offset),
                    actualVertices.get(actualStart + offset),
                    0.0f);
        }
    }

    private static void assertSlotContainsOnlyZeros(
            CanonicalTerrainMeshRenderer mesh, int slot) throws Exception {
        FloatBuffer data = vertices(mesh);
        int start = slot * FLOATS_PER_SEGMENT;
        for (int offset = 0; offset < FLOATS_PER_SEGMENT; offset++) {
            assertEquals(0.0f, data.get(start + offset), 0.0f);
        }
    }

    private static int slotFor(
            CanonicalTerrainMeshRenderer mesh, long segmentId)
            throws Exception {
        Integer slot = segmentSlots(mesh).get(segmentId);
        if (slot == null) {
            throw new AssertionError("No mesh slot for segment " + segmentId);
        }
        return slot.intValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Integer> segmentSlots(
            CanonicalTerrainMeshRenderer mesh) throws Exception {
        Field field = CanonicalTerrainMeshRenderer.class
                .getDeclaredField("segmentSlots");
        field.setAccessible(true);
        return (Map<Long, Integer>) field.get(mesh);
    }

    private static FloatBuffer vertices(
            CanonicalTerrainMeshRenderer mesh) throws Exception {
        Field field = CanonicalTerrainMeshRenderer.class
                .getDeclaredField("vertices");
        field.setAccessible(true);
        return (FloatBuffer) field.get(mesh);
    }
}
