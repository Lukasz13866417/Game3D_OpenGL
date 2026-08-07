package com.example.game3d.core.simulation;

import com.example.game3d.core.input.FixedStepInput;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainWorld;
import com.example.game3d.core.terrain.TrackBuilder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FeatureInteractionTest {
    private final PhysicsConfig config = new PhysicsConfig();

    @Test
    public void multipleFeathersEachAddOnePersistentCharge() {
        TerrainWorld terrain = new TrackBuilder(8.0)
                .straight(1.0)
                .feather(0.0, 0.0, config.cylinderRadius, 0.25)
                .feather(2.0, 0.0, config.cylinderRadius, 0.25)
                .straight(100.0)
                .build();
        SimulationEngine engine = restingEngine(terrain);
        int collections = 0;
        StepResult firstCollection = null;

        for (int i = 0; i < 120; i++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            int events = countEvents(result, SimulationEvent.Type.FEATHER_COLLECTED);
            collections += events;
            if (events > 0 && firstCollection == null) {
                firstCollection = result;
            }
        }

        assertEquals(2, collections);
        assertEquals(2, engine.snapshot().airJumpCharges);
        assertNotNull(firstCollection);
        SimulationEvent collection = onlyEvent(
                firstCollection, SimulationEvent.Type.FEATHER_COLLECTED);
        assertEquals(1.0, collection.tickFraction, 0.0);
        assertEquals(firstCollection.snapshot.timeNanos, collection.timeNanos);
        assertVecEquals(firstCollection.snapshot.absolutePosition,
                collection.position, 0.0);
    }

    @Test
    public void featherLateralNearMissDoesNotCollect() {
        TerrainWorld terrain = new TrackBuilder(8.0)
                .straight(1.0)
                .feather(0.0, 2.0, config.cylinderRadius, 0.2)
                .straight(100.0)
                .build();
        SimulationEngine engine = restingEngine(terrain);

        for (int i = 0; i < 100; i++) {
            assertEquals(0, countEvents(engine.step(FixedStepInput.EMPTY),
                    SimulationEvent.Type.FEATHER_COLLECTED));
        }

        assertEquals(0, engine.snapshot().airJumpCharges);
    }

    @Test
    public void spikeLateralNearMissDoesNotKill() {
        TerrainWorld terrain = new TrackBuilder(8.0)
                .straight(2.0)
                .spike(0.0, 2.0, 0.35, 1.0)
                .straight(100.0)
                .build();
        SimulationEngine engine = restingEngine(terrain);

        for (int i = 0; i < 120; i++) {
            engine.step(FixedStepInput.EMPTY);
        }

        assertFalse(engine.snapshot().dead);
    }

    @Test
    public void spikeDeathAndPlayerDiedEventsAreEmittedOnlyOnce() {
        TerrainWorld terrain = new TrackBuilder(8.0)
                .straight(1.0)
                .spike(0.0, 0.0, 0.4, 1.0)
                .straight(20.0)
                .build();
        SimulationEngine engine = restingEngine(terrain);

        StepResult hit = engine.step(FixedStepInput.EMPTY);
        StepResult terminalTick = engine.step(FixedStepInput.EMPTY);

        assertTrue(hit.snapshot.dead);
        assertEquals(1, countEvents(hit, SimulationEvent.Type.SPIKE_HIT));
        assertEquals(1, countEvents(hit, SimulationEvent.Type.PLAYER_DIED));
        SimulationEvent spike = onlyEvent(hit, SimulationEvent.Type.SPIKE_HIT);
        SimulationEvent death = onlyEvent(hit, SimulationEvent.Type.PLAYER_DIED);
        assertNotNull(spike.position);
        assertTrue(spike.tickFraction > 0.0 && spike.tickFraction <= 1.0);
        assertEquals(spike.timeNanos, death.timeNanos);
        assertEquals(spike.tickFraction, death.tickFraction, 0.0);
        assertVecEquals(spike.position, death.position, 0.0);
        assertEquals(0, countEvents(terminalTick, SimulationEvent.Type.SPIKE_HIT));
        assertEquals(0, countEvents(terminalTick, SimulationEvent.Type.PLAYER_DIED));
        assertEquals(JumpRuleId.TERMINAL_REJECT, terminalTick.jumpDecision.rule);
    }

    private SimulationEngine restingEngine(TerrainWorld terrain) {
        return new SimulationEngine(terrain, config,
                new Vec3(0.0, config.cylinderRadius + 0.002, 1.0),
                0, StepObserver.NONE);
    }

    private static int countEvents(StepResult result, SimulationEvent.Type type) {
        int count = 0;
        for (SimulationEvent event : result.events) {
            if (event.type == type) count++;
        }
        return count;
    }

    private static SimulationEvent onlyEvent(
            StepResult result, SimulationEvent.Type type) {
        SimulationEvent found = null;
        for (SimulationEvent event : result.events) {
            if (event.type == type) {
                assertTrue("multiple " + type + " events in one tick", found == null);
                found = event;
            }
        }
        assertNotNull(found);
        return found;
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual, double tolerance) {
        assertNotNull(actual);
        assertEquals(expected.x, actual.x, tolerance);
        assertEquals(expected.y, actual.y, tolerance);
        assertEquals(expected.z, actual.z, tolerance);
    }
}
