package com.example.game3d_opengl.game.player.player_character;

/**
 * Plans a temporally integrated presentation of the player's rotating wheel.
 *
 * <p>This class deliberately has no Android or OpenGL dependency. A renderer supplies the
 * actual presented axle delta, requested shutter exposure, presentation-frame interval and
 * projected wheel radius. It receives a small, normalized set of centered exposure samples plus
 * the cross-fade between discrete glowing grooves and their high-speed continuous-band limit.</p>
 *
 * <p>The returned {@link Plan} is reused by the next call, so callers must consume it before
 * planning another frame. This keeps the per-frame path allocation-free. The groove-to-band
 * response itself is stateless: quantizing a continuous presentation blend with an LOD
 * deadband creates visible brightness plateaus while wheel speed changes.</p>
 */
public final class WheelTemporalSamplingPlanner {
    /** The mint wheel currently has eighteen evenly spaced glowing grooves. */
    public static final int GROOVE_COUNT = 18;
    public static final int MAX_TEMPORAL_SAMPLES = 12;
    /** One atlas cell is reserved for the phase-invariant band during the cross-fade. */
    public static final int MAX_PHYSICAL_SAMPLES_WITH_BAND =
            MAX_TEMPORAL_SAMPLES - 1;

    /** Angular period of the truthful eighteen-groove geometry. */
    public static final double GROOVE_PITCH_RADIANS =
            Math.PI * 2.0 / GROOVE_COUNT;

    /**
     * Identical grooves become directionally ambiguous after half their angular pitch.
     */
    public static final double ALIAS_HALF_PITCH_DEGREES =
            180.0 / GROOVE_COUNT;

    /** Begin replacing discrete contrast at 35% of one repeated-groove cycle per frame. */
    public static final double BAND_BLEND_START_GROOVE_CYCLES_PER_FRAME = 0.35;
    /** Reach the phase-invariant band at the half-cycle ambiguity boundary. */
    public static final double BAND_BLEND_END_GROOVE_CYCLES_PER_FRAME = 0.50;

    /** Degree aliases retained for diagnostics and source compatibility. */
    public static final double BAND_BLEND_START_DEGREES_PER_FRAME =
            BAND_BLEND_START_GROOVE_CYCLES_PER_FRAME
                    * 360.0 / GROOVE_COUNT;
    public static final double BAND_BLEND_END_DEGREES_PER_FRAME =
            BAND_BLEND_END_GROOVE_CYCLES_PER_FRAME
                    * 360.0 / GROOVE_COUNT;

    /**
     * At most a 360-degree shutter at 30 Hz is planned. This prevents a stalled presentation
     * frame from creating an arbitrarily long trail.
     */
    public static final double MAX_TEMPORAL_EXPOSURE_SECONDS = 1.0 / 30.0;

    /** Desired upper bound on projected travel between neighboring temporal samples. */
    public static final double TARGET_SAMPLE_SPACING_PIXELS = 0.75;

    private static final double RADIANS_TO_DEGREES = 180.0 / Math.PI;
    private static final double TWO_PI = Math.PI * 2.0;

    private final Plan currentPlan = new Plan();
    /**
     * Computes the plan for one presentation frame without allocating.
     *
     * @param angularVelocityRadiansPerSecond signed presentation angular velocity
     * @param requestedExposureSeconds desired virtual shutter duration
     * @param presentationFrameIntervalSeconds elapsed time between presented wheel poses
     * @param projectedRadiusPixels projected radius of the rotating detail in pixels
     * @return a view owned and reused by this planner
     */
    public Plan plan(
            double angularVelocityRadiansPerSecond,
            double requestedExposureSeconds,
            double presentationFrameIntervalSeconds,
            double projectedRadiusPixels) {
        double angularVelocity = angularVelocityRadiansPerSecond;
        boolean hadInvalidInput = false;
        if (!Double.isFinite(angularVelocity)) {
            angularVelocity = 0.0;
            hadInvalidInput = true;
        }

        double frameInterval = presentationFrameIntervalSeconds;
        if (!Double.isFinite(frameInterval) || frameInterval < 0.0) {
            frameInterval = 0.0;
            hadInvalidInput = true;
        }
        double presentedDelta = saturatedSignedProduct(
                angularVelocity, frameInterval);
        return planInternal(
                angularVelocity,
                presentedDelta,
                requestedExposureSeconds,
                frameInterval,
                projectedRadiusPixels,
                hadInvalidInput);
    }

