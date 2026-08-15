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

/** Regression coverage for per-movement jump charging and held-charge decay. */
public class JumpGestureClassificationTest {
    private static final double EPSILON = 1.0e-9;

    private final PhysicsConfig config = new PhysicsConfig();

    @Test
    public void defaultVerticalRatioAndDecayDurationAreStable() {
        assertEquals(1.20, config.maxJumpChargeXToYRatio, 0.0);
        assertEquals(100_000_000L, config.heldGestureChargeGraceNanos);
        assertEquals(400_000_000L, config.heldGestureChargeDecayNanos);
    }

    @Test
    public void perMovementVerticalBoundarySeparatesAcceptedAndBlockedCharge() {
        double rawUpward = 0.040;
        double ratioBoundary = config.maxJumpChargeXToYRatio * rawUpward;
        SimulationEngine accepted = groundedEngine();
        SimulationEngine blocked = groundedEngine();
        long acceptedNow = accepted.snapshot().timeNanos;
        long blockedNow = blocked.snapshot().timeNanos;

        StepResult acceptedSwipe = accepted.step(input(
                PlayerInputEvent.down(acceptedNow, 1L),
                swipe(acceptedNow, 2L,
                        0.0, -rawUpward,
                        ratioBoundary - 1.0e-6, -rawUpward)));
        StepResult blockedSwipe = blocked.step(input(
                PlayerInputEvent.down(blockedNow, 1L),
                swipe(blockedNow, 2L,
                        0.0, -rawUpward,
                        ratioBoundary + 1.0e-6, -rawUpward)));

        double potential = rawUpward * config.swipeChargePerScreenHeight;
        assertEquals(potential, acceptedSwipe.snapshot.gestureCharge, EPSILON);
        assertEquals(potential, acceptedSwipe.snapshot.gestureChargePotential, EPSILON);
        assertTrue(acceptedSwipe.snapshot.jumpChargePathEligible);
        assertEquals(0.0, blockedSwipe.snapshot.gestureCharge, 0.0);
        assertEquals(0.0, blockedSwipe.snapshot.gestureChargePotential, 0.0);
        assertFalse(blockedSwipe.snapshot.jumpChargePathEligible);

        StepResult acceptedRelease = accepted.step(input(
                PlayerInputEvent.up(accepted.snapshot().timeNanos, 3L)));
        StepResult blockedRelease = blocked.step(input(
                PlayerInputEvent.up(blocked.snapshot().timeNanos, 3L)));
        assertTrue(hasEvent(acceptedRelease, SimulationEvent.Type.JUMP));
        assertFalse(hasEvent(blockedRelease, SimulationEvent.Type.JUMP));
    }

    @Test
    public void absoluteHorizontalDistanceDoesNotRejectAVerticalEnoughMovement() {
        SimulationEngine engine = groundedEngine();
        long now = engine.snapshot().timeNanos;

        StepResult charged = engine.step(input(
                PlayerInputEvent.down(now, 1L),
                swipe(now, 2L, 0.0, -0.100, 0.070, -0.100)));

        assertTrue(0.070 > config.maxJumpChargeXScreenHeights);
        assertEquals(0.100 * config.swipeChargePerScreenHeight,
                charged.snapshot.gestureCharge, EPSILON);
        assertTrue(charged.snapshot.jumpChargePathEligible);
    }

    @Test
    public void rawVerticalMovementWithNoScaledUpwardMotionContributesNothing() {
        SimulationEngine engine = groundedEngine();
        long now = engine.snapshot().timeNanos;

        StepResult result = engine.step(input(
                PlayerInputEvent.down(now, 1L),
                swipe(now, 2L, 0.0, 0.0, 0.0, -0.050)));

        assertEquals(0.0, result.snapshot.gestureCharge, 0.0);
        assertFalse(result.snapshot.jumpChargePathEligible);
    }

    @Test
    public void sameDirectionPacketizationProducesTheSameAuthoritativeState() {
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
        assertEquals(expected.gestureChargePotential,
                actual.gestureChargePotential, EPSILON);
        assertEquals(expected.yawRadians, actual.yawRadians, EPSILON);
        assertEquals(expected.stateHash, actual.stateHash);
    }

