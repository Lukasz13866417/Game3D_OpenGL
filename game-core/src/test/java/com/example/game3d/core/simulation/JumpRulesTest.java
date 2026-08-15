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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for jump-rule priority and gesture lifecycle boundaries.
 *
 * <p>These deliberately exercise combinations which are easy to miss in happy-path scenarios:
 * multiple gesture edges in one fixed tick, a held gesture spanning a landing jump, terminal state,
 * and charge values on both sides of the eligibility threshold.</p>
 */
public class JumpRulesTest {
    private final PhysicsConfig config = new PhysicsConfig();

    @Test
    public void defaultJumpChargeUsesTheFasterFillRate() {
        assertEquals(0.9 / 5.0, config.jumpChargeThreshold, 0.0);
        assertEquals(6.5, config.swipeChargePerScreenHeight, 0.0);
        assertEquals(0.06, config.maxJumpChargeXScreenHeights, 0.0);
        assertEquals(1.20, config.maxJumpChargeXToYRatio, 0.0);
        assertEquals("landing buffer look-ahead should be one sixth second at 120 Hz",
                PhysicsConfig.FIXED_HZ / 6, config.landingJumpBufferTicks);
        assertEquals(100_000_000L, config.jumpCooldownNanos);
    }

    @Test
    public void defaultJumpAndBounceTuningUsesRequestedHeightRatios() {
        assertEquals(PhysicsConfig.DEFAULT_JUMP_SPEED, config.jumpSpeed, 0.0);
        assertEquals(PhysicsConfig.DEFAULT_GRAVITY, config.gravity, 0.0);
        assertEquals(PhysicsConfig.DEFAULT_JUMP_FORWARD_BOOST_SPEED,
                config.jumpForwardBoostSpeed, 0.0);
        assertEquals(2.5625, config.jumpForwardBoostSpeed, 0.0);
        double oldJumpHeight = 20.5 * 20.5 / (2.0 * config.gravity);
        double newJumpHeight = config.jumpSpeed * config.jumpSpeed
                / (2.0 * config.gravity);
        assertEquals(PhysicsConfig.DEFAULT_JUMP_HEIGHT_MULTIPLIER,
                newJumpHeight / oldJumpHeight, 1.0e-12);

        assertEquals(PhysicsConfig.DEFAULT_BOUNCE_SPEED_THRESHOLD,
                config.bounceSpeedThreshold, 0.0);
        assertEquals(10.5, config.bounceSpeedThreshold, 0.0);
        assertEquals(PhysicsConfig.DEFAULT_BOUNCE_HEIGHT_MULTIPLIER,
                square(config.restitution) / square(0.60), 1.0e-12);
    }

    @Test
    public void releaseCannotTriggerChargeAddedByALaterGestureInTheSameTick() {
        SimulationEngine engine = groundedEngine(0);
        settle(engine);
        long now = engine.snapshot().timeNanos;

        StepResult result = engine.step(input(
                PlayerInputEvent.down(now, 1),
                PlayerInputEvent.up(now, 2),
                PlayerInputEvent.down(now, 3),
                PlayerInputEvent.swipe(now, 4, 0.0, -0.30)));

        assertFalse("the first gesture's release must not release the later gesture",
                hasEvent(result, SimulationEvent.Type.JUMP));
        assertTrue(result.snapshot.touchHeld);
    }

    @Test
    public void groundedPartialChargesDoNotAccumulateAcrossReleasedGestures() {
        SimulationEngine engine = groundedEngine(0);
        settle(engine);
        double partialSwipe = -0.5 * config.jumpChargeThreshold
                / config.swipeChargePerScreenHeight;

        StepResult first = performReleasedSwipe(engine, partialSwipe, 10);
        assertFalse(hasEvent(first, SimulationEvent.Type.JUMP));

        StepResult second = performReleasedSwipe(engine, partialSwipe, 20);
        assertFalse("two separately released, under-threshold gestures are not one gesture",
                hasEvent(second, SimulationEvent.Type.JUMP));
    }