    /**
     * Plans from the axle delta that actually reached two successive presented frames.
     *
     * <p>This is the production entry point. It avoids classifying a dropped or duplicated frame
     * from a configured/physics frame rate that did not actually reach the display.</p>
     */
    public Plan planFromPresentedDelta(
            double presentedAxleDeltaRadians,
            double requestedExposureSeconds,
            double presentationFrameIntervalSeconds,
            double projectedRadiusPixels) {
        boolean hadInvalidInput = false;
        double presentedDelta = presentedAxleDeltaRadians;
        if (!Double.isFinite(presentedDelta)) {
            presentedDelta = 0.0;
            hadInvalidInput = true;
        }

        double frameInterval = presentationFrameIntervalSeconds;
        if (!Double.isFinite(frameInterval) || frameInterval < 0.0) {
            frameInterval = 0.0;
            hadInvalidInput = true;
        }
        double angularVelocity = frameInterval > 0.0
                ? saturatedSignedQuotient(presentedDelta, frameInterval)
                : 0.0;
        return planInternal(
                angularVelocity,
                presentedDelta,
                requestedExposureSeconds,
                frameInterval,
                projectedRadiusPixels,
                hadInvalidInput);
    }

    private Plan planInternal(
            double angularVelocity,
            double presentedDelta,
            double requestedExposureSeconds,
            double frameInterval,
            double projectedRadiusPixels,
            boolean hadInvalidInput) {

        double requestedExposure = requestedExposureSeconds;
        if (!Double.isFinite(requestedExposure) || requestedExposure < 0.0) {
            requestedExposure = 0.0;
            hadInvalidInput = true;
        }

        double projectedRadius = projectedRadiusPixels;
        if (!Double.isFinite(projectedRadius) || projectedRadius < 0.0) {
            projectedRadius = 0.0;
            hadInvalidInput = true;
        }

        double effectiveExposure = Math.min(
                requestedExposure,
                MAX_TEMPORAL_EXPOSURE_SECONDS
        );
        if (frameInterval > 0.0) {
            // A virtual exposure longer than one presentation interval smears multiple displayed
            // poses together. High-speed aliasing is handled by the groove-to-band LOD instead.
            effectiveExposure = Math.min(effectiveExposure, frameInterval);
        }
        boolean exposureWasCapped = effectiveExposure < requestedExposure;

        double absoluteAngularVelocity = Math.abs(angularVelocity);
        double sweptRadiansPerFrame = Math.abs(presentedDelta);
        double degreesPerFrame = saturatedNonNegativeProduct(
                sweptRadiansPerFrame,
                RADIANS_TO_DEGREES
        );
        double grooveCyclesPerFrame = saturatedNonNegativeQuotient(
                sweptRadiansPerFrame,
                GROOVE_PITCH_RADIANS);

        double sweptRadiansDuringExposure = saturatedNonNegativeProduct(
                absoluteAngularVelocity,
                effectiveExposure
        );
        double projectedSweepPixels = saturatedNonNegativeProduct(
                sweptRadiansDuringExposure,
                projectedRadius
        );
        int sampleCount = chooseSampleCount(projectedSweepPixels);

        double rawBandBlend = smoothStep(
                BAND_BLEND_START_GROOVE_CYCLES_PER_FRAME,
                BAND_BLEND_END_GROOVE_CYCLES_PER_FRAME,
                grooveCyclesPerFrame
        );
        // The phase-invariant band occupies one atlas cell. Keep the physical shutter plus that
        // cell within the fixed twelve-cell budget throughout the transition.
        if (rawBandBlend > 0.0) {
            sampleCount = Math.min(
                    sampleCount, MAX_PHYSICAL_SAMPLES_WITH_BAND);
        }

        currentPlan.setScalarValues(
                angularVelocity,
                presentedDelta,
                requestedExposure,
                effectiveExposure,
                frameInterval,
                projectedRadius,
                degreesPerFrame,
                grooveCyclesPerFrame,
                projectedSweepPixels,
                rawBandBlend,
                hadInvalidInput,
                exposureWasCapped
        );
        currentPlan.populateSoftCenteredShutter(sampleCount, effectiveExposure);
        return currentPlan;
    }

