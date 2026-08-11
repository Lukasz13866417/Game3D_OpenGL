package com.example.game3d_opengl.game.player.player_character;

/**
 * Reusable, allocation-free description of a causal axle-spin motion-blur trail.
 *
 * <p>The normally rendered player is the sharp sample at the current, interpolated axle phase.
 * This plan describes only the older translucent samples behind it. Angles are object-local
 * offsets around the player's axle: a positive angular velocity therefore produces negative
 * (older) sample offsets, and vice versa.</p>
 */
public final class SpinBlurPlan {
    static final double SHUTTER_FRACTION = 0.70;
    static final double MAX_EXPOSURE_SECONDS = 1.0 / 60.0;
    static final double ACTIVATION_ANGLE_RADIANS = Math.toRadians(8.0);
    static final double THREE_SAMPLE_MAX_ANGLE_RADIANS = Math.toRadians(28.0);
    static final double FOUR_SAMPLE_MAX_ANGLE_RADIANS = Math.toRadians(48.0);
    static final double MAX_BLUR_ANGLE_RADIANS = Math.toRadians(70.0);
    /** Reach useful opacity at the normal 120 Hz / 5-rps presentation cap. */
    static final double FULL_STRENGTH_ANGLE_RADIANS =
            Math.toRadians(12.0);
    static final float MAX_COMBINED_TRAIL_OPACITY = 0.36f;

    private int sampleCount;
    private float startAngleRadians;
    private float angleStepRadians;
    private float sampleOpacity;

    /**
     * Updates this plan in place.
     *
     * @param angularVelocityRadiansPerSecond signed authoritative/render angular velocity
     * @param frameDeltaMillis elapsed presentation time in milliseconds
     */
    public void update(
            double angularVelocityRadiansPerSecond,
            double frameDeltaMillis
    ) {
        if (!isFinite(angularVelocityRadiansPerSecond)
                || !isFinite(frameDeltaMillis)
                || frameDeltaMillis <= 0.0) {
            clear();
            return;
        }

        double exposureSeconds = Math.min(
                frameDeltaMillis * 0.001 * SHUTTER_FRACTION,
                MAX_EXPOSURE_SECONDS);
        if (!(exposureSeconds > 0.0)) {
            clear();
            return;
        }

        double uncappedBlurAngle =
                angularVelocityRadiansPerSecond * exposureSeconds;
        double absoluteBlurAngle = Math.abs(uncappedBlurAngle);
        if (!(absoluteBlurAngle > ACTIVATION_ANGLE_RADIANS)) {
            clear();
            return;
        }

        double blurAngle = Math.copySign(
                Math.min(absoluteBlurAngle, MAX_BLUR_ANGLE_RADIANS),
                uncappedBlurAngle);
        double cappedAbsoluteAngle = Math.abs(blurAngle);

        if (cappedAbsoluteAngle <= THREE_SAMPLE_MAX_ANGLE_RADIANS) {
            // Two trailing instances plus the ordinary sharp pose make three temporal samples.
            sampleCount = 2;
        } else if (cappedAbsoluteAngle <= FOUR_SAMPLE_MAX_ANGLE_RADIANS) {
            sampleCount = 3;
        } else {
            sampleCount = 4;
        }

        // Samples run from the oldest pose towards (but never duplicate) the sharp current pose.
        startAngleRadians = (float) -blurAngle;
        angleStepRadians = (float) (blurAngle / sampleCount);

        double normalizedStrength = clamp01(
                (cappedAbsoluteAngle - ACTIVATION_ANGLE_RADIANS)
                        / (FULL_STRENGTH_ANGLE_RADIANS
                        - ACTIVATION_ANGLE_RADIANS));
        double smoothStrength = normalizedStrength * normalizedStrength
                * (3.0 - 2.0 * normalizedStrength);
        sampleOpacity = (float) (
                MAX_COMBINED_TRAIL_OPACITY
                        * smoothStrength
                        / sampleCount);
    }

    /** Clears stale values so an inactive plan is safe to consume without additional guards. */
    public void clear() {
        sampleCount = 0;
        startAngleRadians = 0f;
        angleStepRadians = 0f;
        sampleOpacity = 0f;
    }

    public boolean isActive() {
        return sampleCount > 0;
    }

    /** Number of translucent trailing instances; the ordinary sharp pose is not included. */
    public int sampleCount() {
        return sampleCount;
    }

    /** Object-local angular offset of the oldest trailing sample. */
    public float startAngleRadians() {
        return startAngleRadians;
    }

    /** Signed increment applied for each subsequent trailing instance. */
    public float angleStepRadians() {
        return angleStepRadians;
    }

    /** Straight-alpha opacity applied to every trailing instance. */
    public float sampleOpacity() {
        return sampleOpacity;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
