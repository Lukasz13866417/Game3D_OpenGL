package com.example.game3d_opengl.game.player.player_character;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpinBlurPlanTest {
    private static final double EPSILON = 1.0e-6;
    private static final double FRAME_120_HZ_MILLIS = 1000.0 / 120.0;
    private static final double FRAME_60_HZ_MILLIS = 1000.0 / 60.0;

    @Test
    public void remainsInactiveThroughTheEightDegreeThreshold() {
        SpinBlurPlan plan = new SpinBlurPlan();

        plan.update(angularVelocityForFrameArcDegrees(7.99, 10.0), 10.0);
        assertInactive(plan);

        plan.update(angularVelocityForFrameArcDegrees(8.0, 10.0), 10.0);
        assertInactive(plan);

        plan.update(angularVelocityForFrameArcDegrees(8.01, 10.0), 10.0);
        assertTrue(plan.isActive());
        assertEquals(2, plan.sampleCount());
        assertTrue(plan.sampleOpacity() > 0f);
    }

    @Test
    public void positiveSpinProducesSignedTrailingOffsetsBehindCurrentPhase() {
        SpinBlurPlan plan = new SpinBlurPlan();
        plan.update(angularVelocityForFrameArcDegrees(20.0, 10.0), 10.0);

        assertEquals(2, plan.sampleCount());
        assertEquals(Math.toRadians(-20.0), plan.startAngleRadians(), EPSILON);
        assertEquals(Math.toRadians(10.0), plan.angleStepRadians(), EPSILON);
        assertEquals(
                Math.toRadians(-10.0),
                offsetOf(plan, plan.sampleCount() - 1),
                EPSILON);
        assertAllSamplesTrailCurrentPhase(plan);
    }

    @Test
    public void negativeSpinMirrorsPositiveSpinWithoutChangingStrength() {
        SpinBlurPlan positive = new SpinBlurPlan();
        SpinBlurPlan negative = new SpinBlurPlan();
        double omega = angularVelocityForFrameArcDegrees(35.0, 10.0);

        positive.update(omega, 10.0);
        negative.update(-omega, 10.0);

        assertEquals(positive.sampleCount(), negative.sampleCount());
        assertEquals(
                -positive.startAngleRadians(),
                negative.startAngleRadians(),
                EPSILON);
        assertEquals(
                -positive.angleStepRadians(),
                negative.angleStepRadians(),
                EPSILON);
        assertEquals(positive.sampleOpacity(), negative.sampleOpacity(), 0f);
        assertAllSamplesTrailCurrentPhase(negative);
    }

    @Test
    public void adaptivelyAddsSamplesAtTwentyEightAndFortyEightDegrees() {
        SpinBlurPlan plan = new SpinBlurPlan();

        updateForArcDegrees(plan, 9.0);
        assertEquals(2, plan.sampleCount());
        updateForArcDegrees(plan, 28.0);
        assertEquals(2, plan.sampleCount());
        updateForArcDegrees(plan, 28.01);
        assertEquals(3, plan.sampleCount());
        updateForArcDegrees(plan, 48.0);
        assertEquals(3, plan.sampleCount());
        updateForArcDegrees(plan, 48.01);
        assertEquals(4, plan.sampleCount());
    }

    @Test
    public void capsBothSpinDirectionsAtSeventyDegrees() {
        SpinBlurPlan positive = new SpinBlurPlan();
        SpinBlurPlan negative = new SpinBlurPlan();

        positive.update(Double.MAX_VALUE, 90.0);
        negative.update(-Double.MAX_VALUE, 90.0);

        assertEquals(4, positive.sampleCount());
        assertEquals(Math.toRadians(-70.0), positive.startAngleRadians(), EPSILON);
        assertEquals(Math.toRadians(17.5), positive.angleStepRadians(), EPSILON);
        assertEquals(4, negative.sampleCount());
        assertEquals(Math.toRadians(70.0), negative.startAngleRadians(), EPSILON);
        assertEquals(Math.toRadians(-17.5), negative.angleStepRadians(), EPSILON);
    }

    @Test
    public void exposureTracksFrameDurationAtOneTwentyAndSixtyHertz() {
        SpinBlurPlan at120Hz = new SpinBlurPlan();
        SpinBlurPlan at60Hz = new SpinBlurPlan();
        double tenRevolutionsPerSecond = Math.PI * 2.0 * 10.0;

        at120Hz.update(tenRevolutionsPerSecond, FRAME_120_HZ_MILLIS);
        at60Hz.update(tenRevolutionsPerSecond, FRAME_60_HZ_MILLIS);

        // A 70% shutter produces 21 degrees at 120 Hz and 42 degrees at 60 Hz.
        assertEquals(Math.toRadians(-21.0), at120Hz.startAngleRadians(), EPSILON);
        assertEquals(2, at120Hz.sampleCount());
        assertEquals(Math.toRadians(-42.0), at60Hz.startAngleRadians(), EPSILON);
        assertEquals(3, at60Hz.sampleCount());
    }

    @Test
    public void visualSpinCapProducesUsefulBlurAtOneTwentyHertz() {
        SpinBlurPlan plan = new SpinBlurPlan();

        plan.update(
                Math.PI * 2.0 * Player.MAX_VISUAL_SPIN_RPS,
                FRAME_120_HZ_MILLIS);

        assertTrue(plan.isActive());
        assertEquals(2, plan.sampleCount());
        assertTrue("120 Hz trail is too faint to read",
                plan.sampleOpacity() >= 0.10f);
        assertTrue("trail obscures the sharp wheel pose",
                plan.sampleOpacity() <= 0.18f);
    }

    @Test
    public void longFrameUsesFiniteExposureAndAngleCaps() {
        SpinBlurPlan plan = new SpinBlurPlan();
        double tenRevolutionsPerSecond = Math.PI * 2.0 * 10.0;

        plan.update(tenRevolutionsPerSecond, 90.0);

        assertTrue(plan.isActive());
        assertEquals(4, plan.sampleCount());
        // The long frame is limited to a 1/60-second exposure: 10 rps then spans 60 degrees.
        assertEquals(Math.toRadians(-60.0), plan.startAngleRadians(), EPSILON);
        assertFiniteAndBounded(plan);
    }

    @Test
    public void zeroInvalidAndNegativeDurationsClearEveryOutput() {
        SpinBlurPlan plan = new SpinBlurPlan();
        updateForArcDegrees(plan, 40.0);
        assertTrue(plan.isActive());

        plan.update(Math.PI * 20.0, 0.0);
        assertInactive(plan);
        plan.update(Math.PI * 20.0, -90.0);
        assertInactive(plan);
        plan.update(Math.PI * 20.0, Double.NaN);
        assertInactive(plan);
        plan.update(Math.PI * 20.0, Double.POSITIVE_INFINITY);
        assertInactive(plan);
    }

    @Test
    public void invalidAngularVelocitiesClearEveryOutput() {
        SpinBlurPlan plan = new SpinBlurPlan();
        updateForArcDegrees(plan, 40.0);

        plan.update(Double.NaN, 90.0);
        assertInactive(plan);
        plan.update(Double.POSITIVE_INFINITY, 90.0);
        assertInactive(plan);
        plan.update(Double.NEGATIVE_INFINITY, 90.0);
        assertInactive(plan);
    }

    @Test
    public void opacityRampsSmoothlyAndKeepsCombinedBudgetBounded() {
        SpinBlurPlan nearThreshold = new SpinBlurPlan();
        SpinBlurPlan fullStrength = new SpinBlurPlan();
        updateForArcDegrees(nearThreshold, 9.0);
        updateForArcDegrees(fullStrength, 28.0);

        assertTrue(nearThreshold.sampleOpacity() > 0f);
        assertTrue(nearThreshold.sampleOpacity()
                < fullStrength.sampleOpacity());
        assertEquals(
                SpinBlurPlan.MAX_COMBINED_TRAIL_OPACITY,
                fullStrength.sampleOpacity() * fullStrength.sampleCount(),
                1.0e-6f);
        assertFiniteAndBounded(nearThreshold);
        assertFiniteAndBounded(fullStrength);
    }

    private static void updateForArcDegrees(
            SpinBlurPlan plan,
            double degrees
    ) {
        double frameMillis = 10.0;
        plan.update(
                angularVelocityForFrameArcDegrees(degrees, frameMillis),
                frameMillis);
    }

    private static double angularVelocityForFrameArcDegrees(
            double degrees,
            double frameMillis
    ) {
        double exposureSeconds = frameMillis * 0.001
                * SpinBlurPlan.SHUTTER_FRACTION;
        return Math.toRadians(degrees) / exposureSeconds;
    }

    private static double offsetOf(SpinBlurPlan plan, int sampleIndex) {
        return plan.startAngleRadians()
                + plan.angleStepRadians() * sampleIndex;
    }

    private static void assertAllSamplesTrailCurrentPhase(SpinBlurPlan plan) {
        double sign = Math.signum(plan.startAngleRadians());
        double previousMagnitude = Double.POSITIVE_INFINITY;
        for (int i = 0; i < plan.sampleCount(); i++) {
            double offset = offsetOf(plan, i);
            assertEquals(sign, Math.signum(offset), 0.0);
            assertTrue(Math.abs(offset) < previousMagnitude);
            previousMagnitude = Math.abs(offset);
        }
        assertTrue(previousMagnitude > 0.0);
    }

    private static void assertInactive(SpinBlurPlan plan) {
        assertFalse(plan.isActive());
        assertEquals(0, plan.sampleCount());
        assertEquals(0f, plan.startAngleRadians(), 0f);
        assertEquals(0f, plan.angleStepRadians(), 0f);
        assertEquals(0f, plan.sampleOpacity(), 0f);
    }

    private static void assertFiniteAndBounded(SpinBlurPlan plan) {
        assertTrue(Float.isFinite(plan.startAngleRadians()));
        assertTrue(Float.isFinite(plan.angleStepRadians()));
        assertTrue(Float.isFinite(plan.sampleOpacity()));
        assertTrue(Math.abs(plan.startAngleRadians())
                <= SpinBlurPlan.MAX_BLUR_ANGLE_RADIANS + EPSILON);
        assertTrue(plan.sampleOpacity() >= 0f);
        assertTrue(plan.sampleOpacity() * plan.sampleCount()
                <= SpinBlurPlan.MAX_COMBINED_TRAIL_OPACITY + 1.0e-6f);
    }
}
