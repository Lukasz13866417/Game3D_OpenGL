package com.example.game3d.core.simulation;

import com.example.game3d.core.input.FixedStepInput;
import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainWorld;
import com.example.game3d.core.terrain.TrackBuilder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Regression coverage for sensitivity-independent jump-swipe path classification. */
public class JumpGestureClassificationTest {
    private static final double EPSILON = 1.0e-9;

    private final PhysicsConfig config = new PhysicsConfig();

    @Test
    public void defaultHorizontalLimitsMatchTheRequestedTightening() {
        assertEquals(0.06, config.maxJumpChargeXScreenHeights, 0.0);
        assertEquals(1.20, config.maxJumpChargeXToYRatio, 0.0);
    }

    @Test
    public void relativeHorizontalBoundarySeparatesChargeAndReleaseJump() {
        double rawUpward = 0.040;
        double ratioBoundary = config.maxJumpChargeXToYRatio * rawUpward;
        SimulationEngine accepted = groundedEngine();
        SimulationEngine rejected = groundedEngine();
        long acceptedNow = accepted.snapshot().timeNanos;
        long rejectedNow = rejected.snapshot().timeNanos;

        StepResult acceptedSwipe = accepted.step(input(
                PlayerInputEvent.down(acceptedNow, 1L),
                swipe(acceptedNow, 2L,
                        0.0, -rawUpward,
                        ratioBoundary - 1.0e-6, -rawUpward)));
        StepResult rejectedSwipe = rejected.step(input(
                PlayerInputEvent.down(rejectedNow, 1L),
                swipe(rejectedNow, 2L,
                        0.0, -rawUpward,
                        ratioBoundary + 1.0e-6, -rawUpward)));

        assertEquals(rawUpward * config.swipeChargePerScreenHeight,
                acceptedSwipe.snapshot.gestureCharge, EPSILON);
        assertEquals(0.0, rejectedSwipe.snapshot.gestureCharge, 0.0);

        StepResult acceptedRelease = accepted.step(input(
                PlayerInputEvent.up(accepted.snapshot().timeNanos, 3L)));
        StepResult rejectedRelease = rejected.step(input(
                PlayerInputEvent.up(rejected.snapshot().timeNanos, 3L)));
        assertTrue(hasEvent(acceptedRelease, SimulationEvent.Type.JUMP));
        assertFalse(hasEvent(rejectedRelease, SimulationEvent.Type.JUMP));
    }

    @Test
    public void absoluteHorizontalBoundarySeparatesChargeAndReleaseJump() {
        double rawUpward = 0.100;
        double absoluteBoundary = config.maxJumpChargeXScreenHeights;
        SimulationEngine accepted = groundedEngine();
        SimulationEngine rejected = groundedEngine();
        long acceptedNow = accepted.snapshot().timeNanos;
        long rejectedNow = rejected.snapshot().timeNanos;

        StepResult acceptedSwipe = accepted.step(input(
                PlayerInputEvent.down(acceptedNow, 1L),
                swipe(acceptedNow, 2L,
                        0.0, -rawUpward,
                        absoluteBoundary - 1.0e-6, -rawUpward)));
        StepResult rejectedSwipe = rejected.step(input(
                PlayerInputEvent.down(rejectedNow, 1L),
                swipe(rejectedNow, 2L,
                        0.0, -rawUpward,
                        absoluteBoundary + 1.0e-6, -rawUpward)));

        assertEquals(rawUpward * config.swipeChargePerScreenHeight,
                acceptedSwipe.snapshot.gestureCharge, EPSILON);
        assertEquals(0.0, rejectedSwipe.snapshot.gestureCharge, 0.0);

        StepResult acceptedRelease = accepted.step(input(
                PlayerInputEvent.up(accepted.snapshot().timeNanos, 3L)));
        StepResult rejectedRelease = rejected.step(input(
                PlayerInputEvent.up(rejected.snapshot().timeNanos, 3L)));
        assertTrue(hasEvent(acceptedRelease, SimulationEvent.Type.JUMP));
        assertFalse(hasEvent(rejectedRelease, SimulationEvent.Type.JUMP));
    }

