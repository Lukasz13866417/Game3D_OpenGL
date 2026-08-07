package com.example.game3d.core.simulation;

import com.example.game3d.core.input.FixedStepInput;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainWorld;
import com.example.game3d.core.terrain.TrackBuilder;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SimulationLifecycleTest {
    private final PhysicsConfig config = new PhysicsConfig();

    @Test
    public void fallingOutOfWorldEmitsOneDeathEventAndRemainsTerminal() {
        SimulationEngine engine = new SimulationEngine(
                new TerrainWorld(Collections.emptyList()), config,
                new Vec3(0.0, 1.0, 0.0), 0, StepObserver.NONE);
        int deathEvents = 0;
        StepResult deathResult = null;

        while (!engine.snapshot().dead && engine.snapshot().tick < 300) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            int emitted = countEvents(result, SimulationEvent.Type.PLAYER_DIED);
            deathEvents += emitted;
            if (emitted > 0) {
                deathResult = result;
            }
        }
        StepResult later = engine.step(FixedStepInput.EMPTY);

        assertTrue(engine.snapshot().dead);
        assertEquals(1, deathEvents);
        assertTrue(deathResult != null);
        SimulationEvent death = findEvent(
                deathResult, SimulationEvent.Type.PLAYER_DIED);
        assertEquals(1.0, death.tickFraction, 0.0);
        assertEquals(deathResult.snapshot.timeNanos, death.timeNanos);
        assertEquals(deathResult.snapshot.absolutePosition.x,
                death.position.x, 0.0);
        assertEquals(deathResult.snapshot.absolutePosition.y,
                death.position.y, 0.0);
        assertEquals(deathResult.snapshot.absolutePosition.z,
                death.position.z, 0.0);
        assertEquals(0, countEvents(later, SimulationEvent.Type.PLAYER_DIED));
        assertEquals(JumpRuleId.TERMINAL_REJECT, later.jumpDecision.rule);
    }

    @Test
    public void renderOriginRebasesWithoutChangingAbsolutePhysicsOrGrounding() {
        TerrainWorld terrain = new TrackBuilder(8.0).straight(900.0).build();
        SimulationEngine engine = new SimulationEngine(terrain, config,
                new Vec3(0.0, config.cylinderRadius + 0.002, 1.0),
                0, StepObserver.NONE);

        for (int i = 0; i < 2050; i++) {
            engine.step(FixedStepInput.EMPTY);
        }
        PlayerSnapshot snapshot = engine.snapshot();

        assertTrue(snapshot.absolutePosition.z < -500.0);
        assertTrue(Math.abs(snapshot.position.z) < 100.0);
        assertTrue(snapshot.grounded);
        assertFalse(snapshot.dead);
        assertEquals(snapshot.absolutePosition.y, snapshot.position.y, 1.0e-9);
        assertTrue(Math.abs(snapshot.renderOrigin.z) > 400.0);

        Vec3 rebasedOrigin = snapshot.renderOrigin;
        for (int i = 0; i < 20; i++) {
            snapshot = engine.step(FixedStepInput.EMPTY).snapshot;
            assertEquals(rebasedOrigin, snapshot.renderOrigin);
        }
    }

    private static int countEvents(StepResult result, SimulationEvent.Type type) {
        int count = 0;
        for (SimulationEvent event : result.events) {
            if (event.type == type) count++;
        }
        return count;
    }

    private static SimulationEvent findEvent(
            StepResult result, SimulationEvent.Type type) {
        for (SimulationEvent event : result.events) {
            if (event.type == type) {
                return event;
            }
        }
        throw new AssertionError("Missing event " + type);
    }
}
