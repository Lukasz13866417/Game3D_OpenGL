package com.example.game3d_opengl.game.player.player_character;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WheelTemporalSamplingPlannerTest {
    private static final double FRAME_120_HZ = 1.0 / 120.0;

    @Test
    public void stationaryWheelUsesOneCenteredEnergyConservingSample() {
        WheelTemporalSamplingPlanner.Plan plan =
                new WheelTemporalSamplingPlanner().plan(
                        0.0,
                        FRAME_120_HZ,
                        FRAME_120_HZ,
                        180.0
                );

        assertEquals(1, plan.sampleCount());
        assertEquals(0.0, plan.sampleTimeSeconds(0), 0.0);
        assertEquals(1.0, plan.sampleWeight(0), 0.0);
        assertEquals(0.0, plan.degreesPerFrame(), 0.0);
        assertEquals(0.0, plan.continuousBandBlend(), 0.0);
        assertEquals(1.0, plan.grooveContrast(), 0.0);
        assertFalse(plan.hadInvalidInput());
    }

    @Test
    public void shutterSamplesAreSoftSymmetricCenteredAndNormalized() {
        WheelTemporalSamplingPlanner.Plan plan =
                new WheelTemporalSamplingPlanner().plan(
                        5.0,
                        0.01,
                        0.01,
                        1000.0
                );

        assertEquals(WheelTemporalSamplingPlanner.MAX_TEMPORAL_SAMPLES,
                plan.sampleCount());
        double weightSum = 0.0;
        double weightedTime = 0.0;
        for (int index = 0; index < plan.sampleCount(); index++) {
            int opposite = plan.sampleCount() - 1 - index;
            assertEquals(-plan.sampleTimeSeconds(opposite),
                    plan.sampleTimeSeconds(index), 1.0e-15);
            assertEquals(plan.sampleWeight(opposite),
                    plan.sampleWeight(index), 1.0e-15);
            assertTrue(plan.sampleWeight(index) >= 0.0);
            assertTrue(Math.abs(plan.sampleTimeSeconds(index)) < 0.005);
            assertTrue(Math.abs(plan.sampleTimeFraction(index)) < 0.5);
            assertEquals(
                    plan.sampleTimeSeconds(index),
                    plan.sampleTimeFraction(index)
                            * plan.effectiveExposureSeconds(),
                    1.0e-15
            );
            if (index > 0) {
                assertTrue(plan.sampleTimeSeconds(index)
                        > plan.sampleTimeSeconds(index - 1));
            }
            weightSum += plan.sampleWeight(index);
            weightedTime += plan.sampleWeight(index)
                    * plan.sampleTimeSeconds(index);
        }

        assertEquals(1.0, weightSum, 1.0e-15);
        assertEquals(0.0, weightedTime, 1.0e-15);
        assertTrue(plan.sampleWeight(0)
                < plan.sampleWeight(plan.sampleCount() / 2));
    }

    @Test
    public void projectedTravelSelectsAdaptiveSampleCountAndCapsAtTwelve() {
        WheelTemporalSamplingPlanner planner =
                new WheelTemporalSamplingPlanner();

        // 0.5 projected pixels: one interval plus the first sample.
        assertEquals(2, planner.plan(10.0, 0.01, 0.01, 5.0).sampleCount());
        // 1.0 projected pixels: two intervals plus the first sample.
        assertEquals(3, planner.plan(10.0, 0.01, 0.01, 10.0).sampleCount());
        assertEquals(WheelTemporalSamplingPlanner.MAX_TEMPORAL_SAMPLES,
                planner.plan(10.0, 0.01, 0.01, 1000.0).sampleCount());

        WheelTemporalSamplingPlanner.Plan transition =
                planForDegreesPerFrame(planner, 8.5);
        assertEquals(
                WheelTemporalSamplingPlanner.MAX_PHYSICAL_SAMPLES_WITH_BAND,
                transition.sampleCount());
        assertEquals(WheelTemporalSamplingPlanner.MAX_TEMPORAL_SAMPLES,
                transition.combinedAtlasSampleCount(true));
    }

    @Test
    public void grooveToBandTransitionStraddlesAliasBoundary() {
        WheelTemporalSamplingPlanner planner =
                new WheelTemporalSamplingPlanner();

        WheelTemporalSamplingPlanner.Plan below =
                planForDegreesPerFrame(planner, 6.9);
        assertEquals(0.0, below.continuousBandBlend(), 0.0);
        assertEquals(1.0, below.grooveContrast(), 0.0);

        WheelTemporalSamplingPlanner.Plan middle =
                planForDegreesPerFrame(planner, 8.5);
        assertEquals(0.5, middle.rawContinuousBandBlend(), 1.0e-15);
        assertEquals(0.5, middle.continuousBandBlend(), 1.0e-15);
        assertEquals(0.5, middle.grooveContrast(), 1.0e-15);

        WheelTemporalSamplingPlanner.Plan above =
                planForDegreesPerFrame(planner, 10.0);
        assertEquals(1.0, above.continuousBandBlend(), 0.0);
        assertEquals(0.0, above.grooveContrast(), 0.0);
        assertEquals(10.0,
                WheelTemporalSamplingPlanner.ALIAS_HALF_PITCH_DEGREES,
                0.0);
        assertEquals(
                WheelTemporalSamplingPlanner.MAX_PHYSICAL_SAMPLES_WITH_BAND,
                above.sampleCount());
        assertEquals(0, above.physicalAtlasSampleCount(true));
        assertEquals(1, above.combinedAtlasSampleCount(true));
        double weightSum = 0.0;
        for (int index = 0; index < above.sampleCount(); index++) {
            assertEquals(0.0,
                    above.resolvedSampleWeight(index), 0.0);
            weightSum += above.resolvedSampleWeight(index);
        }
        assertEquals(0.0, weightSum, 0.0);
    }

    @Test
    public void continuousBandBlendRespondsSmoothlyToEverySpeedChange() {
        WheelTemporalSamplingPlanner planner =
                new WheelTemporalSamplingPlanner();

        WheelTemporalSamplingPlanner.Plan first =
                planForDegreesPerFrame(planner, 9.0);
        double initialBlend = first.continuousBandBlend();

        WheelTemporalSamplingPlanner.Plan changed =
                planForDegreesPerFrame(planner, 9.1);
        assertEquals(changed.rawContinuousBandBlend(),
                changed.continuousBandBlend(), 0.0);
        assertTrue(changed.continuousBandBlend() > initialBlend);
    }

    @Test
    public void directionDoesNotChangeExposureOrLodPlan() {
        double speed = Math.PI * 2.0 * 3.0;
        WheelTemporalSamplingPlanner.Plan positive =
                new WheelTemporalSamplingPlanner().plan(
                        speed, FRAME_120_HZ, FRAME_120_HZ, 120.0);
        WheelTemporalSamplingPlanner.Plan negative =
                new WheelTemporalSamplingPlanner().plan(
                        -speed, FRAME_120_HZ, FRAME_120_HZ, 120.0);

        assertEquals(speed, positive.angularVelocityRadiansPerSecond(), 0.0);
        assertEquals(-speed, negative.angularVelocityRadiansPerSecond(), 0.0);
        assertEquals(positive.degreesPerFrame(),
                negative.degreesPerFrame(), 0.0);
        assertEquals(positive.continuousBandBlend(),
                negative.continuousBandBlend(), 0.0);
        assertEquals(positive.sampleCount(), negative.sampleCount());
        for (int index = 0; index < positive.sampleCount(); index++) {
            assertEquals(positive.sampleTimeSeconds(index),
                    negative.sampleTimeSeconds(index), 0.0);
            assertEquals(positive.sampleWeight(index),
                    negative.sampleWeight(index), 0.0);
            assertEquals(positive.resolvedSampleWeight(index),
                    negative.resolvedSampleWeight(index), 0.0);
            assertEquals(positive.resolvedSampleAngleOffsetRadians(index),
                    -negative.resolvedSampleAngleOffsetRadians(index), 1.0e-15);
        }
    }

    @Test
    public void invalidInputsDegradeToFiniteStationaryPlan() {
        WheelTemporalSamplingPlanner.Plan plan =
                new WheelTemporalSamplingPlanner().plan(
                        Double.NaN,
                        Double.POSITIVE_INFINITY,
                        -1.0,
                        Double.NaN
                );

        assertTrue(plan.hadInvalidInput());
        assertEquals(0.0, plan.angularVelocityRadiansPerSecond(), 0.0);
        assertEquals(0.0, plan.requestedExposureSeconds(), 0.0);
        assertEquals(0.0, plan.presentationFrameIntervalSeconds(), 0.0);
        assertEquals(0.0, plan.projectedRadiusPixels(), 0.0);
        assertEquals(1, plan.sampleCount());
        assertEquals(0.0, plan.degreesPerFrame(), 0.0);
        assertFinitePlan(plan);
    }

    @Test
    public void enormousFiniteInputsSaturateWithoutOverflowOrArtificialRpsCap() {
        WheelTemporalSamplingPlanner.Plan plan =
                new WheelTemporalSamplingPlanner().plan(
                        Double.MAX_VALUE,
                        Double.MAX_VALUE,
                        Double.MAX_VALUE,
                        Double.MAX_VALUE
                );

        assertFalse(plan.hadInvalidInput());
        assertTrue(plan.exposureWasCapped());
        assertEquals(WheelTemporalSamplingPlanner.MAX_TEMPORAL_EXPOSURE_SECONDS,
                plan.effectiveExposureSeconds(), 0.0);
        assertEquals(Double.MAX_VALUE, plan.degreesPerFrame(), 0.0);
        assertEquals(Double.MAX_VALUE, plan.projectedSweepPixels(), 0.0);
        assertEquals(WheelTemporalSamplingPlanner.MAX_PHYSICAL_SAMPLES_WITH_BAND,
                plan.sampleCount());
        assertEquals(1.0, plan.continuousBandBlend(), 0.0);
        assertEquals(0, plan.physicalAtlasSampleCount(true));
        assertEquals(1, plan.combinedAtlasSampleCount(true));
        assertFinitePlan(plan);
        for (int sample = 0; sample < plan.sampleCount(); sample++) {
            assertTrue(Double.isFinite(
                    plan.resolvedSampleAngleOffsetRadians(sample)));
        }
    }

    @Test
    public void zeroFrameIntervalStillProducesFiniteEquivalentPhysicalPoses() {
        WheelTemporalSamplingPlanner.Plan plan =
                new WheelTemporalSamplingPlanner().plan(
                        Double.MAX_VALUE,
                        WheelTemporalSamplingPlanner.MAX_TEMPORAL_EXPOSURE_SECONDS,
                        0.0,
                        100.0
                );

        assertEquals(0.0, plan.continuousBandBlend(), 0.0);
        assertEquals(WheelTemporalSamplingPlanner.MAX_TEMPORAL_SAMPLES,
                plan.sampleCount());
        for (int sample = 0; sample < plan.sampleCount(); sample++) {
            double angle = plan.resolvedSampleAngleOffsetRadians(sample);
            assertTrue(Double.isFinite(angle));
            assertTrue(Math.abs(angle) <= Math.PI);
        }
    }

    @Test
    public void exposureIsCappedByFrameIntervalAndByHitchLimit() {
        WheelTemporalSamplingPlanner.Plan ordinary =
                new WheelTemporalSamplingPlanner().plan(
                        1.0, 1.0, 0.01, 100.0);
        assertEquals(0.01, ordinary.effectiveExposureSeconds(), 0.0);
        assertTrue(ordinary.exposureWasCapped());

        WheelTemporalSamplingPlanner.Plan hitch =
                new WheelTemporalSamplingPlanner().plan(
                        1.0, 1.0, 0.5, 100.0);
        assertEquals(WheelTemporalSamplingPlanner.MAX_TEMPORAL_EXPOSURE_SECONDS,
                hitch.effectiveExposureSeconds(), 0.0);
        assertTrue(hitch.exposureWasCapped());
    }

    @Test
    public void slowPresentedFrameStillUsesItsFullPhaseJumpForAliasLod() {
        double frameInterval = 0.090;
        double angularVelocity = Math.toRadians(1000.0);
        WheelTemporalSamplingPlanner.Plan plan =
                new WheelTemporalSamplingPlanner().plan(
                        angularVelocity,
                        frameInterval * 0.75,
                        frameInterval,
                        80.0);

        assertEquals(90.0, plan.degreesPerFrame(), 1.0e-12);
        assertEquals(1.0, plan.continuousBandBlend(), 0.0);
        assertEquals(WheelTemporalSamplingPlanner.MAX_TEMPORAL_EXPOSURE_SECONDS,
                plan.effectiveExposureSeconds(), 0.0);
    }

    @Test
    public void actualPresentedDeltaDirectlyDrivesGrooveCycleLod() {
        WheelTemporalSamplingPlanner planner =
                new WheelTemporalSamplingPlanner();
        double pitch = WheelTemporalSamplingPlanner.GROOVE_PITCH_RADIANS;

        WheelTemporalSamplingPlanner.Plan start =
                planner.planFromPresentedDelta(
                        pitch * 0.35,
                        FRAME_120_HZ * 0.75,
                        FRAME_120_HZ,
                        100.0);
        assertEquals(0.35, start.grooveCyclesPerFrame(), 1.0e-15);
        assertEquals(0.0, start.continuousBandBlend(), 0.0);

        WheelTemporalSamplingPlanner.Plan middle =
                planner.planFromPresentedDelta(
                        -pitch * 0.425,
                        FRAME_120_HZ * 0.75,
                        FRAME_120_HZ,
                        100.0);
        assertEquals(-pitch * 0.425,
                middle.presentedAxleDeltaRadians(), 0.0);
        assertEquals(0.425, middle.grooveCyclesPerFrame(), 1.0e-15);
        assertEquals(0.5, middle.continuousBandBlend(), 1.0e-14);

        WheelTemporalSamplingPlanner.Plan end =
                planner.planFromPresentedDelta(
                        pitch * 0.5,
                        FRAME_120_HZ * 0.75,
                        FRAME_120_HZ,
                        100.0);
        assertEquals(1.0, end.continuousBandBlend(), 0.0);
        assertEquals(0, end.physicalAtlasSampleCount(true));
        assertEquals(1, end.combinedAtlasSampleCount(true));
    }

    @Test
    public void duplicatedPresentedPoseStaysSharpRatherThanUsingMotorRate() {
        WheelTemporalSamplingPlanner.Plan plan =
                new WheelTemporalSamplingPlanner().planFromPresentedDelta(
                        0.0,
                        FRAME_120_HZ * 0.75,
                        FRAME_120_HZ,
                        200.0);

        assertEquals(0.0, plan.angularVelocityRadiansPerSecond(), 0.0);
        assertEquals(0.0, plan.grooveCyclesPerFrame(), 0.0);
        assertEquals(0.0, plan.continuousBandBlend(), 0.0);
        assertEquals(1, plan.combinedAtlasSampleCount(true));
    }

    @Test
    public void zeroRadiusStillBandlimitsAnUnambiguouslyFastWheel() {
        WheelTemporalSamplingPlanner.Plan plan =
                new WheelTemporalSamplingPlanner().plan(
                        1.0e100,
                        FRAME_120_HZ,
                        FRAME_120_HZ,
                        0.0
                );

        assertEquals(1, plan.sampleCount());
        assertEquals(1.0, plan.continuousBandBlend(), 0.0);
        assertEquals(1, plan.combinedAtlasSampleCount(true));
    }

    @Test
    public void planViewIsReusedWithoutPerFrameAllocation() {
        WheelTemporalSamplingPlanner planner =
                new WheelTemporalSamplingPlanner();
        WheelTemporalSamplingPlanner.Plan first =
                planner.plan(1.0, 0.01, 0.01, 1.0);
        WheelTemporalSamplingPlanner.Plan second =
                planner.plan(2.0, 0.01, 0.01, 1.0);

        assertSame(first, second);
        assertEquals(2.0, first.angularVelocityRadiansPerSecond(), 0.0);
    }

    @Test
    public void resolvedExposureRemainsFiniteNormalizedAndBoundedAcrossSpeeds() {
        WheelTemporalSamplingPlanner planner =
                new WheelTemporalSamplingPlanner();
        for (int degreesPerFrame = 0; degreesPerFrame <= 360;
             degreesPerFrame++) {
            WheelTemporalSamplingPlanner.Plan plan =
                    planForDegreesPerFrame(planner, degreesPerFrame);
            double weightSum = 0.0;
            for (int sample = 0; sample < plan.sampleCount(); sample++) {
                double weight = plan.resolvedSampleWeight(sample);
                double angle =
                        plan.resolvedSampleAngleOffsetRadians(sample);
                assertTrue(Double.isFinite(weight));
                assertTrue(Double.isFinite(angle));
                assertTrue(weight >= 0.0);
                weightSum += weight;
                assertTrue(Math.abs(angle) <= Math.PI);
            }
            assertEquals(plan.grooveContrast(), weightSum, 1.0e-12);
            assertTrue(plan.combinedAtlasSampleCount(true)
                    <= WheelTemporalSamplingPlanner.MAX_TEMPORAL_SAMPLES);
        }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void inactiveSampleCannotBeRead() {
        WheelTemporalSamplingPlanner.Plan plan =
                new WheelTemporalSamplingPlanner().plan(
                        0.0, FRAME_120_HZ, FRAME_120_HZ, 10.0);
        plan.sampleWeight(1);
    }

    private static WheelTemporalSamplingPlanner.Plan planForDegreesPerFrame(
            WheelTemporalSamplingPlanner planner,
            double degreesPerFrame) {
        double radiansPerSecond = Math.toRadians(degreesPerFrame)
                / FRAME_120_HZ;
        return planner.plan(
                radiansPerSecond,
                FRAME_120_HZ,
                FRAME_120_HZ,
                100.0
        );
    }

    private static void assertFinitePlan(
            WheelTemporalSamplingPlanner.Plan plan) {
        assertTrue(Double.isFinite(plan.angularVelocityRadiansPerSecond()));
        assertTrue(Double.isFinite(plan.presentedAxleDeltaRadians()));
        assertTrue(Double.isFinite(plan.requestedExposureSeconds()));
        assertTrue(Double.isFinite(plan.effectiveExposureSeconds()));
        assertTrue(Double.isFinite(plan.presentationFrameIntervalSeconds()));
        assertTrue(Double.isFinite(plan.projectedRadiusPixels()));
        assertTrue(Double.isFinite(plan.degreesPerFrame()));
        assertTrue(Double.isFinite(plan.grooveCyclesPerFrame()));
        assertTrue(Double.isFinite(plan.projectedSweepPixels()));
        assertTrue(Double.isFinite(plan.rawContinuousBandBlend()));
        assertTrue(Double.isFinite(plan.continuousBandBlend()));
        assertTrue(Double.isFinite(plan.grooveContrast()));
        double sum = 0.0;
        for (int index = 0; index < plan.sampleCount(); index++) {
            assertTrue(Double.isFinite(plan.sampleTimeSeconds(index)));
            assertTrue(Double.isFinite(plan.sampleWeight(index)));
            sum += plan.sampleWeight(index);
        }
        assertEquals(1.0, sum, 1.0e-15);
    }
}