    @Test
    public void coalescedAndPacketizedPathsProduceTheSameAuthoritativeState() {
        SimulationEngine coalesced = groundedEngine();
        SimulationEngine packetized = groundedEngine();
        long coalescedNow = coalesced.snapshot().timeNanos;
        long packetizedNow = packetized.snapshot().timeNanos;

        coalesced.step(input(
                PlayerInputEvent.down(coalescedNow, 1L),
                swipe(coalescedNow, 2L, 0.050, -0.050, 0.050, -0.050)));

        List<PlayerInputEvent> packets = new ArrayList<PlayerInputEvent>();
        packets.add(PlayerInputEvent.down(packetizedNow, 1L));
        for (int i = 0; i < 10; i++) {
            packets.add(swipe(packetizedNow, 2L + i,
                    0.005, -0.005, 0.005, -0.005));
        }
        packetized.step(new FixedStepInput(packets));

        PlayerSnapshot expected = coalesced.snapshot();
        PlayerSnapshot actual = packetized.snapshot();
        assertEquals(expected.gestureCharge, actual.gestureCharge, EPSILON);
        assertEquals(expected.yawRadians, actual.yawRadians, EPSILON);
        assertEquals(expected.stateHash, actual.stateHash);
    }

    @Test
    public void smallPacketsCannotBypassTheCumulativeAbsoluteLimit() {
        SimulationEngine packetized = groundedEngine();
        SimulationEngine coalesced = groundedEngine();
        long packetizedNow = packetized.snapshot().timeNanos;
        long coalescedNow = coalesced.snapshot().timeNanos;

        List<PlayerInputEvent> packets = new ArrayList<PlayerInputEvent>();
        packets.add(PlayerInputEvent.down(packetizedNow, 1L));
        for (int i = 0; i < 10; i++) {
            packets.add(swipe(packetizedNow, 2L + i,
                    0.0, -0.010, 0.007, -0.010));
        }
        StepResult packetizedResult = packetized.step(new FixedStepInput(packets));
        StepResult coalescedResult = coalesced.step(input(
                PlayerInputEvent.down(coalescedNow, 1L),
                swipe(coalescedNow, 2L, 0.0, -0.100, 0.070, -0.100)));

        assertEquals(0.0, packetizedResult.snapshot.gestureCharge, 0.0);
        assertEquals(0.0, coalescedResult.snapshot.gestureCharge, 0.0);
    }

    @Test
    public void laterHorizontalExcursionInvalidatesEarlierVisibleCharge() {
        SimulationEngine engine = groundedEngine();
        long now = engine.snapshot().timeNanos;
        StepResult charged = engine.step(input(
                PlayerInputEvent.down(now, 1L),
                swipe(now, 2L, 0.0, -0.050, 0.0, -0.050)));
        assertTrue(charged.snapshot.gestureCharge >= config.jumpChargeThreshold);

        double yawBefore = charged.snapshot.yawRadians;
        now = engine.snapshot().timeNanos;
        StepResult invalidated = engine.step(input(
                swipe(now, 3L, 0.061, 0.0, 0.061, 0.0)));

        assertEquals(0.0, invalidated.snapshot.gestureCharge, 0.0);
        assertNotEquals(yawBefore, invalidated.snapshot.yawRadians, EPSILON);

        StepResult release = engine.step(input(
                PlayerInputEvent.up(engine.snapshot().timeNanos, 4L)));
        assertFalse(hasEvent(release, SimulationEvent.Type.JUMP));
    }

    @Test
    public void peakHorizontalExcursionCannotBeHiddenByReturningToTheStartX() {
        SimulationEngine engine = groundedEngine();
        long now = engine.snapshot().timeNanos;

        StepResult result = engine.step(input(
                PlayerInputEvent.down(now, 1L),
                swipe(now, 2L, 0.0, 0.0, 0.061, 0.0),
                swipe(now, 3L, 0.0, 0.0, -0.061, 0.0),
                swipe(now, 4L, 0.0, -0.100, 0.0, -0.100)));

        assertEquals(0.0, result.snapshot.gestureCharge, 0.0);
    }