    private static int chooseSampleCount(double projectedSweepPixels) {
        if (!(projectedSweepPixels > 0.0)) {
            return 1;
        }
        double requiredIntervals = Math.ceil(
                projectedSweepPixels / TARGET_SAMPLE_SPACING_PIXELS
        );
        if (requiredIntervals >= MAX_TEMPORAL_SAMPLES - 1) {
            return MAX_TEMPORAL_SAMPLES;
        }
        return 1 + (int) requiredIntervals;
    }

    private static double saturatedNonNegativeProduct(double first, double second) {
        if (!(first > 0.0) || !(second > 0.0)) {
            return 0.0;
        }
        if (first > Double.MAX_VALUE / second) {
            return Double.MAX_VALUE;
        }
        return first * second;
    }

    private static double saturatedSignedProduct(
            double signedValue,
            double nonNegativeValue) {
        if (signedValue == 0.0 || !(nonNegativeValue > 0.0)) {
            return 0.0;
        }
        if (Math.abs(signedValue) > Double.MAX_VALUE / nonNegativeValue) {
            return Math.copySign(Double.MAX_VALUE, signedValue);
        }
        return signedValue * nonNegativeValue;
    }

    private static double saturatedSignedQuotient(
            double signedValue,
            double positiveDivisor) {
        if (signedValue == 0.0 || !(positiveDivisor > 0.0)) {
            return 0.0;
        }
        if (positiveDivisor < 1.0
                && Math.abs(signedValue) > Double.MAX_VALUE * positiveDivisor) {
            return Math.copySign(Double.MAX_VALUE, signedValue);
        }
        return signedValue / positiveDivisor;
    }

    private static double saturatedNonNegativeQuotient(
            double nonNegativeValue,
            double positiveDivisor) {
        if (!(nonNegativeValue > 0.0) || !(positiveDivisor > 0.0)) {
            return 0.0;
        }
        if (positiveDivisor < 1.0
                && nonNegativeValue > Double.MAX_VALUE * positiveDivisor) {
            return Double.MAX_VALUE;
        }
        return nonNegativeValue / positiveDivisor;
    }

    private static double smoothStep(double lower, double upper, double value) {
        if (value <= lower) {
            return 0.0;
        }
        if (value >= upper) {
            return 1.0;
        }
        double unit = (value - lower) / (upper - lower);
        return unit * unit * (3.0 - 2.0 * unit);
    }

    /** Read-only result view reused by {@link WheelTemporalSamplingPlanner#plan}. */
    public static final class Plan {
        private final double[] sampleTimesSeconds =
                new double[MAX_TEMPORAL_SAMPLES];
        private final double[] sampleWeights =
                new double[MAX_TEMPORAL_SAMPLES];

        private double angularVelocityRadiansPerSecond;
        private double presentedAxleDeltaRadians;
        private double requestedExposureSeconds;
        private double effectiveExposureSeconds;
        private double presentationFrameIntervalSeconds;
        private double projectedRadiusPixels;
        private double degreesPerFrame;
        private double grooveCyclesPerFrame;
        private double projectedSweepPixels;
        private double rawContinuousBandBlend;
        private double continuousBandBlend;
        private double grooveContrast;
        private int sampleCount;
        private boolean hadInvalidInput;
        private boolean exposureWasCapped;

        private Plan() {
        }

