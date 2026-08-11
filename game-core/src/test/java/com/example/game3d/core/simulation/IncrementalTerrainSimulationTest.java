package com.example.game3d.core.simulation;

import com.example.game3d.core.input.FixedStepInput;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.SurfaceProperties;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.TerrainVertexAppearance;
import com.example.game3d.core.terrain.TrackBuilder;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IncrementalTerrainSimulationTest {
    @Test
    public void identicalUpsertPreservesSupportButGeometryChangeClearsIt() {
        TerrainSnapshot initial = new TrackBuilder(12.0)
                .straight(200.0)
                .buildSnapshot();
        PhysicsConfig config = new PhysicsConfig();
        SimulationEngine engine = new SimulationEngine(
                initial, config,
                new Vec3(0.0, config.cylinderRadius + 0.002, 1.0),
                0, StepObserver.NONE);
        for (int i = 0; i < 8; i++) {
            engine.step(FixedStepInput.EMPTY);
        }
        PlayerSnapshot supported = engine.snapshot();
        assertTrue(supported.grounded);
        TerrainSegment original = initial.segments.get(0);

        engine.applyTerrainCommit(new TerrainCommit(
                0L, 1L, 0L, 0L, Collections.singletonList(original)));
        assertTrue(engine.snapshot().grounded);
        assertEquals(supported.supportTriangleId, engine.snapshot().supportTriangleId);

        TerrainSegment moved = new TerrainSegment(
                original.id,
                original.nearLeft.add(Vec3.UP),
                original.nearRight.add(Vec3.UP),
                original.farLeft.add(Vec3.UP),
                original.farRight.add(Vec3.UP),
                true,
                original.connectedToPrevious,
                SurfaceProperties.NORMAL,
                TerrainVertexAppearance.DEFAULT,
                TerrainVertexAppearance.DEFAULT,
                TerrainVertexAppearance.DEFAULT,
                TerrainVertexAppearance.DEFAULT,
                original.features);
        engine.applyTerrainCommit(new TerrainCommit(
                1L, 2L, 0L, 0L, Collections.singletonList(moved)));
        assertFalse(engine.snapshot().grounded);
        assertEquals(-1L, engine.snapshot().supportTriangleId);
        assertEquals(supported.tick, engine.snapshot().tick);
    }

    @Test
    public void frameSnapshotCarriesTerrainRevisionAndStableProgressSegment() {
        TerrainSnapshot initial = new TrackBuilder(12.0)
                .straight(200.0)
                .buildSnapshot();
        PhysicsConfig config = new PhysicsConfig();
        SimulationEngine engine = new SimulationEngine(
                initial, config,
                new Vec3(0.0, config.cylinderRadius + 0.002, 1.0),
                0, StepObserver.NONE);
        for (int i = 0; i < 8; i++) {
            engine.step(FixedStepInput.EMPTY);
        }

        SimulationFrameSnapshot frame = engine.frameSnapshot();
        assertEquals(0L, frame.terrainRevision);
        assertTrue(frame.player.supportSegmentId >= 0L);
        assertEquals(frame.player.supportSegmentId, frame.player.lastSupportedSegmentId);
    }
}