    @Test
    public void additionalUpwardTravelCanRecoverTheRelativeGuard() {
        SimulationEngine engine = groundedEngine();
        long now = engine.snapshot().timeNanos;
        StepResult initiallyTooDiagonal = engine.step(input(
                PlayerInputEvent.down(now, 1L),
                swipe(now, 2L, 0.0, -0.020, 0.040, -0.020)));
        assertEquals(0.0, initiallyTooDiagonal.snapshot.gestureCharge, 0.0);

        now = engine.snapshot().timeNanos;
        StepResult recovered = engine.step(input(
                swipe(now, 3L, 0.0, -0.020, 0.0, -0.020)));

        assertEquals(0.040 * config.swipeChargePerScreenHeight,
                recovered.snapshot.gestureCharge, EPSILON);
    }

    @Test
    public void rejectedChargeSwipeStillChangesFacing() {
        SimulationEngine engine = groundedEngine();
        double adjustedX = 0.050;
        double rawY = -0.040;
        long now = engine.snapshot().timeNanos;

        StepResult result = engine.step(input(
                PlayerInputEvent.down(now, 1L),
                swipe(now, 2L, adjustedX, rawY, 0.050, rawY)));

        assertEquals(0.0, result.snapshot.gestureCharge, 0.0);
        assertEquals(adjustedX * config.facingRadiansPerScreenHeight,
                result.snapshot.yawRadians, EPSILON);
    }

    @Test
    public void hiddenPeakExcursionIsHashedBecauseItChangesFutureCharging() {
        SimulationEngine cleanPath = groundedEngine();
        SimulationEngine excessiveExcursion = groundedEngine();
        long cleanNow = cleanPath.snapshot().timeNanos;
        long excursionNow = excessiveExcursion.snapshot().timeNanos;

        cleanPath.step(input(PlayerInputEvent.down(cleanNow, 1L)));
        excessiveExcursion.step(input(
                PlayerInputEvent.down(excursionNow, 1L),
                swipe(excursionNow, 2L, 0.0, 0.0, 0.061, 0.0),
                swipe(excursionNow, 3L, 0.0, 0.0, -0.061, 0.0)));

        PlayerSnapshot cleanBefore = cleanPath.snapshot();
        PlayerSnapshot excursionBefore = excessiveExcursion.snapshot();
        assertEquals(cleanBefore.gestureCharge, excursionBefore.gestureCharge, 0.0);
        assertEquals(cleanBefore.yawRadians, excursionBefore.yawRadians, 0.0);
        assertEquals(cleanBefore.position.x, excursionBefore.position.x, EPSILON);
        assertEquals(cleanBefore.position.y, excursionBefore.position.y, EPSILON);
        assertEquals(cleanBefore.position.z, excursionBefore.position.z, EPSILON);
        assertNotEquals(cleanBefore.stateHash, excursionBefore.stateHash);

        long nextClean = cleanPath.snapshot().timeNanos;
        long nextExcursion = excessiveExcursion.snapshot().timeNanos;
        StepResult cleanCharged = cleanPath.step(input(
                swipe(nextClean, 4L, 0.0, -0.100, 0.0, -0.100)));
        StepResult excursionRejected = excessiveExcursion.step(input(
                swipe(nextExcursion, 4L, 0.0, -0.100, 0.0, -0.100)));

        assertTrue(cleanCharged.snapshot.gestureCharge >= config.jumpChargeThreshold);
        assertEquals(0.0, excursionRejected.snapshot.gestureCharge, 0.0);
    }

    private SimulationEngine groundedEngine() {
        TerrainWorld terrain = new TrackBuilder(8.0).straight(200.0).build();
        SimulationEngine engine = new SimulationEngine(
                terrain,
                config,
                new Vec3(0.0, config.cylinderRadius + 0.002, 1.0),
                0,
                StepObserver.NONE);
        for (int i = 0; i < 5; i++) {
            engine.step(FixedStepInput.EMPTY);
        }
        assertTrue(engine.snapshot().grounded);
        return engine;
    }

    private static PlayerInputEvent swipe(
            long timeNanos, long sequence,
            double adjustedX, double adjustedY,
            double rawX, double rawY) {
        return PlayerInputEvent.swipe(
                timeNanos, sequence, adjustedX, adjustedY, rawX, rawY);
    }

    private static FixedStepInput input(PlayerInputEvent... events) {
        return new FixedStepInput(Arrays.asList(events));
    }

    private static boolean hasEvent(StepResult result, SimulationEvent.Type type) {
        for (SimulationEvent event : result.events) {
            if (event.type == type) {
                return true;
            }
        }
        return false;
    }
}