    @Test
    public void everyAcceptedUpwardMovementContributesAcrossHorizontalSteering() {
        SimulationEngine engine = groundedEngine();
        long now = engine.snapshot().timeNanos;

        StepResult charged = engine.step(input(
                PlayerInputEvent.down(now, 1L),
                swipe(now, 2L, 0.0, -0.020, 0.0, -0.020),
                swipe(now, 3L, 0.080, 0.0, 0.080, 0.0),
                swipe(now, 4L, 0.0, -0.020, 0.0, -0.020)));

        assertEquals(0.040 * config.swipeChargePerScreenHeight,
                charged.snapshot.gestureCharge, EPSILON);
        assertEquals(0.040, charged.snapshot.gestureRawUpwardDistance, EPSILON);
        assertEquals(0.080 * config.facingRadiansPerScreenHeight,
                charged.snapshot.yawRadians, EPSILON);

        StepResult release = engine.step(input(
                PlayerInputEvent.up(engine.snapshot().timeNanos, 5L)));
        assertTrue(hasEvent(release, SimulationEvent.Type.JUMP));
    }

    @Test
    public void earlierHorizontalMovementCannotPoisonLaterVerticalMovement() {
        SimulationEngine engine = groundedEngine();
        long now = engine.snapshot().timeNanos;
        engine.step(input(
                PlayerInputEvent.down(now, 1L),
                swipe(now, 2L, 0.090, 0.0, 0.090, 0.0)));

        now = engine.snapshot().timeNanos;
        StepResult charged = engine.step(input(
                swipe(now, 3L, 0.0, -0.050, 0.0, -0.050)));

        assertEquals(0.050 * config.swipeChargePerScreenHeight,
                charged.snapshot.gestureCharge, EPSILON);
        assertTrue(charged.snapshot.jumpChargePathEligible);

        StepResult release = engine.step(input(
                PlayerInputEvent.up(engine.snapshot().timeNanos, 4L)));
        assertTrue(hasEvent(release, SimulationEvent.Type.JUMP));
        assertEquals(JumpRuleId.GROUNDED_RELEASED, release.jumpDecision.rule);
    }

    @Test
    public void blockedUpwardMovementDoesNotPoisonLaterAcceptedContribution() {
        SimulationEngine engine = groundedEngine();
        long now = engine.snapshot().timeNanos;
        StepResult charged = engine.step(input(
                PlayerInputEvent.down(now, 1L),
                swipe(now, 2L, 0.0, -0.010, 0.020, -0.010),
                swipe(now, 3L, 0.0, -0.040, 0.0, -0.040)));

        assertEquals(0.040 * config.swipeChargePerScreenHeight,
                charged.snapshot.gestureCharge, EPSILON);
        assertEquals(0.040 * config.swipeChargePerScreenHeight,
                charged.snapshot.gestureChargePotential, EPSILON);
        assertTrue(charged.snapshot.jumpChargePathEligible);

        StepResult release = engine.step(input(
                PlayerInputEvent.up(engine.snapshot().timeNanos, 4L)));
        assertTrue(hasEvent(release, SimulationEvent.Type.JUMP));
    }

    @Test
    public void horizontalMovementDoesNotInstantlyEraseChargeButTimeStillDecaysIt() {
        SimulationEngine engine = groundedEngine();
        long now = engine.snapshot().timeNanos;
        StepResult initial = engine.step(input(
                PlayerInputEvent.down(now, 1L),
                swipe(now, 2L, 0.0, -0.050, 0.0, -0.050)));
        double yawBefore = initial.snapshot.yawRadians;
        now = engine.snapshot().timeNanos;
        StepResult steered = engine.step(input(
                swipe(now, 3L, 0.061, 0.0, 0.061, 0.0)));

        assertEquals(initial.snapshot.gestureCharge,
                steered.snapshot.gestureCharge, EPSILON);
        assertNotEquals(yawBefore, steered.snapshot.yawRadians, EPSILON);

        StepResult release = engine.step(input(
                PlayerInputEvent.up(engine.snapshot().timeNanos, 4L)));
        assertTrue(hasEvent(release, SimulationEvent.Type.JUMP));
    }

