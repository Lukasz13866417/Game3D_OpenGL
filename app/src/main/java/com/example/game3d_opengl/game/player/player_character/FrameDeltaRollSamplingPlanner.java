package com.example.game3d_opengl.game.player.player_character;

/**
 * Experimental frame-delta integrator for wheel roll.
 *
 * <p>This is deliberately separate from {@link WheelTemporalSamplingPlanner}: it describes the
 * alternative of splitting only the latest presented roll delta, without a virtual shutter,
 * projected-size LOD, yaw samples, or a continuous-groove band. At or below the requested
 * maximum angle it returns the exact current pose with no blur. Once that threshold is exceeded,
 * samples are equal-angle midpoints over the interval from the previous pose to the current pose
 * and have uniform, normalized weights. The intentionally abrupt switch is retained so this
 * experiment can reveal any phase or latency discontinuity at its activation threshold.</p>
 *
 * <p>The current GLES renderer owns a fixed 4-by-3 temporal atlas, so it can consume no more
 * than {@value #MAX_ROLL_SAMPLES} poses. A delta requiring more subdivisions is capped and the
 * returned plan explicitly reports that its actual part angle exceeds the requested maximum.
 * This makes overload visible instead of allocating or silently claiming a false bound.</p>
 *
 * <p>The returned {@link Plan} is reused by subsequent calls. Callers must consume it before
 * asking this planner for another frame.</p>
 */
public final class FrameDeltaRollSamplingPlanner {
    /** Must fit the renderer's fixed 4-by-3 temporal atlas. */
    public static final int MAX_ROLL_SAMPLES =
            WheelTemporalSamplingPlanner.MAX_TEMPORAL_SAMPLES;

    private final Plan currentPlan = new Plan();

    /**
     * Splits one signed presented roll delta into equal-angle midpoint samples.
     *
     * @param signedPresentedRollDeltaRadians current roll minus previous roll, in radians
     * @param maximumPartAngleRadians desired positive maximum angular width of one part
     * @return a reused, allocation-free plan
     */
    public Plan plan(
            double signedPresentedRollDeltaRadians,
            double maximumPartAngleRadians) {
        boolean hadInvalidInput = !Double.isFinite(signedPresentedRollDeltaRadians)
                || !Double.isFinite(maximumPartAngleRadians)
                || !(maximumPartAngleRadians > 0.0);

        double rollDelta = hadInvalidInput ? 0.0 : signedPresentedRollDeltaRadians;
        double maximumPartAngle = hadInvalidInput ? 0.0 : maximumPartAngleRadians;
        long idealPartCount = chooseIdealPartCount(
                Math.abs(rollDelta),
                maximumPartAngle
        );
        boolean sampleBudgetExceeded = idealPartCount > MAX_ROLL_SAMPLES;
        int sampleCount = sampleBudgetExceeded
                ? MAX_ROLL_SAMPLES
                : (int) idealPartCount;

        currentPlan.populate(
                rollDelta,
                maximumPartAngle,
                idealPartCount,
                sampleCount,
                sampleBudgetExceeded,
                hadInvalidInput
        );
        return currentPlan;
    }