    @Test
    public void cancelledChargedGestureNeverBecomesAReleaseJump() {
        SimulationEngine engine = groundedEngine(0);
        settle(engine);
        long now = engine.snapshot().timeNanos;

        StepResult cancelled = engine.step(input(
                PlayerInputEvent.down(now, 1),
                PlayerInputEvent.swipe(now, 2, 0.0, -0.30),
                PlayerInputEvent.cancel(now, 3)));

        assertFalse(hasEvent(cancelled, SimulationEvent.Type.JUMP));
        assertFalse(cancelled.snapshot.touchHeld);
        assertEquals(0.0, cancelled.snapshot.gestureCharge, 0.0);
        for (int i = 0; i < 30; i++) {
            assertFalse(hasEvent(
                    engine.step(FixedStepInput.EMPTY),
                    SimulationEvent.Type.JUMP));
        }
    }

    @Test
    public void heldGestureCannotRechargeAfterItsLandingJump() {
        TerrainWorld terrain = flatTerrain();
        SimulationEngine engine = new SimulationEngine(terrain, config,
                new Vec3(0.0, 2.0, 1.0), 0, StepObserver.NONE);
        engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, -0.30)));

        StepResult landingJump = runUntilEvent(engine, SimulationEvent.Type.JUMP, 120);
        assertNotNull("charged held touch should jump on landing", landingJump);
        assertEquals(JumpRuleId.LANDING_CHARGED, landingJump.jumpDecision.rule);
        assertTrue(landingJump.snapshot.touchHeld);

        long now = engine.snapshot().timeNanos;
        StepResult continuedMove = engine.step(input(
                PlayerInputEvent.swipe(now, 3, 0.0, -0.30)));

        assertEquals("one held gesture can produce at most one jump",
                0.0, continuedMove.snapshot.gestureCharge, 0.0);
    }

    @Test
    public void heldLandingArmExpiresWhenHeldChargeDecaysBelowThreshold() {
        SimulationEngine engine = new SimulationEngine(flatTerrain(), config,
                new Vec3(0.0, 0.95, 1.0), new Vec3(0.0, -1.0, 0.0),
                0, StepObserver.NONE);
        double initialCharge = config.jumpChargeThreshold + 0.02;
        double upwardSwipe = -initialCharge / config.swipeChargePerScreenHeight;
        StepResult armed = engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, upwardSwipe)));

        assertTrue(armed.snapshot.landingJumpArmed);
        assertFalse(hasEvent(armed, SimulationEvent.Type.JUMP));

        boolean expired = false;
        StepResult landed = null;
        for (int i = 0; i < 60; i++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            if (result.snapshot.gestureCharge < config.jumpChargeThreshold) {
                expired = true;
                assertFalse(result.snapshot.landingJumpArmed);
            }
            assertFalse(hasEvent(result, SimulationEvent.Type.JUMP));
            if (hasEvent(result, SimulationEvent.Type.LAND)) {
                landed = result;
                break;
            }
        }

        assertTrue("held charge did not expire before contact", expired);
        assertNotNull("fixture never reached resting support", landed);
        assertTrue(landed.snapshot.grounded);
    }

    @Test
    public void airborneReleaseWithoutChargeAndSafeLandingClearsImmediately() {
        SimulationEngine engine = new SimulationEngine(
                new TerrainWorld(Collections.emptyList()), config,
                new Vec3(0.0, 50.0, 0.0), new Vec3(0.0, -1.0, 0.0),
                0, StepObserver.NONE);
        StepResult released = engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, -0.30),
                PlayerInputEvent.up(0L, 3)));

        assertEquals(JumpRuleId.AIRBORNE_NO_CHARGE_REJECT,
                released.jumpDecision.rule);
        assertEquals(0.0, released.snapshot.gestureCharge, 0.0);
        assertFalse(released.snapshot.landingJumpArmed);
        assertFalse(hasEvent(released, SimulationEvent.Type.JUMP));
    }

    @Test
    public void thresholdChargeIsEligibleButChargeImmediatelyBelowItIsNot() {
        SimulationEngine atThreshold = groundedEngine(0);
        settle(atThreshold);
        double exactSwipe = -config.jumpChargeThreshold
                / config.swipeChargePerScreenHeight;
        StepResult exact = performReleasedSwipe(atThreshold, exactSwipe, 30);
        assertTrue(hasEvent(exact, SimulationEvent.Type.JUMP));
        assertEquals(JumpRuleId.GROUNDED_RELEASED, exact.jumpDecision.rule);

        SimulationEngine belowThreshold = groundedEngine(0);
        settle(belowThreshold);
        double insufficientSwipe = -(config.jumpChargeThreshold - 1.0e-6)
                / config.swipeChargePerScreenHeight;
        StepResult below = performReleasedSwipe(belowThreshold, insufficientSwipe, 40);
        assertFalse(hasEvent(below, SimulationEvent.Type.JUMP));
    }

    @Test
    public void groundedJumpAddsASmallBoostAlongFacingDirection() {
        SimulationEngine engine = groundedEngine(0);
        settle(engine);
        PlayerSnapshot before = engine.snapshot();
        double forwardSpeedBefore =
                before.velocity.withY(0.0).dot(before.heading);
        double exactSwipe = -config.jumpChargeThreshold
                / config.swipeChargePerScreenHeight;

        StepResult jump = performReleasedSwipe(engine, exactSwipe, 45);

        assertTrue(hasEvent(jump, SimulationEvent.Type.JUMP));
        double forwardSpeedAfter =
                jump.snapshot.velocity.withY(0.0).dot(jump.snapshot.heading);
        assertEquals(
                config.jumpForwardBoostSpeed,
                forwardSpeedAfter - forwardSpeedBefore,
                1.0e-9);
    }

    @Test
    public void safeLandingDefersJumpAndPreservesPersistentAirCharges() {
        SimulationEngine engine = new SimulationEngine(flatTerrain(), config,
                new Vec3(0.0, 1.0, 1.0), new Vec3(0.0, -1.0, 0.0),
                2, StepObserver.NONE);
        StepResult released = engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, -0.30),
                PlayerInputEvent.up(0L, 3)));

        assertFalse(hasEvent(released, SimulationEvent.Type.JUMP));
        assertEquals(JumpRuleId.AIRBORNE_SAFE_SUPPORT_DEFER,
                released.jumpDecision.rule);
        assertTrue(released.snapshot.landingJumpArmed);
        assertTrue(hasEvent(released, SimulationEvent.Type.LANDING_JUMP_ARMED));

        StepResult landingJump = runUntilEvent(engine, SimulationEvent.Type.JUMP, 120);
        assertNotNull(landingJump);
        assertEquals(JumpRuleId.LANDING_CHARGED, landingJump.jumpDecision.rule);
        assertEquals("landing jumps do not consume persistent air charges",
                2, landingJump.snapshot.airJumpCharges);
    }

    @Test
    public void fallingNearSafeSupportBuffersWithoutPersistentCharge() {
        SimulationEngine engine = new SimulationEngine(flatTerrain(), config,
                new Vec3(0.0, 1.0, 1.0), new Vec3(0.0, -1.0, 0.0),
                0, StepObserver.NONE);

        StepResult released = engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, -0.30),
                PlayerInputEvent.up(0L, 3)));

        assertFalse(hasEvent(released, SimulationEvent.Type.JUMP));
        assertEquals(JumpRuleId.AIRBORNE_NO_CHARGE_DEFER,
                released.jumpDecision.rule);
        assertEquals(0, released.snapshot.airJumpCharges);
        assertTrue(released.snapshot.gestureCharge >= config.jumpChargeThreshold);
        assertTrue(released.snapshot.landingJumpArmed);
    }

    @Test
    public void safeGapForecastAndChargeUseAreInvariantUnderVerticalTranslation() {
        StepResult lowered = releasedAirborneRequestAcrossShortGap(-100.0);
        StepResult baseline = releasedAirborneRequestAcrossShortGap(0.0);
        StepResult elevated = releasedAirborneRequestAcrossShortGap(100.0);

        assertEquivalentDeferredRecovery(baseline, lowered);
        assertEquivalentDeferredRecovery(baseline, elevated);
    }

    @Test
    public void unrecoverableAirJumpConsumesExactlyOneChargeAndOneGesture() {
        SimulationEngine engine = new SimulationEngine(
                new TerrainWorld(Collections.emptyList()), config,
                new Vec3(0.0, 2.0, 0.0), new Vec3(0.0, -0.1, 0.0),
                2, StepObserver.NONE);
        StepResult jump = engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, -0.30),
                PlayerInputEvent.up(0L, 3)));

        assertTrue(hasEvent(jump, SimulationEvent.Type.JUMP));
        assertEquals(JumpRuleId.AIRBORNE_UNRECOVERABLE, jump.jumpDecision.rule);
        assertEquals(1, jump.snapshot.airJumpCharges);

        int laterJumps = 0;
        for (int i = 0; i < 80; i++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            if (hasEvent(result, SimulationEvent.Type.JUMP)) {
                laterJumps++;
            }
        }
        assertEquals("a consumed request must not fire again", 0, laterJumps);
        assertEquals(1, engine.snapshot().airJumpCharges);
    }

    @Test
    public void persistentAirJumpLaunchesTowardCurrentFacing() {
        double initialHorizontalSpeed = 12.0;
        SimulationEngine engine = new SimulationEngine(
                new TerrainWorld(Collections.emptyList()), config,
                new Vec3(0.0, 4.0, 0.0),
                new Vec3(0.0, 0.0, -initialHorizontalSpeed),
                1, StepObserver.NONE);
        double quarterTurnSwipe =
                (Math.PI * 0.5) / config.facingRadiansPerScreenHeight;
        StepResult turned = engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(
                        0L, 2, quarterTurnSwipe, 0.0)));
        assertHorizontalVelocityDiffersFromFacing(turned.snapshot);
        long now = turned.snapshot.timeNanos;
        engine.step(input(PlayerInputEvent.up(now, 3)));
        now = engine.snapshot().timeNanos;

        StepResult jump = engine.step(input(
                PlayerInputEvent.down(now, 4),
                PlayerInputEvent.swipe(now, 5, 0.0, -0.30),
                PlayerInputEvent.up(now, 6)));

        assertTrue(hasEvent(jump, SimulationEvent.Type.JUMP));
        assertEquals(
                JumpRuleId.AIRBORNE_UNRECOVERABLE,
                jump.jumpDecision.rule);
        assertEquals(0, jump.snapshot.airJumpCharges);
        assertHorizontalVelocityFollowsFacing(
                jump.snapshot,
                initialHorizontalSpeed + config.jumpForwardBoostSpeed);
    }

    @Test
    public void deferredLandingJumpLaunchesTowardCurrentFacing() {
        double initialHorizontalSpeed = 12.0;
        SimulationEngine engine = new SimulationEngine(
                new TrackBuilder(30.0).straight(200.0).build(),
                config,
                new Vec3(0.0, 1.0, 1.0),
                new Vec3(0.0, 0.0, -initialHorizontalSpeed),
                0, StepObserver.NONE);
        double quarterTurnSwipe =
                (Math.PI * 0.5) / config.facingRadiansPerScreenHeight;
        StepResult turned = engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(
                        0L, 2, quarterTurnSwipe, 0.0)));
        assertHorizontalVelocityDiffersFromFacing(turned.snapshot);
        long now = turned.snapshot.timeNanos;
        engine.step(input(PlayerInputEvent.up(now, 3)));
        now = engine.snapshot().timeNanos;
        StepResult released = engine.step(input(
                PlayerInputEvent.down(now, 4),
                PlayerInputEvent.swipe(now, 5, 0.0, -0.30),
                PlayerInputEvent.up(now, 6)));
        assertEquals(
                JumpRuleId.AIRBORNE_NO_CHARGE_DEFER,
                released.jumpDecision.rule);

        StepResult landingJump = runUntilEvent(
                engine, SimulationEvent.Type.JUMP, 120);

        assertNotNull(landingJump);
        assertEquals(
                JumpRuleId.LANDING_CHARGED,
                landingJump.jumpDecision.rule);
        assertHorizontalVelocityFollowsFacing(
                landingJump.snapshot,
                initialHorizontalSpeed + config.jumpForwardBoostSpeed);
    }

    @Test
    public void risingReleaseUsesAirChargeInsteadOfArmingLandingJump() {
        SimulationEngine engine = new SimulationEngine(flatTerrain(), config,
                new Vec3(0.0, 0.8, 1.0), new Vec3(0.0, 5.0, 0.0),
                1, StepObserver.NONE);

        StepResult released = engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, -0.30),
                PlayerInputEvent.up(0L, 3)));

        assertEquals(JumpRuleId.AIRBORNE_RELEASED, released.jumpDecision.rule);
        assertTrue(hasEvent(released, SimulationEvent.Type.JUMP));
        assertFalse(hasEvent(released, SimulationEvent.Type.LANDING_JUMP_ARMED));
        assertFalse(released.snapshot.landingJumpArmed);
        assertEquals(0, released.snapshot.airJumpCharges);
    }

    @Test
    public void risingReleaseNearSafeSupportStillUsesAirChargeImmediately() {
        SimulationEngine engine = new SimulationEngine(flatTerrain(), config,
                new Vec3(0.0, 0.5, 1.0), new Vec3(0.0, 2.0, 0.0),
                1, StepObserver.NONE);

        StepResult released = engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, -0.30),
                PlayerInputEvent.up(0L, 3)));

        assertTrue("fixture must still be rising when the request is evaluated",
                released.snapshot.velocity.y > 0.0);
        assertEquals(JumpRuleId.AIRBORNE_RELEASED,
                released.jumpDecision.rule);
        assertTrue(hasEvent(released, SimulationEvent.Type.JUMP));
        assertFalse(hasEvent(released, SimulationEvent.Type.LANDING_JUMP_ARMED));
        assertFalse(released.snapshot.landingJumpArmed);
        assertEquals(0, released.snapshot.airJumpCharges);
    }

    @Test
    public void secondReleasedJumpWaitsForOneHundredMillisecondCooldown() {
        SimulationEngine engine = groundedEngine(1);
        settle(engine);
        StepResult firstJump = performReleasedSwipe(engine, -0.30, 100);
        SimulationEvent firstEvent = onlyEvent(firstJump, SimulationEvent.Type.JUMP);
        assertEquals(JumpRuleId.GROUNDED_RELEASED, firstJump.jumpDecision.rule);
        assertTrue(firstJump.snapshot.jumpCooldownRemainingNanos > 0L);

        StepResult buffered = performReleasedSwipe(engine, -0.30, 200);
        assertEquals(JumpRuleId.JUMP_COOLDOWN_DEFER, buffered.jumpDecision.rule);
        assertFalse(hasEvent(buffered, SimulationEvent.Type.JUMP));
        assertTrue(buffered.snapshot.gestureCharge >= config.jumpChargeThreshold);

        StepResult secondJump = runUntilEvent(engine, SimulationEvent.Type.JUMP, 20);
        assertNotNull(secondJump);
        SimulationEvent secondEvent = onlyEvent(secondJump, SimulationEvent.Type.JUMP);
        long elapsed = secondEvent.timeNanos - firstEvent.timeNanos;
        assertTrue("cooldown allowed the second jump too early",
                elapsed >= config.jumpCooldownNanos);
        assertTrue("fixed-step cooldown overshot by more than one tick",
                elapsed < config.jumpCooldownNanos + PhysicsConfig.FIXED_DT_NANOS);
        assertEquals(JumpRuleId.AIRBORNE_RELEASED, secondJump.jumpDecision.rule);
        assertEquals(0, secondJump.snapshot.airJumpCharges);
    }

    @Test
    public void safeSupportBeyondLandingBufferDoesNotArmLandingJump() {
        SimulationEngine engine = new SimulationEngine(flatTerrain(), config,
                new Vec3(0.0, 5.0, 1.0), new Vec3(0.0, -1.0, 0.0),
                1, StepObserver.NONE);

        StepResult released = engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, -0.30),
                PlayerInputEvent.up(0L, 3)));

        assertEquals(JumpRuleId.AIRBORNE_UNRECOVERABLE,
                released.jumpDecision.rule);
        assertTrue(hasEvent(released, SimulationEvent.Type.JUMP));
        assertFalse(hasEvent(released, SimulationEvent.Type.LANDING_JUMP_ARMED));
        assertFalse(released.snapshot.landingJumpArmed);
        assertEquals(0, released.snapshot.airJumpCharges);
    }

    @Test
    public void heldDownwardSwipeAbsorbsHardGroundImpact() {
        SimulationEngine engine = hardDropEngine();
        StepResult first = engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, 0.12)));
        assertTrue(first.snapshot.touchHeld);
        assertTrue(first.snapshot.impactBrakeArmed);

        StepResult impact = runUntilEvent(
                engine, SimulationEvent.Type.BOUNCE_SUPPRESSED, 60);

        assertNotNull("held downward swipe never reached a braked impact", impact);
        assertTrue(hasEvent(impact, SimulationEvent.Type.LAND));
        assertFalse(hasEvent(impact, SimulationEvent.Type.BOUNCE));
        assertFalse(hasEvent(impact, SimulationEvent.Type.JUMP));
        assertTrue(impact.snapshot.grounded);
        assertTrue("touch must still be held at the absorbed impact",
                impact.snapshot.touchHeld);
        assertFalse(impact.snapshot.impactBrakeArmed);
        assertEquals(0.0, impact.snapshot.velocity.y, 1.0e-9);

        StepResult recharged = engine.step(input(
                PlayerInputEvent.swipe(0L, 3, 0.0, -0.12)));
        assertTrue("same held touch did not begin charging after the braked landing",
                recharged.snapshot.gestureCharge >= config.jumpChargeThreshold);
        assertTrue(recharged.snapshot.touchHeld);
        assertTrue(recharged.snapshot.grounded);

        StepResult jumped = engine.step(input(PlayerInputEvent.up(0L, 4)));
        assertEquals(JumpRuleId.GROUNDED_RELEASED, jumped.jumpDecision.rule);
        assertTrue(hasEvent(jumped, SimulationEvent.Type.JUMP));
        assertFalse(jumped.snapshot.touchHeld);
    }

    @Test
    public void releasingHeldTouchAfterImpactBrakeWithoutRechargingDoesNotJump() {
        SimulationEngine engine = hardDropEngine();
        engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, 0.12)));
        StepResult impact = runUntilEvent(
                engine, SimulationEvent.Type.BOUNCE_SUPPRESSED, 60);
        assertNotNull(impact);

        StepResult released = engine.step(input(PlayerInputEvent.up(0L, 3)));
        assertFalse(hasEvent(released, SimulationEvent.Type.JUMP));
        assertEquals(0.0, released.snapshot.gestureCharge, 0.0);
        assertFalse(released.snapshot.touchHeld);
    }

    @Test
    public void releasingDownwardSwipeBeforeImpactRestoresBounce() {
        SimulationEngine engine = hardDropEngine();
        StepResult released = engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, 0.12),
                PlayerInputEvent.up(0L, 3)));
        assertFalse(released.snapshot.touchHeld);
        assertFalse(released.snapshot.impactBrakeArmed);

        StepResult impact = runUntilEvent(engine, SimulationEvent.Type.BOUNCE, 60);

        assertNotNull("released downward swipe unexpectedly suppressed bounce", impact);
        assertFalse(hasEvent(impact, SimulationEvent.Type.BOUNCE_SUPPRESSED));
        assertTrue(impact.snapshot.velocity.y > 0.0);
    }

    @Test
    public void downwardSwipeWhileRisingDoesNotArmFutureImpactBrake() {
        SimulationEngine engine = new SimulationEngine(flatTerrain(), config,
                new Vec3(0.0, 3.0, 1.0), new Vec3(0.0, 10.0, 0.0),
                0, StepObserver.NONE);
        StepResult swiped = engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, 0.12)));
        assertFalse(swiped.snapshot.impactBrakeArmed);

        StepResult impact = runUntilEvent(engine, SimulationEvent.Type.BOUNCE, 180);

        assertNotNull("unarmed rising swipe did not produce the control bounce", impact);
        assertFalse(hasEvent(impact, SimulationEvent.Type.BOUNCE_SUPPRESSED));
    }

    @Test
    public void terminalPlayerCannotJumpWhenItLaterContactsSupport() {
        TerrainWorld terrain = new TrackBuilder(8.0)
                .straight(1.0)
                .spike(0.0, 0.0, 0.50, 2.0)
                .straight(100.0)
                .build();
        SimulationEngine engine = new SimulationEngine(terrain, config,
                new Vec3(0.0, 1.0, 1.0), 0, StepObserver.NONE);
        StepResult lethal = engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, -0.30)));
        assertTrue(hasEvent(lethal, SimulationEvent.Type.PLAYER_DIED));
        assertTrue(lethal.snapshot.dead);

        for (int i = 0; i < 120; i++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            assertFalse("TERMINAL_REJECT must outrank the landing jump rule",
                    hasEvent(result, SimulationEvent.Type.JUMP));
        }
    }

    @Test
    public void landingJumpWinsOverHardImpactBounce() {
        SimulationEngine engine = new SimulationEngine(flatTerrain(), config,
                new Vec3(0.0, 1.0, 1.0), new Vec3(0.0, -30.0, 0.0),
                0, StepObserver.NONE);
        engine.step(input(
                PlayerInputEvent.down(0L, 1),
                PlayerInputEvent.swipe(0L, 2, 0.0, -0.30)));

        StepResult jump = runUntilEvent(engine, SimulationEvent.Type.JUMP, 30);
        assertNotNull(jump);
        assertEquals(JumpRuleId.LANDING_CHARGED, jump.jumpDecision.rule);
        assertFalse(hasEvent(jump, SimulationEvent.Type.BOUNCE));
        assertTrue(jump.snapshot.velocity.y > 0.0);
    }

    private SimulationEngine groundedEngine(int charges) {
        return new SimulationEngine(flatTerrain(), config,
                new Vec3(0.0, config.cylinderRadius + 0.002, 1.0),
                charges, StepObserver.NONE);
    }

    private TerrainWorld flatTerrain() {
        return new TrackBuilder(8.0).straight(200.0).build();
    }

    private SimulationEngine hardDropEngine() {
        return new SimulationEngine(flatTerrain(), config,
                new Vec3(0.0, 3.0, 1.0), new Vec3(0.0, -30.0, 0.0),
                0, StepObserver.NONE);
    }

    private StepResult releasedAirborneRequestAcrossShortGap(double surfaceY) {
        TerrainWorld terrain = new TrackBuilder(8.0)
                .lift(surfaceY)
                .straight(12.0)
                .gap(1.0)
                .straight(200.0)
                .build();
        SimulationEngine engine = new SimulationEngine(terrain, config,
                new Vec3(0.0, surfaceY + config.cylinderRadius + 0.002, 1.0),
                1, StepObserver.NONE);

        boolean establishedSupport = false;
        for (int tick = 0; tick < 240; tick++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            establishedSupport = establishedSupport || result.snapshot.grounded;
            if (establishedSupport && !result.snapshot.grounded) {
                long now = result.snapshot.timeNanos;
                return engine.step(input(
                        PlayerInputEvent.down(now, 100),
                        PlayerInputEvent.swipe(now, 101, 0.0, -0.30),
                        PlayerInputEvent.up(now, 102)));
            }
        }
        throw new AssertionError("player never left the initial support");
    }

    private static void assertEquivalentDeferredRecovery(
            StepResult expected, StepResult actual) {
        assertEquals(JumpRuleId.AIRBORNE_SAFE_SUPPORT_DEFER,
                expected.jumpDecision.rule);
        assertEquals(expected.jumpDecision.rule, actual.jumpDecision.rule);
        assertEquals(expected.jumpDecision.action, actual.jumpDecision.action);
        assertEquals(expected.jumpDecision.consumesAirCharge,
                actual.jumpDecision.consumesAirCharge);
        assertFalse(hasEvent(actual, SimulationEvent.Type.JUMP));
        assertEquals(expected.snapshot.airJumpCharges,
                actual.snapshot.airJumpCharges);
        assertEquals(1, actual.snapshot.airJumpCharges);
    }

    private static void settle(SimulationEngine engine) {
        for (int i = 0; i < 5; i++) {
            engine.step(FixedStepInput.EMPTY);
        }
        assertTrue(engine.snapshot().grounded);
    }

    private static StepResult performReleasedSwipe(
            SimulationEngine engine, double deltaY, long sequenceBase) {
        long now = engine.snapshot().timeNanos;
        return engine.step(input(
                PlayerInputEvent.down(now, sequenceBase),
                PlayerInputEvent.swipe(now, sequenceBase + 1, 0.0, deltaY),
                PlayerInputEvent.up(now, sequenceBase + 2)));
    }

    private static StepResult runUntilEvent(
            SimulationEngine engine, SimulationEvent.Type type, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            if (hasEvent(result, type)) {
                return result;
            }
        }
        return null;
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

    private static SimulationEvent onlyEvent(
            StepResult result, SimulationEvent.Type type) {
        SimulationEvent found = null;
        for (SimulationEvent event : result.events) {
            if (event.type != type) {
                continue;
            }
            assertTrue("multiple " + type + " events in one fixed tick", found == null);
            found = event;
        }
        assertNotNull("missing " + type + " event", found);
        return found;
    }

    private static void assertHorizontalVelocityFollowsFacing(
            PlayerSnapshot snapshot, double expectedSpeed) {
        Vec3 horizontalVelocity = snapshot.velocity.withY(0.0);
        assertEquals(expectedSpeed, horizontalVelocity.length(), 1.0e-9);
        Vec3 launchDirection = horizontalVelocity.normalized();
        assertEquals(snapshot.heading.x, launchDirection.x, 1.0e-9);
        assertEquals(snapshot.heading.z, launchDirection.z, 1.0e-9);
    }

    private static void assertHorizontalVelocityDiffersFromFacing(
            PlayerSnapshot snapshot) {
        Vec3 movementDirection =
                snapshot.velocity.withY(0.0).normalized();
        assertEquals(0.0, movementDirection.dot(snapshot.heading), 1.0e-9);
    }

    private static double square(double value) {
        return value * value;
    }
}