        private void setScalarValues(
                double angularVelocityRadiansPerSecond,
                double presentedAxleDeltaRadians,
                double requestedExposureSeconds,
                double effectiveExposureSeconds,
                double presentationFrameIntervalSeconds,
                double projectedRadiusPixels,
                double degreesPerFrame,
                double grooveCyclesPerFrame,
                double projectedSweepPixels,
                double rawContinuousBandBlend,
                boolean hadInvalidInput,
                boolean exposureWasCapped) {
            this.angularVelocityRadiansPerSecond = angularVelocityRadiansPerSecond;
            this.presentedAxleDeltaRadians = presentedAxleDeltaRadians;
            this.requestedExposureSeconds = requestedExposureSeconds;
            this.effectiveExposureSeconds = effectiveExposureSeconds;
            this.presentationFrameIntervalSeconds = presentationFrameIntervalSeconds;
            this.projectedRadiusPixels = projectedRadiusPixels;
            this.degreesPerFrame = degreesPerFrame;
            this.grooveCyclesPerFrame = grooveCyclesPerFrame;
            this.projectedSweepPixels = projectedSweepPixels;
            this.rawContinuousBandBlend = rawContinuousBandBlend;
            this.continuousBandBlend = rawContinuousBandBlend;
            this.grooveContrast = 1.0 - rawContinuousBandBlend;
            this.hadInvalidInput = hadInvalidInput;
            this.exposureWasCapped = exposureWasCapped;
        }

        private void populateSoftCenteredShutter(
                int newSampleCount,
                double exposureSeconds) {
            sampleCount = newSampleCount;
            for (int index = 0; index < MAX_TEMPORAL_SAMPLES; index++) {
                sampleTimesSeconds[index] = 0.0;
                sampleWeights[index] = 0.0;
            }
            if (newSampleCount == 1) {
                sampleWeights[0] = 1.0;
                return;
            }

            double rawWeightSum = 0.0;
            for (int index = 0; index < newSampleCount; index++) {
                // Midpoint quadrature avoids spending samples on the zero-valued exact edges of
                // the Hann exposure while retaining a symmetric, softly closing shutter.
                double unitTime = (index + 0.5) / newSampleCount;
                sampleTimesSeconds[index] = (unitTime - 0.5) * exposureSeconds;
                double rawWeight = 0.5 - 0.5 * Math.cos(TWO_PI * unitTime);
                sampleWeights[index] = rawWeight;
                rawWeightSum += rawWeight;
            }

            double normalizedSum = 0.0;
            for (int index = 0; index < newSampleCount; index++) {
                sampleWeights[index] /= rawWeightSum;
                normalizedSum += sampleWeights[index];
            }
            // Make the radiance-conservation invariant exact to normal floating-point precision.
            sampleWeights[newSampleCount - 1] += 1.0 - normalizedSum;
        }

        public double angularVelocityRadiansPerSecond() {
            return angularVelocityRadiansPerSecond;
        }

        /** Signed unwrapped axle delta between the two presented poses. */
        public double presentedAxleDeltaRadians() {
            return presentedAxleDeltaRadians;
        }

        public double requestedExposureSeconds() {
            return requestedExposureSeconds;
        }

        public double effectiveExposureSeconds() {
            return effectiveExposureSeconds;
        }

        public double presentationFrameIntervalSeconds() {
            return presentationFrameIntervalSeconds;
        }

        public double projectedRadiusPixels() {
            return projectedRadiusPixels;
        }

        public double degreesPerFrame() {
            return degreesPerFrame;
        }

        /** Absolute repeated-groove cycles crossed by the actual presented frame. */
        public double grooveCyclesPerFrame() {
            return grooveCyclesPerFrame;
        }

        public double projectedSweepPixels() {
            return projectedSweepPixels;
        }

        /** Stateless groove-to-band target. */
        public double rawContinuousBandBlend() {
            return rawContinuousBandBlend;
        }

        /** Smooth contribution of the energy-matched continuous emissive band. */
        public double continuousBandBlend() {
            return continuousBandBlend;
        }

