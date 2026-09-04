package com.example.game3d_opengl.game.player.player_character;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies exact physics-phase interpolation and the uncapped presentation boundary.
 */
public class PlayerSpinInterpolationTest {
    @Test
    public void interpolationPreservesMoreThanHalfTurnInOneTick() {
        double previous = 0.25;
        double exactDelta = -Math.PI * 1.5;

        double halfway = Player.interpolateAxleRadians(
                previous, exactDelta, 0.5);
        double endpoint = Player.interpolateAxleRadians(
                previous, exactDelta, 1.0);

        assertEquals(Math.IEEEremainder(
                previous + exactDelta * 0.5, Math.PI * 2.0),
                halfway, 0.0);
        assertEquals(Math.IEEEremainder(
                previous + exactDelta, Math.PI * 2.0),
                endpoint, 0.0);
        assertTrue("shortest-path interpolation would rotate the opposite way",
                halfway < previous);
    }

    @Test
    public void visualSpinPresentationPreservesUnrestrictedFiniteRotation() {
        double veryFast = Math.PI * 2.0 * 22.0;
        double presented =
                Player.sanitizeVisualAngularVelocity(veryFast);

        assertEquals(veryFast, presented, 0.0);
        assertEquals(
                -presented,
                Player.sanitizeVisualAngularVelocity(-veryFast),
                0.0);
        assertEquals(
                0.0,
                Player.sanitizeVisualAngularVelocity(0.0),
                0.0);
        assertEquals(0.0,
                Player.sanitizeVisualAngularVelocity(Double.NaN), 0.0);
    }

    @Test
    public void presentedDeltaUnwrapsAcrossTheSignedPhaseBoundary() {
        double previous = Math.toRadians(170.0);
        double current = Math.toRadians(-170.0);

        assertEquals(
                Math.toRadians(20.0),
                Player.resolvePresentedAxleDelta(
                        previous, current, Math.toRadians(20.0)),
                1.0e-15);
        assertEquals(
                Math.toRadians(-340.0),
                Player.resolvePresentedAxleDelta(
                        previous, current, Math.toRadians(-340.0)),
                1.0e-15);
    }

    @Test
    public void physicalDeltaDisambiguatesAWholeTurnWithEqualEndpoints() {
        assertEquals(
                Math.PI * 2.0,
                Player.resolvePresentedAxleDelta(0.4, 0.4, Math.PI * 2.0),
                1.0e-15);
        assertEquals(
                -Math.PI * 4.0,
                Player.resolvePresentedAxleDelta(0.4, 0.4, -Math.PI * 4.0),
                1.0e-15);
    }

    @Test
    public void finitePresentedVelocitySaturatesInsteadOfCollapsingOnOverflow() {
        assertEquals(
                Double.MAX_VALUE,
                Player.saturatedSignedQuotient(
                        Double.MAX_VALUE, Double.MIN_NORMAL),
                0.0);
        assertEquals(
                -Double.MAX_VALUE,
                Player.saturatedSignedQuotient(
                        -Double.MAX_VALUE, Double.MIN_NORMAL),
                0.0);
    }

    @Test
    public void temporalExposureActivationIsAnIntentionalSubpixelPolicy() {
        WheelTemporalSamplingPlanner planner =
                new WheelTemporalSamplingPlanner();

        WheelTemporalSamplingPlanner.Plan below = planner.plan(
                0.49, 0.01, 0.01, 100.0);
        assertEquals(0.49, below.projectedSweepPixels(), 1.0e-15);
        assertTrue(!Player.shouldUseTemporalExposure(below));

        WheelTemporalSamplingPlanner.Plan above = planner.plan(
                0.55, 0.01, 0.01, 100.0);
        assertEquals(0.55, above.projectedSweepPixels(), 1.0e-15);
        assertTrue(Player.shouldUseTemporalExposure(above));
        assertTrue(Player.wheelBloomCorrectionBlend(above) > 0.0);
        assertTrue(Player.wheelBloomCorrectionBlend(above) < 0.01);

        WheelTemporalSamplingPlanner.Plan half = planner.plan(
                1.5, 0.01, 0.01, 100.0);
        assertEquals(0.5, Player.wheelBloomCorrectionBlend(half), 1.0e-15);

        WheelTemporalSamplingPlanner.Plan full = planner.plan(
                2.5, 0.01, 0.01, 100.0);
        assertEquals(1.0, Player.wheelBloomCorrectionBlend(full), 0.0);
    }

    @Test
    public void bandLodActivatesCoreWithoutArtificiallyPumpingBloomResidual() {
        double frame = 1.0 / 120.0;
        WheelTemporalSamplingPlanner.Plan plan =
                new WheelTemporalSamplingPlanner().planFromPresentedDelta(
                        WheelTemporalSamplingPlanner.GROOVE_PITCH_RADIANS
                                * 0.5,
                        frame * 0.75,
                        frame,
                        0.0);

        assertEquals(1.0, plan.continuousBandBlend(), 0.0);
        assertEquals(1.0, Player.wheelTemporalBlend(plan), 0.0);
        assertEquals(0.0, Player.wheelBloomCorrectionBlend(plan), 0.0);
        assertTrue(Player.shouldUseTemporalExposure(plan));
    }

    @Test
    public void violetDetailUsesNestedAnglePerFrameTransitions() {
        assertEquals(
                1f,
                Player.fadeOutForAngle(
                        5.0,
                        Player.DETAIL_GROOVES_FULL_BELOW_DEGREES,
                        Player.DETAIL_GROOVES_GONE_AT_DEGREES),
                0f);
        assertEquals(
                0f,
                Player.fadeOutForAngle(
                        20.0,
                        Player.SECONDARY_GROOVES_FULL_BELOW_DEGREES,
                        Player.SECONDARY_GROOVES_GONE_AT_DEGREES),
                0f);
        assertEquals(
                1f,
                Player.fadeOutForAngle(
                        20.0,
                        Player.PRIMARY_GROOVES_FULL_BELOW_DEGREES,
                        Player.PRIMARY_GROOVES_GONE_AT_DEGREES),
                0f);
        assertEquals(
                1f,
                Player.fadeInForAngle(
                        30.0,
                        Player.CORE_GLOW_START_DEGREES,
                        Player.CORE_GLOW_FULL_DEGREES),
                0f);
    }
}
