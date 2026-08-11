package com.example.game3d.core.simulation;

import com.example.game3d.core.input.FixedStepInput;
import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainWorld;
import com.example.game3d.core.terrain.TrackBuilder;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SimulationEngineTest {
    private final PhysicsConfig config = new PhysicsConfig();

    @Test
    public void identicalRunsProduceIdenticalHashes() {
        TerrainWorld terrain = new TrackBuilder(8.0).straight(200.0).build();
        SimulationEngine left = engine(terrain, 0);
        SimulationEngine right = engine(terrain, 0);

        for (int i = 0; i < 300; i++) {
            assertEquals(left.step(FixedStepInput.EMPTY).snapshot.stateHash,
                    right.step(FixedStepInput.EMPTY).snapshot.stateHash);
        }
    }

    @Test
    public void motorSettlesAndRollsOnFlatTerrain() {
        SimulationEngine engine = engine(new TrackBuilder(8.0).straight(200.0).build(), 0);
        for (int i = 0; i < 180; i++) {
            engine.step(FixedStepInput.EMPTY);
        }
        PlayerSnapshot state = engine.snapshot();
        assertTrue(state.grounded);
        assertEquals(config.cylinderRadius, state.absolutePosition.y, 0.003);
        assertEquals(-config.cruisingSpeed, state.velocity.z, 0.1);
        assertFalse(state.dead);
    }

    @Test
    public void groundedJumpRequiresRelease() {
        SimulationEngine engine = engine(new TrackBuilder(8.0).straight(200.0).build(), 0);
        settle(engine);
        StepResult held = engine.step(new FixedStepInput(Arrays.asList(
                PlayerInputEvent.down(engine.snapshot().timeNanos, 1),
                PlayerInputEvent.swipe(engine.snapshot().timeNanos, 2, 0.0, -0.30))));
        assertFalse(hasEvent(held, SimulationEvent.Type.JUMP));

        PlayerSnapshot takeoffState = engine.snapshot();
        StepResult released = engine.step(new FixedStepInput(Collections.singletonList(
                PlayerInputEvent.up(engine.snapshot().timeNanos, 3))));
        assertTrue(hasEvent(released, SimulationEvent.Type.JUMP));
        assertEquals(JumpRuleId.GROUNDED_RELEASED, released.jumpDecision.rule);
        SimulationEvent jump = event(released, SimulationEvent.Type.JUMP);
        assertEquals(0.0, jump.tickFraction, 0.0);
        assertEquals(takeoffState.timeNanos, jump.timeNanos);
        assertEquals(takeoffState.absolutePosition.y, jump.position.y, 1.0e-12);
    }

    @Test
    public void chargedHeldTouchJumpsOnFirstLandingAndSuppressesBounce() {
        TerrainWorld terrain = new TrackBuilder(8.0).straight(200.0).build();
        SimulationEngine engine = new SimulationEngine(terrain, config,
                new Vec3(0.0, 3.0, 1.0), 0, StepObserver.NONE);
        engine.step(new FixedStepInput(Arrays.asList(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(1L, 2, 0.0, -0.30))));

        StepResult landing = null;
        for (int i = 0; i < 100; i++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            if (hasEvent(result, SimulationEvent.Type.JUMP)) {
                landing = result;
                break;
            }
        }
        assertTrue("landing jump was not produced", landing != null);
        assertEquals(JumpRuleId.LANDING_CHARGED, landing.jumpDecision.rule);
        assertFalse(hasEvent(landing, SimulationEvent.Type.BOUNCE));
        assertTrue(landing.snapshot.velocity.y > 0.0);
        SimulationEvent jump = event(landing, SimulationEvent.Type.JUMP);
        assertTrue(jump.tickFraction > 0.0 && jump.tickFraction <= 1.0);
        assertTrue(jump.position != null);
        assertTrue(jump.timeNanos >= landing.snapshot.timeNanos - PhysicsConfig.FIXED_DT_NANOS);
    }

    @Test
    public void armedLandingJumpKeepsReleasedBarFullUntilImpact() {
        TerrainWorld terrain = new TrackBuilder(8.0).straight(200.0).build();
        SimulationEngine engine = new SimulationEngine(terrain, config,
                new Vec3(0.0, 1.4, 1.0), new Vec3(0.0, -1.0, 0.0),
                0, StepObserver.NONE);
        StepResult released = engine.step(new FixedStepInput(Arrays.asList(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(1L, 2, 0.0, -0.30),
                PlayerInputEvent.up(2L, 3))));
        double charged = engine.snapshot().gestureCharge;
        assertTrue(released.snapshot.landingJumpArmed);
        assertTrue(charged >= config.jumpChargeThreshold);

        boolean jumped = false;
        for (int i = 0; i < config.landingJumpBufferTicks; i++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            if (hasEvent(result, SimulationEvent.Type.JUMP)) {
                jumped = true;
                break;
            }
            assertTrue(result.snapshot.landingJumpArmed);
            assertEquals(charged, result.snapshot.gestureCharge, 1.0e-9);
        }
        assertTrue("armed landing jump did not reach its predicted impact", jumped);
    }

    @Test
    public void featherAddsOnePersistentChargeExactlyOnce() {
        TerrainWorld terrain = new TrackBuilder(8.0)
                .straight(1.0)
                .feather(0.0, 0.0, config.cylinderRadius, 0.3)
                .straight(100.0)
                .build();
        SimulationEngine engine = engine(terrain, 0);
        int collections = 0;
        for (int i = 0; i < 120; i++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            if (hasEvent(result, SimulationEvent.Type.FEATHER_COLLECTED)) {
                collections++;
            }
        }
        assertEquals(1, collections);
        assertEquals(1, engine.snapshot().airJumpCharges);
    }

    @Test
    public void airborneFacingDoesNotSteerUntilLandingThenSnapsVelocity() {
        TerrainWorld terrain = new TrackBuilder(30.0).straight(200.0).build();
        SimulationEngine engine = new SimulationEngine(terrain, config,
                new Vec3(0.0, 2.0, 1.0), new Vec3(0.0, 0.0, -10.0),
                0, StepObserver.NONE);
        engine.step(new FixedStepInput(Collections.singletonList(
                PlayerInputEvent.swipe(0L, 1L, 0.20, 0.0))));
        assertEquals(0.0, engine.snapshot().velocity.x, 1.0e-9);

        StepResult landing = null;
        for (int i = 0; i < 100; i++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            if (hasEvent(result, SimulationEvent.Type.LAND)) {
                landing = result;
                break;
            }
        }
        assertTrue(landing != null);
        Vec3 horizontal = landing.snapshot.velocity.withY(0.0).normalized();
        assertEquals(landing.snapshot.heading.x, horizontal.x, 1.0e-9);
        assertEquals(landing.snapshot.heading.z, horizontal.z, 1.0e-9);
    }

    @Test
    public void deferredFacingInputRealignsVelocityImmediatelyAfterLanding() {
        TerrainWorld terrain = new TrackBuilder(30.0).straight(200.0).build();
        SimulationEngine engine = new SimulationEngine(terrain, config,
                new Vec3(0.0, 2.0, 1.0), new Vec3(0.0, 0.0, -10.0),
                0, StepObserver.NONE);

        StepResult landing = null;
        for (int i = 0; i < 100; i++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            if (hasEvent(result, SimulationEvent.Type.LAND)) {
                landing = result;
                break;
            }
        }
        assertTrue(landing != null);

        StepResult redirected = engine.step(new FixedStepInput(
                Collections.singletonList(
                        PlayerInputEvent.swipe(
                                landing.snapshot.timeNanos,
                                1L,
                                0.375,
                                0.0))));

        assertTrue(redirected.snapshot.grounded);
        Vec3 horizontal =
                redirected.snapshot.velocity.withY(0.0).normalized();
        assertEquals(redirected.snapshot.heading.x, horizontal.x, 1.0e-9);
        assertEquals(redirected.snapshot.heading.z, horizontal.z, 1.0e-9);
    }

    @Test
    public void highSpeedFallDoesNotTunnelThroughTerrain() {
        TerrainWorld terrain = new TrackBuilder(20.0).straight(200.0).build();
        SimulationEngine engine = new SimulationEngine(terrain, config,
                new Vec3(0.0, 4.0, 1.0), new Vec3(0.0, -500.0, -1.0),
                0, StepObserver.NONE);

        StepResult result = engine.step(FixedStepInput.EMPTY);

        assertTrue(result.snapshot.absolutePosition.y >= config.cylinderRadius - 0.002);
        assertTrue(result.snapshot.velocity.y >= 0.0);
        assertFalse(result.snapshot.dead);
    }

    @Test
    public void oneSidedTerrainDoesNotTeleportBodyFromBelow() {
        TerrainWorld terrain = new TrackBuilder(20.0).straight(200.0).build();
        SimulationEngine engine = new SimulationEngine(terrain, config,
                new Vec3(0.0, -0.05, 1.0), new Vec3(0.0, -1.0, 0.0),
                0, StepObserver.NONE);

        engine.step(FixedStepInput.EMPTY);

        assertTrue(engine.snapshot().absolutePosition.y < -0.05);
        assertFalse(engine.snapshot().grounded);
    }

    private SimulationEngine engine(TerrainWorld terrain, int charges) {
        return new SimulationEngine(terrain, config,
                new Vec3(0.0, config.cylinderRadius + 0.002, 1.0),
                charges, StepObserver.NONE);
    }

    private static void settle(SimulationEngine engine) {
        for (int i = 0; i < 5; i++) {
            engine.step(FixedStepInput.EMPTY);
        }
    }

    private static boolean hasEvent(StepResult result, SimulationEvent.Type type) {
        return event(result, type) != null;
    }

    private static SimulationEvent event(StepResult result, SimulationEvent.Type type) {
        for (SimulationEvent event : result.events) {
            if (event.type == type) return event;
        }
        return null;
    }
}