        /** Remaining contrast of the eighteen individual emissive grooves. */
        public double grooveContrast() {
            return grooveContrast;
        }

        public int sampleCount() {
            return sampleCount;
        }

        /** Time relative to the centered presentation instant. */
        public double sampleTimeSeconds(int index) {
            checkSampleIndex(index);
            return sampleTimesSeconds[index];
        }

        /**
         * Exposure-normalized time in the centered {@code [-0.5, +0.5]} shutter interval.
         */
        public double sampleTimeFraction(int index) {
            checkSampleIndex(index);
            if (effectiveExposureSeconds == 0.0) {
                return 0.0;
            }
            return sampleTimesSeconds[index] / effectiveExposureSeconds;
        }

        /** Normalized physical-shutter weight. All active weights sum to one. */
        public double sampleWeight(int index) {
            checkSampleIndex(index);
            return sampleWeights[index];
        }

        /**
         * Premultiplied physical-groove contribution after the band cross-fade. The active
         * resolved weights sum to {@link #grooveContrast()}.
         */
        public double resolvedSampleWeight(int index) {
            checkSampleIndex(index);
            return sampleWeights[index] * grooveContrast;
        }

        /**
         * Local axle-angle offset for a physical groove-exposure sample. The separate band mesh
         * is phase invariant, so it never modifies or reinterprets these actual shutter poses.
         */
        public double resolvedSampleAngleOffsetRadians(int index) {
            checkSampleIndex(index);
            return finitePeriodicProduct(
                    angularVelocityRadiansPerSecond,
                    sampleTimesSeconds[index],
                    TWO_PI);
        }

        /** True when at least one physical groove exposure cell contributes visible energy. */
        public boolean requiresPhysicalGrooveExposure() {
            return grooveContrast > 1.0e-6;
        }

        /** True when the dedicated phase-invariant band contributes visible energy. */
        public boolean requiresMotionBand() {
            return continuousBandBlend > 1.0e-6;
        }

        /** Number of physical groove cells when the dedicated band asset is available. */
        public int physicalAtlasSampleCount(boolean motionBandAvailable) {
            if (motionBandAvailable && requiresMotionBand()
                    && !requiresPhysicalGrooveExposure()) {
                return 0;
            }
            return sampleCount;
        }

        /** Fixed atlas work, including the optional one-cell phase-invariant band. */
        public int combinedAtlasSampleCount(boolean motionBandAvailable) {
            int physical = physicalAtlasSampleCount(motionBandAvailable);
            return physical + (motionBandAvailable && requiresMotionBand()
                    ? 1 : 0);
        }

        /**
         * Multiplies an unrestricted finite velocity by time without allowing the equivalent
         * rotation passed to the renderer to overflow. Reducing an angle by a full revolution
         * changes neither the sampled wheel pose nor the physical shutter distribution.
         */
        private static double finitePeriodicProduct(
                double value,
                double factor,
                double period) {
            if (value == 0.0 || factor == 0.0) {
                return 0.0;
            }
            double product = value * factor;
            if (Double.isFinite(product)) {
                return Math.IEEEremainder(product, period);
            }

            // Reduce before multiplying when the direct product overflowed. Since
            // (value mod (period / |factor|)) * factor is congruent to value*factor modulo
            // period, this preserves the sampled pose while bounding the intermediate.
            double inputPeriod = period / Math.abs(factor);
            if (Double.isFinite(inputPeriod) && inputPeriod > 0.0) {
                double reduced = Math.IEEEremainder(value, inputPeriod) * factor;
                if (Double.isFinite(reduced)) {
                    return Math.IEEEremainder(reduced, period);
                }
            }
            return 0.0;
        }

        public boolean hadInvalidInput() {
            return hadInvalidInput;
        }

        public boolean exposureWasCapped() {
            return exposureWasCapped;
        }

        private void checkSampleIndex(int index) {
            if (index < 0 || index >= sampleCount) {
                throw new IndexOutOfBoundsException(
                        "sample index " + index + " outside [0, " + sampleCount + ')'
                );
            }
        }
    }
}
