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

/**
 * Contract tests for the runtime controls used when the Android game feeds immutable terrain
 * snapshots into the desktop-tested simulation.
 */
public class RuntimeTerrainIntegrationTest {
    private static final double EPSILON = 1.0e-9;
    private final PhysicsConfig config = new PhysicsConfig();

    @Test
    public void replacingTerrainRetainsOnlySupportWhoseStableTriangleIdStillExists() {
        TerrainWorld initial = new TrackBuilder(12.0).straight(200.0).build();
        SimulationEngine engine = new SimulationEngine(initial, config,
                restingStart(), 0, StepObserver.NONE);
        settle(engine);

        PlayerSnapshot supported = engine.snapshot();
        assertTrue(supported.grounded);
        assertTrue(supported.supportTriangleId >= 0L);

        TerrainWorld equivalentSnapshot =
                new TrackBuilder(12.0).straight(200.0).build();
        engine.replaceTerrain(equivalentSnapshot);

        PlayerSnapshot retained = engine.snapshot();
        assertTrue(retained.grounded);
        assertEquals(supported.supportTriangleId, retained.supportTriangleId);
        assertEquals("an equivalent terrain publication must not mutate body state",
                supported.stateHash, retained.stateHash);
        assertTrue(engine.step(FixedStepInput.EMPTY).snapshot.grounded);

        PlayerSnapshot beforeRemoval = engine.snapshot();
        engine.replaceTerrain(new TerrainWorld(Collections.emptyList()));

        PlayerSnapshot removed = engine.snapshot();
        assertFalse(removed.grounded);
        assertEquals(-1L, removed.supportTriangleId);
        assertEquals(beforeRemoval.tick, removed.tick);
        assertEquals(beforeRemoval.timeNanos, removed.timeNanos);
        assertVecEquals(beforeRemoval.absolutePosition, removed.absolutePosition);
    }

    @Test
    public void commandedCruisingSpeedIsARepeatableMotorTarget() {
        TerrainWorld empty = new TerrainWorld(Collections.emptyList());
        SimulationEngine left = airborneEngine(empty);
        SimulationEngine right = airborneEngine(empty);
        SimulationEngine defaultSpeed = airborneEngine(empty);

        double commandedSpeed = 6.0;
        left.setCruisingSpeed(commandedSpeed);
        right.setCruisingSpeed(commandedSpeed);
        assertEquals(commandedSpeed, left.cruisingSpeed(), 0.0);

        for (int tick = 0; tick < 60; tick++) {
            assertEquals(left.step(FixedStepInput.EMPTY).snapshot.stateHash,
                    right.step(FixedStepInput.EMPTY).snapshot.stateHash);
            defaultSpeed.step(FixedStepInput.EMPTY);
        }

        assertEquals(commandedSpeed,
                -left.snapshot().angularVelocity * config.cylinderRadius, EPSILON);
        assertTrue("the runtime command must change the motor target",
                -defaultSpeed.snapshot().angularVelocity * config.cylinderRadius
                        > commandedSpeed + 1.0);
    }

    @Test
    public void deathFloorFollowsTheLastSupportOnAWorldShiftedBelowAbsoluteDeathY() {
        double surfaceY = -100.0;
        TerrainWorld loweredPlatform = new TrackBuilder(12.0)
                .lift(surfaceY)
                .straight(2.0)
                .build();
        SimulationEngine engine = new SimulationEngine(loweredPlatform, config,
                new Vec3(0.0, surfaceY + config.cylinderRadius + 0.002, 1.0),
                0, StepObserver.NONE);

        double lastSupportedY = Double.NaN;
        PlayerSnapshot previous = engine.snapshot();
        StepResult death = null;
        boolean becameAirborne = false;
        for (int tick = 0; tick < 240; tick++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            if (result.snapshot.grounded) {
                lastSupportedY = result.snapshot.absolutePosition.y;
                assertFalse("support below the old absolute bound must remain playable",
                        result.snapshot.dead);
            } else if (!Double.isNaN(lastSupportedY)) {
                becameAirborne = true;
            }
            if (hasEvent(result, SimulationEvent.Type.PLAYER_DIED)) {
                death = result;
                break;
            }
            previous = result.snapshot;
        }

        assertTrue("the lowered platform never established support",
                !Double.isNaN(lastSupportedY));
        assertTrue("the short platform never produced a fall", becameAirborne);
        assertTrue("the relative fall bound was never reached", death != null);
        double expectedFloor = lastSupportedY + config.deathY;
        assertTrue("test floor must be far below the legacy absolute death bound",
                expectedFloor < config.deathY - 50.0);
        assertFalse(previous.dead);
        assertTrue(previous.absolutePosition.y >= expectedFloor);
        assertTrue(death.snapshot.absolutePosition.y < expectedFloor);
    }

    @Test
    public void renderOriginRebasesOnlyComponentsBeyondTheThreshold() {
        assertSingleComponentRebased(new Vec3(600.0, 30.0, 40.0), 0);
        assertSingleComponentRebased(new Vec3(30.0, 600.0, 40.0), 1);
        assertSingleComponentRebased(new Vec3(30.0, 40.0, 600.0), 2);
    }

    private void assertSingleComponentRebased(Vec3 start, int rebasedComponent) {
        TerrainWorld empty = new TerrainWorld(Collections.emptyList());
        SimulationEngine engine = new SimulationEngine(empty, config,
                start, 0, StepObserver.NONE);

        PlayerSnapshot snapshot = engine.step(FixedStepInput.EMPTY).snapshot;
        double[] absolute = {
                snapshot.absolutePosition.x,
                snapshot.absolutePosition.y,
                snapshot.absolutePosition.z
        };
        double[] local = {
                snapshot.position.x,
                snapshot.position.y,
                snapshot.position.z
        };
        for (int component = 0; component < 3; component++) {
            if (component == rebasedComponent) {
                assertEquals("threshold-crossing component was not rebased",
                        0.0, local[component], EPSILON);
            } else {
                assertEquals("an in-range component was rebased unnecessarily",
                        absolute[component], local[component], EPSILON);
            }
        }
    }

    private SimulationEngine airborneEngine(TerrainWorld terrain) {
        return new SimulationEngine(terrain, config,
                new Vec3(0.0, 10.0, 0.0), 0, StepObserver.NONE);
    }

    private Vec3 restingStart() {
        return new Vec3(0.0, config.cylinderRadius + 0.002, 1.0);
    }

    private static void settle(SimulationEngine engine) {
        for (int tick = 0; tick < 8; tick++) {
            engine.step(FixedStepInput.EMPTY);
        }
    }

    private static boolean hasEvent(StepResult result, SimulationEvent.Type type) {
        for (SimulationEvent event : result.events) {
            if (event.type == type) {
                return true;
            }
        }
        return false;
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
