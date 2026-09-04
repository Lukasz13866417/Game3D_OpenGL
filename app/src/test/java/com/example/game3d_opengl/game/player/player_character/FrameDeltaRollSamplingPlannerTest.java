package com.example.game3d_opengl.game.player.player_character;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FrameDeltaRollSamplingPlannerTest {
    private static final double EPSILON = 1.0e-15;

    @Test
    public void deltaWithinMaximumUsesExactCurrentPoseWithoutBlur() {
        FrameDeltaRollSamplingPlanner.Plan plan =
                new FrameDeltaRollSamplingPlanner().plan(0.2, 0.25);

        assertEquals(1L, plan.idealPartCount());
        assertEquals(1, plan.sampleCount());
        assertEquals(0.0, plan.sampleAngleOffsetRadians(0), 0.0);
        assertEquals(1.0, plan.sampleWeight(0), 0.0);
        assertEquals(0.2, plan.actualPartAngleRadians(), 0.0);
        assertFalse(plan.sampleBudgetExceeded());
        assertFalse(plan.hadInvalidInput());
    }

    @Test
    public void deltaExactlyAtMaximumStillUsesExactCurrentPose() {
        FrameDeltaRollSamplingPlanner.Plan plan =
                new FrameDeltaRollSamplingPlanner().plan(0.25, 0.25);

        assertEquals(1L, plan.idealPartCount());
        assertEquals(1, plan.sampleCount());
        assertEquals(0.0, plan.sampleAngleOffsetRadians(0), 0.0);
        assertEquals(1.0, plan.sampleWeight(0), 0.0);
    }

    @Test
    public void crossingThresholdBeginsMidpointIntegration() {
        FrameDeltaRollSamplingPlanner.Plan plan =
                new FrameDeltaRollSamplingPlanner().plan(0.2501, 0.25);

        assertEquals(2L, plan.idealPartCount());
        assertEquals(2, plan.sampleCount());
        assertEquals(-0.2501 * 0.75,
                plan.sampleAngleOffsetRadians(0), EPSILON);
        assertEquals(-0.2501 * 0.25,
                plan.sampleAngleOffsetRadians(1), EPSILON);
        assertUniformNormalizedWeights(plan);
    }

    @Test
    public void positiveDeltaUsesEqualMidpointsFromPreviousTowardCurrent() {
        FrameDeltaRollSamplingPlanner.Plan plan =
                new FrameDeltaRollSamplingPlanner().plan(1.0, 0.25);

        assertEquals(4L, plan.idealPartCount());
        assertEquals(4, plan.sampleCount());
        assertEquals(-0.875, plan.sampleAngleOffsetRadians(0), EPSILON);
        assertEquals(-0.625, plan.sampleAngleOffsetRadians(1), EPSILON);
        assertEquals(-0.375, plan.sampleAngleOffsetRadians(2), EPSILON);
        assertEquals(-0.125, plan.sampleAngleOffsetRadians(3), EPSILON);
        assertEquals(0.25, plan.actualPartAngleRadians(), 0.0);
        assertUniformNormalizedWeights(plan);
    }

    @Test
    public void negativeDeltaMirrorsOnlyTheRollOffsets() {
        FrameDeltaRollSamplingPlanner.Plan positive =
                new FrameDeltaRollSamplingPlanner().plan(1.0, 0.3);
        FrameDeltaRollSamplingPlanner.Plan negative =
                new FrameDeltaRollSamplingPlanner().plan(-1.0, 0.3);

        assertEquals(positive.sampleCount(), negative.sampleCount());
        for (int index = 0; index < positive.sampleCount(); index++) {
            assertEquals(
                    -positive.sampleAngleOffsetRadians(index),
                    negative.sampleAngleOffsetRadians(index),
                    EPSILON
            );
            assertEquals(
                    positive.sampleWeight(index),
                    negative.sampleWeight(index),
                    0.0
            );
        }
    }

    @Test
    public void nonMultipleRoundsUpAndHonorsRequestedMaximum() {
        FrameDeltaRollSamplingPlanner.Plan plan =
                new FrameDeltaRollSamplingPlanner().plan(1.0, 0.3);

        assertEquals(4L, plan.idealPartCount());
        assertEquals(4, plan.sampleCount());
        assertTrue(plan.actualPartAngleRadians() <= 0.3);
    }

    @Test
    public void zeroDeltaStillProducesOneStableSample() {
        FrameDeltaRollSamplingPlanner.Plan plan =
                new FrameDeltaRollSamplingPlanner().plan(0.0, 0.1);

        assertEquals(1L, plan.idealPartCount());
        assertEquals(1, plan.sampleCount());
        assertEquals(0.0, plan.sampleAngleOffsetRadians(0), 0.0);
        assertEquals(1.0, plan.sampleWeight(0), 0.0);
        assertEquals(0.0, plan.actualPartAngleRadians(), 0.0);
    }

    @Test
    public void excessiveSplitCountIsVisibleAndCappedToRendererBudget() {
        FrameDeltaRollSamplingPlanner.Plan plan =
                new FrameDeltaRollSamplingPlanner().plan(4.0, 0.01);

        assertEquals(400L, plan.idealPartCount());
        assertEquals(
                WheelTemporalSamplingPlanner.MAX_TEMPORAL_SAMPLES,
                FrameDeltaRollSamplingPlanner.MAX_ROLL_SAMPLES
        );
        assertEquals(
                FrameDeltaRollSamplingPlanner.MAX_ROLL_SAMPLES,
                plan.sampleCount()
        );
        assertTrue(plan.sampleBudgetExceeded());
        assertTrue(plan.actualPartAngleRadians() > plan.maximumPartAngleRadians());
        assertUniformNormalizedWeights(plan);
    }

    @Test
    public void finiteInputsWhoseRatioOverflowsSaturateWithoutInvalidSamples() {
        FrameDeltaRollSamplingPlanner.Plan plan =
                new FrameDeltaRollSamplingPlanner().plan(
                        Double.MAX_VALUE,
                        Double.MIN_VALUE
                );

        assertEquals(Long.MAX_VALUE, plan.idealPartCount());
        assertEquals(
                FrameDeltaRollSamplingPlanner.MAX_ROLL_SAMPLES,
                plan.sampleCount()
        );
        assertTrue(plan.sampleBudgetExceeded());
        assertFalse(plan.hadInvalidInput());
        for (int index = 0; index < plan.sampleCount(); index++) {
            assertTrue(Double.isFinite(plan.sampleAngleOffsetRadians(index)));
            assertTrue(Double.isFinite(plan.sampleWeight(index)));
        }
        assertUniformNormalizedWeights(plan);
    }

    @Test
    public void invalidInputsFallBackToOneStationarySample() {
        double[][] invalidInputs = new double[][]{
                {Double.NaN, 0.1},
                {Double.POSITIVE_INFINITY, 0.1},
                {Double.NEGATIVE_INFINITY, 0.1},
                {1.0, Double.NaN},
                {1.0, Double.POSITIVE_INFINITY},
                {1.0, Double.NEGATIVE_INFINITY},
                {1.0, 0.0},
                {1.0, -0.1}
        };

        FrameDeltaRollSamplingPlanner planner =
                new FrameDeltaRollSamplingPlanner();
        for (double[] input : invalidInputs) {
            FrameDeltaRollSamplingPlanner.Plan plan =
                    planner.plan(input[0], input[1]);
            assertTrue(plan.hadInvalidInput());
            assertEquals(1L, plan.idealPartCount());
            assertEquals(1, plan.sampleCount());
            assertEquals(0.0, plan.signedPresentedRollDeltaRadians(), 0.0);
            assertEquals(0.0, plan.maximumPartAngleRadians(), 0.0);
            assertEquals(0.0, plan.sampleAngleOffsetRadians(0), 0.0);
            assertEquals(1.0, plan.sampleWeight(0), 0.0);
        }
    }

    @Test
    public void resultStorageIsReusedAndInactiveIndicesAreRejected() {
        FrameDeltaRollSamplingPlanner planner =
                new FrameDeltaRollSamplingPlanner();
        FrameDeltaRollSamplingPlanner.Plan first = planner.plan(1.0, 0.25);
        FrameDeltaRollSamplingPlanner.Plan second = planner.plan(0.0, 0.25);

        assertSame(first, second);
        try {
            second.sampleWeight(1);
            fail("Expected an inactive sample index to be rejected");
        } catch (IndexOutOfBoundsException expected) {
            assertTrue(expected.getMessage().contains("outside"));
        }
    }

    private static void assertUniformNormalizedWeights(
            FrameDeltaRollSamplingPlanner.Plan plan) {
        double sum = 0.0;
        for (int index = 0; index < plan.sampleCount(); index++) {
            assertEquals(
                    1.0 / plan.sampleCount(),
                    plan.sampleWeight(index),
                    EPSILON
            );
            sum += plan.sampleWeight(index);
        }
        assertEquals(1.0, sum, EPSILON);
    }
}