    private static long chooseIdealPartCount(
            double absoluteRollDelta,
            double maximumPartAngle) {
        if (!(absoluteRollDelta > 0.0) || !(maximumPartAngle > 0.0)) {
            return 1L;
        }

        double quotient = absoluteRollDelta / maximumPartAngle;
        if (!Double.isFinite(quotient) || quotient >= (double) Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(1L, (long) Math.ceil(quotient));
    }

    /** Read-only result view reused by {@link #plan(double, double)}. */
    public static final class Plan {
        private final double[] sampleAngleOffsetsRadians =
                new double[MAX_ROLL_SAMPLES];
        private final double[] sampleWeights =
                new double[MAX_ROLL_SAMPLES];

        private double signedPresentedRollDeltaRadians;
        private double maximumPartAngleRadians;
        private double actualPartAngleRadians;
        private long idealPartCount;
        private int sampleCount;
        private boolean sampleBudgetExceeded;
        private boolean hadInvalidInput;

        private Plan() {
        }

        private void populate(
                double rollDelta,
                double maximumPartAngle,
                long newIdealPartCount,
                int newSampleCount,
                boolean newSampleBudgetExceeded,
                boolean newHadInvalidInput) {
            signedPresentedRollDeltaRadians = rollDelta;
            maximumPartAngleRadians = maximumPartAngle;
            idealPartCount = newIdealPartCount;
            sampleCount = newSampleCount;
            sampleBudgetExceeded = newSampleBudgetExceeded;
            hadInvalidInput = newHadInvalidInput;
            actualPartAngleRadians = Math.abs(rollDelta) / newSampleCount;

            for (int index = 0; index < MAX_ROLL_SAMPLES; index++) {
                sampleAngleOffsetsRadians[index] = 0.0;
                sampleWeights[index] = 0.0;
            }

            if (newSampleCount == 1) {
                // Splitting and averaging begin only after the requested maximum is exceeded.
                sampleAngleOffsetsRadians[0] = 0.0;
                sampleWeights[0] = 1.0;
                return;
            }

            double normalizedWeight = 1.0 / newSampleCount;
            double accumulatedWeight = 0.0;
            for (int index = 0; index < newSampleCount; index++) {
                // This fraction decreases from almost one to almost zero. Multiplying the
                // original delta by a fraction avoids overflow even for near-Double.MAX_VALUE
                // inputs. Offset zero is the current pose, so positive roll has negative
                // history offsets and negative roll has positive history offsets.
                double fractionBeforeCurrent =
                        1.0 - (index + 0.5) / newSampleCount;
                sampleAngleOffsetsRadians[index] =
                        -rollDelta * fractionBeforeCurrent;
                sampleWeights[index] = normalizedWeight;
                accumulatedWeight += normalizedWeight;
            }
            sampleWeights[newSampleCount - 1] += 1.0 - accumulatedWeight;
        }

        /** Sanitized signed roll delta from the previous presented pose to the current pose. */
        public double signedPresentedRollDeltaRadians() {
            return signedPresentedRollDeltaRadians;
        }

        /** Sanitized requested upper bound. Zero means the input was invalid. */
        public double maximumPartAngleRadians() {
            return maximumPartAngleRadians;
        }

        /**
         * Number of parts requested by {@code ceil(abs(delta) / maximumPartAngle)}.
         * Extremely large results saturate at {@link Long#MAX_VALUE}.
         */
        public long idealPartCount() {
            return idealPartCount;
        }

        /** Number of samples that fit the current renderer budget. */
        public int sampleCount() {
            return sampleCount;
        }

        /** Absolute angular width represented by one actual, possibly capped part. */
        public double actualPartAngleRadians() {
            return actualPartAngleRadians;
        }

        /** True when the renderer budget prevented honoring the requested maximum part angle. */
        public boolean sampleBudgetExceeded() {
            return sampleBudgetExceeded;
        }

        /** True when either input was non-finite or the maximum part angle was not positive. */
        public boolean hadInvalidInput() {
            return hadInvalidInput;
        }

        /** Roll-only angle relative to the current pose; zero would be the current pose. */
        public double sampleAngleOffsetRadians(int index) {
            checkSampleIndex(index);
            return sampleAngleOffsetsRadians[index];
        }

        /** Uniform normalized radiance weight. Active sample weights sum to one. */
        public double sampleWeight(int index) {
            checkSampleIndex(index);
            return sampleWeights[index];
        }

        private void checkSampleIndex(int index) {
            if (index < 0 || index >= sampleCount) {
                throw new IndexOutOfBoundsException(
                        "Sample index " + index + " outside [0, " + sampleCount + ")"
                );
            }
        }
    }
}
