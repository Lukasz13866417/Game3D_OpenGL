package com.example.game3d.terrain.editor.ui;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.SurfaceProperties;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.TerrainVertexAppearance;
import com.example.game3d.core.terrain.addon.AddonFootprint;
import com.example.game3d.core.terrain.addon.DeathSpike;
import javafx.scene.Scene;
import javafx.scene.PerspectiveCamera;
import javafx.geometry.Point3D;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class TerrainPreviewBatchingInteractionTest {
    private TerrainPreviewPane preview;

    @Start void start(Stage stage) {
        preview = new TerrainPreviewPane();
        stage.setScene(new Scene(preview, 900, 650));
        stage.show();
        stage.toFront();
    }

    @Test void largeSnapshotUsesReusableChunksAndFaceSourcePickingMetadata(FxRobot robot) {
        TerrainSnapshot first = snapshot(500, -1, 0.0);
        Map<String, Long> sources = sources(500);
        robot.interact(() -> preview.show(first, sources, Map.of(), ignored -> { }));
        awaitPreviewBuild();

        assertEquals(4, preview.terrainChunkCountForTesting());
        assertEquals(8, preview.terrainMeshViewCountForTesting(),
                "500 tiles with edges enabled must stay within the node-count ceiling");
        assertEquals("tile-0", preview.sourceForFaceForTesting(0, true, 0));
        assertEquals("tile-127", preview.sourceForFaceForTesting(0, false, 254));
        assertEquals("tile-128", preview.sourceForFaceForTesting(1, true, 0));
        robot.interact(() -> assertCameraLooksAtOrbitTarget(preview));
        List<Object> originalChunks = preview.chunkIdentitiesForTesting();

        robot.interact(() -> preview.show(first, sources, Map.of(), ignored -> { }));
        awaitPreviewBuild();
        List<Object> identicalChunks = preview.chunkIdentitiesForTesting();
        assertSame(originalChunks.get(0), identicalChunks.get(0));
        assertSame(originalChunks.get(1), identicalChunks.get(1));
        assertSame(originalChunks.get(2), identicalChunks.get(2));
        assertSame(originalChunks.get(3), identicalChunks.get(3));

        robot.interact(() -> preview.show(
                snapshot(500, 260, .5), sources, Map.of(), ignored -> { }));
        awaitPreviewBuild();
        List<Object> changedChunks = preview.chunkIdentitiesForTesting();
        assertSame(originalChunks.get(0), changedChunks.get(0));
        assertSame(originalChunks.get(1), changedChunks.get(1));
        assertNotSame(originalChunks.get(2), changedChunks.get(2));
        assertSame(originalChunks.get(3), changedChunks.get(3));
    }

    @Test void stateOverlayDisablesStalePickingAndCurrentShowRestoresIt(FxRobot robot) {
        assertFalse(preview.hasCurrentGeometry());
        robot.interact(() -> preview.showInvalid(null));
        assertTrue(((Label) preview.lookup("#preview-state-overlay"))
                .getText().contains("no preview"));

        robot.interact(() -> preview.show(
                snapshot(2, -1, 0), sources(2), Map.of(), ignored -> { }));
        awaitPreviewBuild();
        assertEquals(TerrainPreviewPane.PreviewState.CURRENT, preview.previewState());
        assertTrue(preview.hasCurrentGeometry());
        assertTrue(preview.isPickingEnabled());
        assertTrue(preview.lookup("#preview-geometry-label").isVisible());

        robot.interact(() -> preview.showCompiling(null));
        assertEquals(TerrainPreviewPane.PreviewState.COMPILING, preview.previewState());
        assertFalse(preview.isPickingEnabled());
        assertTrue(preview.lookup("#preview-state-overlay").isVisible());

        robot.interact(() -> preview.showInvalid("Invalid turn"));
        assertEquals(TerrainPreviewPane.PreviewState.STALE_INVALID, preview.previewState());
        assertFalse(preview.isPickingEnabled());

        robot.interact(() -> preview.showFailure("Compiler failed"));
        assertEquals(TerrainPreviewPane.PreviewState.FAILED, preview.previewState());
        assertFalse(preview.isPickingEnabled());

        robot.interact(() -> preview.show(
                snapshot(2, -1, 0), sources(2), Map.of(), ignored -> { }));
        awaitPreviewBuild();
        assertTrue(preview.isPickingEnabled());
        robot.interact(() -> preview.setPickingEnabled(false));
        assertFalse(preview.isPickingEnabled());
    }

    @Test void selectionFramingTargetsOnlyTheSelectedCanonicalTile(FxRobot robot) {
        robot.interact(() -> preview.show(
                snapshot(20, -1, 0), sources(20), Map.of(), ignored -> { }));
        awaitPreviewBuild();
        Vec3 allTarget = preview.orbitTargetForTesting();
        robot.interact(() -> preview.setSelectedSourceIds(Set.of("tile-15")));

        robot.interact(preview::frameSelection);

        Vec3 selectedTarget = preview.orbitTargetForTesting();
        assertFalse(allTarget.equals(selectedTarget));
        assertEquals(0.0, selectedTarget.x, 1.0e-9);
        assertEquals(0.0, selectedTarget.y, 1.0e-9);
        assertEquals(-15.5, selectedTarget.z, 1.0e-9);

        robot.interact(() -> preview.show(
                snapshot(20, 5, .25), sources(20), Map.of(), ignored -> { }));
        awaitPreviewBuild();
        assertEquals(selectedTarget, preview.orbitTargetForTesting(),
                "ordinary recompiles must preserve the user's camera target");

        // The old 20%-of-radius tolerance retained a target just beyond the replacement track.
        // Once the target is outside the actual bounds, even by less than that tolerance, the
        // replacement must be framed around its own center.
        robot.interact(() -> preview.show(
                shiftedSnapshot(20, 5.2), sources(20), Map.of(), ignored -> { }));
        awaitPreviewBuild();
        assertEquals(-4.8, preview.orbitTargetForTesting().z, 1.0e-9,
                "a target just outside replacement bounds must auto-frame");

        robot.interact(() -> preview.show(
                shiftedSnapshot(20, -1000), sources(20), Map.of(), ignored -> { }));
        awaitPreviewBuild();
        assertEquals(-1010.0, preview.orbitTargetForTesting().z, 1.0e-9,
                "a replacement far outside the retained view must auto-frame");
    }

    @Test void spikeGeometryIsPreparedOffFxAndReusedInBoundedPickableChunks(
            FxRobot robot) {
        TerrainSnapshot first = snapshotWithSpikes(300, -1, 0.8);
        Map<String, Long> addonSources = addonSources(300);
        robot.interact(() -> preview.show(
                first, sources(300), addonSources, ignored -> { }));
        awaitPreviewBuild();

        assertFalse(preview.lastBufferBuildRanOnFxThreadForTesting());
        assertEquals(3, preview.spikeChunkCountForTesting());
        assertEquals("spike-0", preview.spikeSourceForFaceForTesting(0, 0));
        assertEquals("spike-127", preview.spikeSourceForFaceForTesting(0, 511));
        assertEquals("spike-128", preview.spikeSourceForFaceForTesting(1, 0));
        List<Object> original = preview.spikeChunkIdentitiesForTesting();

        robot.interact(() -> preview.show(
                first, sources(300), addonSources, ignored -> { }));
        awaitPreviewBuild();
        List<Object> identical = preview.spikeChunkIdentitiesForTesting();
        assertSame(original.get(0), identical.get(0));
        assertSame(original.get(1), identical.get(1));
        assertSame(original.get(2), identical.get(2));

        robot.interact(() -> preview.show(snapshotWithSpikes(300, 260, 1.1),
                sources(300), addonSources, ignored -> { }));
        awaitPreviewBuild();
        List<Object> changed = preview.spikeChunkIdentitiesForTesting();
        assertSame(original.get(0), changed.get(0));
        assertSame(original.get(1), changed.get(1));
        assertNotSame(original.get(2), changed.get(2));
    }

    private static TerrainSnapshot snapshot(int count, int changedIndex, double lift) {
        List<TerrainSegment> segments = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double y = index == changedIndex ? lift : 0.0;
            segments.add(new TerrainSegment(index,
                    new Vec3(-1.6, y, -index),
                    new Vec3(1.6, y, -index),
                    new Vec3(-1.6, y, -index - 1),
                    new Vec3(1.6, y, -index - 1),
                    true, index > 0, SurfaceProperties.NORMAL,
                    TerrainVertexAppearance.DEFAULT, TerrainVertexAppearance.DEFAULT,
                    TerrainVertexAppearance.DEFAULT, TerrainVertexAppearance.DEFAULT,
                    List.of()));
        }
        return new TerrainSnapshot(0, count - 1L, 0, segments);
    }

    private static TerrainSnapshot shiftedSnapshot(int count, double zOffset) {
        List<TerrainSegment> segments = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            double near = zOffset - index;
            segments.add(new TerrainSegment(index,
                    new Vec3(-1.6, 0, near), new Vec3(1.6, 0, near),
                    new Vec3(-1.6, 0, near - 1), new Vec3(1.6, 0, near - 1),
                    true, index > 0, SurfaceProperties.NORMAL,
                    TerrainVertexAppearance.DEFAULT, TerrainVertexAppearance.DEFAULT,
                    TerrainVertexAppearance.DEFAULT, TerrainVertexAppearance.DEFAULT,
                    List.of()));
        }
        return new TerrainSnapshot(0, count - 1L, 0, segments);
    }

    private static Map<String, Long> sources(int count) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            result.put("tile-" + index, (long) index);
        }
        return result;
    }

    private static Map<String, Long> addonSources(int count) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            result.put("spike-" + index, index + 1L);
        }
        return result;
    }

    private static TerrainSnapshot snapshotWithSpikes(
            int count, int changedIndex, double changedHeight) {
        List<TerrainSegment> segments = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Vec3 center = new Vec3(0, 0, -index - .5);
            double height = index == changedIndex ? changedHeight : .8;
            Vec3 nearLeft = center.add(new Vec3(-.2, 0, .2));
            Vec3 nearRight = center.add(new Vec3(.2, 0, .2));
            Vec3 farLeft = center.add(new Vec3(-.2, 0, -.2));
            Vec3 farRight = center.add(new Vec3(.2, 0, -.2));
            DeathSpike spike = new DeathSpike(
                    nearLeft, nearRight, farLeft, farRight,
                    center.add(Vec3.UP.multiply(height)), Vec3.UP,
                    0, center, .2, height);
            spike.place(index + 1L, index, AddonFootprint.quadrilateral(
                    nearLeft, nearRight, farLeft, farRight));
            segments.add(new TerrainSegment(index,
                    new Vec3(-1.6, 0, -index),
                    new Vec3(1.6, 0, -index),
                    new Vec3(-1.6, 0, -index - 1),
                    new Vec3(1.6, 0, -index - 1),
                    true, index > 0, SurfaceProperties.NORMAL,
                    TerrainVertexAppearance.DEFAULT, TerrainVertexAppearance.DEFAULT,
                    TerrainVertexAppearance.DEFAULT, TerrainVertexAppearance.DEFAULT,
                    List.of(spike)));
        }
        return new TerrainSnapshot(0, count - 1L, 0, segments);
    }

    private void awaitPreviewBuild() {
        try {
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                    () -> preview.completedBuildTicketForTesting()
                            == preview.requestedBuildTicketForTesting());
            WaitForAsyncUtils.waitForFxEvents();
        } catch (TimeoutException timeout) {
            throw new AssertionError("Preview build did not attach", timeout);
        }
    }

    private static void assertCameraLooksAtOrbitTarget(TerrainPreviewPane value) {
        PerspectiveCamera camera = (PerspectiveCamera) value.cameraForTesting();
        Point3D renderedForward = camera.getLocalToParentTransform()
                .deltaTransform(0, 0, 1).normalize();
        Vec3 target = value.orbitTargetForTesting();
        Vec3 expected = target.subtract(new Vec3(camera.getTranslateX(),
                camera.getTranslateY(), camera.getTranslateZ())).normalized();
        assertEquals(expected.x, renderedForward.getX(), 1.0e-9);
        assertEquals(expected.y, renderedForward.getY(), 1.0e-9);
        assertEquals(expected.z, renderedForward.getZ(), 1.0e-9);
    }
}