    @Test
    public void heldChargeDecaysBelowThresholdWithoutFurtherUpwardInput() {
        SimulationEngine engine = groundedEngine();
        long now = engine.snapshot().timeNanos;
        StepResult initial = engine.step(input(
                PlayerInputEvent.down(now, 1L),
                swipe(now, 2L, 0.0, -0.050, 0.0, -0.050)));
        assertTrue(initial.snapshot.gestureCharge >= config.jumpChargeThreshold);

        double previous = initial.snapshot.gestureCharge;
        long lastContribution = initial.snapshot.timeNanos
                - PhysicsConfig.FIXED_DT_NANOS;
        while (engine.snapshot().timeNanos
                <= lastContribution + config.heldGestureChargeGraceNanos) {
            StepResult held = engine.step(FixedStepInput.EMPTY);
            assertEquals(initial.snapshot.gestureCharge,
                    held.snapshot.gestureCharge, EPSILON);
            previous = held.snapshot.gestureCharge;
        }
        long firstDecayBoundary = engine.snapshot().timeNanos;
        long firstDecayNanos = firstDecayBoundary - Math.max(
                firstDecayBoundary - PhysicsConfig.FIXED_DT_NANOS,
                lastContribution + config.heldGestureChargeGraceNanos);
        StepResult firstDecayed = engine.step(FixedStepInput.EMPTY);
        double expectedFirstDecay = initial.snapshot.gestureCharge
                - (double) firstDecayNanos
                / (double) config.heldGestureChargeDecayNanos;
        assertEquals(expectedFirstDecay,
                firstDecayed.snapshot.gestureCharge, EPSILON);
        previous = firstDecayed.snapshot.gestureCharge;
        while (previous + EPSILON >= config.jumpChargeThreshold) {
            StepResult decayed = engine.step(FixedStepInput.EMPTY);
            assertTrue(decayed.snapshot.gestureCharge <= previous + EPSILON);
            previous = decayed.snapshot.gestureCharge;
        }
        assertTrue(previous < config.jumpChargeThreshold);

        StepResult release = engine.step(input(
                PlayerInputEvent.up(engine.snapshot().timeNanos, 3L)));
        assertFalse(hasEvent(release, SimulationEvent.Type.JUMP));
    }

    @Test
    public void nonContributingMovementsDoNotRefreshHeldChargeGrace() {
        SimulationEngine horizontal = groundedEngine();
        SimulationEngine tooDiagonal = groundedEngine();
        long horizontalNow = horizontal.snapshot().timeNanos;
        long diagonalNow = tooDiagonal.snapshot().timeNanos;
        horizontal.step(input(
                PlayerInputEvent.down(horizontalNow, 1L),
                swipe(horizontalNow, 2L, 0.0, -0.050, 0.0, -0.050)));
        tooDiagonal.step(input(
                PlayerInputEvent.down(diagonalNow, 1L),
                swipe(diagonalNow, 2L, 0.0, -0.050, 0.0, -0.050)));

        for (int i = 0; i < 24; i++) {
            horizontalNow = horizontal.snapshot().timeNanos;
            horizontal.step(input(swipe(
                    horizontalNow, 10L + i,
                    0.001, 0.0, 0.001, 0.0)));
            diagonalNow = tooDiagonal.snapshot().timeNanos;
            tooDiagonal.step(input(swipe(
                    diagonalNow, 10L + i,
                    0.002, -0.001, 0.002, -0.001)));
        }

        assertTrue(horizontal.snapshot().gestureCharge < config.jumpChargeThreshold);
        assertTrue(tooDiagonal.snapshot().gestureCharge < config.jumpChargeThreshold);
        assertFalse(tooDiagonal.snapshot().jumpChargePathEligible);
    }

    @Test
    public void laterAcceptedMovementReplenishesTheDecayingBar() {
        SimulationEngine engine = groundedEngine();
        long now = engine.snapshot().timeNanos;
        StepResult first = engine.step(input(
                PlayerInputEvent.down(now, 1L),
                swipe(now, 2L, 0.0, -0.030, 0.0, -0.030)));

        now = engine.snapshot().timeNanos;
        StepResult second = engine.step(input(
                swipe(now, 3L, 0.0, -0.030, 0.0, -0.030)));

        double expected = first.snapshot.gestureCharge
                + 0.030 * config.swipeChargePerScreenHeight;
        assertEquals(expected, second.snapshot.gestureCharge, EPSILON);

        StepResult release = engine.step(input(
                PlayerInputEvent.up(engine.snapshot().timeNanos, 4L)));
        assertTrue(hasEvent(release, SimulationEvent.Type.JUMP));
    }

    @Test
    public void acceptedMovementAfterDecayReplenishesAndRestartsGrace() {
        SimulationEngine engine = groundedEngine();
        long now = engine.snapshot().timeNanos;
        StepResult initial = engine.step(input(
                PlayerInputEvent.down(now, 1L),
                swipe(now, 2L, 0.0, -0.050, 0.0, -0.050)));
        for (int i = 0; i < 18; i++) {
            engine.step(FixedStepInput.EMPTY);
        }
        double beforeRecharge = engine.snapshot().gestureCharge;
        assertTrue(beforeRecharge < initial.snapshot.gestureCharge);

        now = engine.snapshot().timeNanos;
        StepResult recharged = engine.step(input(
                swipe(now, 3L, 0.0, -0.020, 0.0, -0.020)));
        assertTrue(recharged.snapshot.gestureCharge > beforeRecharge);

        for (int i = 0; i < 12; i++) {
            StepResult held = engine.step(FixedStepInput.EMPTY);
            assertEquals(recharged.snapshot.gestureCharge,
                    held.snapshot.gestureCharge, EPSILON);
        }
        StepResult decayedAgain = engine.step(FixedStepInput.EMPTY);
        assertTrue(decayedAgain.snapshot.gestureCharge
                < recharged.snapshot.gestureCharge);
    }

    @Test
    public void downwardSwipeStillCancelsChargeImmediately() {
        SimulationEngine engine = groundedEngine();
        long now = engine.snapshot().timeNanos;
        engine.step(input(
                PlayerInputEvent.down(now, 1L),
                swipe(now, 2L, 0.0, -0.050, 0.0, -0.050)));

        now = engine.snapshot().timeNanos;
        StepResult cancelled = engine.step(input(
                swipe(now, 3L, 0.0, 0.100, 0.0, 0.100)));
        assertEquals(0.0, cancelled.snapshot.gestureCharge, 0.0);

        StepResult release = engine.step(input(
                PlayerInputEvent.up(engine.snapshot().timeNanos, 4L)));
        assertFalse(hasEvent(release, SimulationEvent.Type.JUMP));
    }

    @Test
    public void blockedChargeSwipeStillChangesFacing() {
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
    public void horizontalThenUpwardLegsAreIndependentOfSameTickPacketization() {
        SimulationEngine coalesced = groundedEngine();
        SimulationEngine packetized = groundedEngine();
        long coalescedNow = coalesced.snapshot().timeNanos;
        long packetizedNow = packetized.snapshot().timeNanos;

        coalesced.step(input(
                PlayerInputEvent.down(coalescedNow, 1L),
                swipe(coalescedNow, 2L, 0.090, 0.0, 0.090, 0.0),
                swipe(coalescedNow, 3L, 0.0, -0.050, 0.0, -0.050)));

        List<PlayerInputEvent> packets = new ArrayList<PlayerInputEvent>();
        packets.add(PlayerInputEvent.down(packetizedNow, 1L));
        for (int i = 0; i < 9; i++) {
            packets.add(swipe(packetizedNow, 2L + i,
                    0.010, 0.0, 0.010, 0.0));
        }
        for (int i = 0; i < 5; i++) {
            packets.add(swipe(packetizedNow, 11L + i,
                    0.0, -0.010, 0.0, -0.010));
        }
        packetized.step(new FixedStepInput(packets));

        PlayerSnapshot expected = coalesced.snapshot();
        PlayerSnapshot actual = packetized.snapshot();
        assertEquals(expected.gestureCharge, actual.gestureCharge, EPSILON);
        assertEquals(expected.gestureChargePotential,
                actual.gestureChargePotential, EPSILON);
        assertEquals(expected.gestureRawDeltaX, actual.gestureRawDeltaX, EPSILON);
        assertEquals(expected.gestureRawUpwardDistance,
                actual.gestureRawUpwardDistance, EPSILON);
        assertEquals(expected.yawRadians, actual.yawRadians, EPSILON);
        assertEquals(expected.stateHash, actual.stateHash);
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
