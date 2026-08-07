package com.example.game3d_opengl.game.player.player_character;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlayerTurnVisualEffectTest {
    private static final float FRAME_120_HZ_MILLIS = 1_000f / 120f;
    private static final float EPSILON = 1e-5f;

    @Test
    public void yawRateResponseIsFourTimesMoreSensitiveThanFormerTuning() {
        assertEquals(
                240f / 4f,
                PlayerTurnVisualEffect.FULL_YAW_RATE_DEGREES_PER_SECOND,
                EPSILON
        );
        assertEquals(
                3f / 4f,
                PlayerTurnVisualEffect.MIN_ACTIVE_YAW_RATE_DEGREES_PER_SECOND,
                EPSILON
        );
        assertEquals(
                0.75f / 4f,
                PlayerTurnVisualEffect.MEANINGFUL_YAW_CHANGE_DEGREES,
                EPSILON
        );
        assertEquals(
                1.5f / 4f,
                PlayerTurnVisualEffect.REVERSAL_YAW_CHANGE_DEGREES,
                EPSILON
        );
    }

    @Test
    public void firstSampleOnlyEstablishesTheAngleBaseline() {
        PlayerTurnVisualEffect effect = new PlayerTurnVisualEffect();
        effect.beginHold(Math.toRadians(73.0));

        effect.update(Math.toRadians(73.0), FRAME_120_HZ_MILLIS);

        assertEquals(0f, effect.yawOffsetDegrees(), EPSILON);
    }

    @Test
    public void strengthComesFromYawRateAndUsesModelYawSign() {
        PlayerTurnVisualEffect positiveTurn = new PlayerTurnVisualEffect();
        PlayerTurnVisualEffect negativeTurn = new PlayerTurnVisualEffect();
        positiveTurn.beginHold(0.0);
        negativeTurn.beginHold(0.0);
        double fullRateFrameRadians = Math.toRadians(
                PlayerTurnVisualEffect.FULL_YAW_RATE_DEGREES_PER_SECOND
                        * FRAME_120_HZ_MILLIS / 1000f
        );

        positiveTurn.update(
                fullRateFrameRadians,
                FRAME_120_HZ_MILLIS
        );
        negativeTurn.update(
                -fullRateFrameRadians,
                FRAME_120_HZ_MILLIS
        );

        assertTrue(positiveTurn.yawOffsetDegrees() < 0f);
        assertTrue(negativeTurn.yawOffsetDegrees() > 0f);
        assertEquals(
                -positiveTurn.yawOffsetDegrees(),
                negativeTurn.yawOffsetDegrees(),
                EPSILON
        );
        assertTrue(Math.abs(positiveTurn.yawOffsetDegrees())
                < PlayerTurnVisualEffect.MAX_YAW_DEGREES);
    }

    @Test
    public void equalAngleChangesGiveEqualResultsAtDifferentAbsoluteAngles() {
        PlayerTurnVisualEffect nearZero = new PlayerTurnVisualEffect();
        PlayerTurnVisualEffect farFromZero = new PlayerTurnVisualEffect();
        double deltaRadians = Math.toRadians(1.5);
        nearZero.beginHold(0.0);
        farFromZero.beginHold(Math.toRadians(137.0));

        nearZero.update(deltaRadians, FRAME_120_HZ_MILLIS);
        farFromZero.update(
                Math.toRadians(137.0) + deltaRadians,
                FRAME_120_HZ_MILLIS
        );

        assertEquals(
                nearZero.yawOffsetDegrees(),
                farFromZero.yawOffsetDegrees(),
                EPSILON
        );
    }

    @Test
    public void fixedAngleWaitsThenFadesInsteadOfAccumulating() {
        PlayerTurnVisualEffect effect = drivenEffect(1.0, 16);
        float atStop = effect.yawOffsetDegrees();

        effect.update(
                Math.toRadians(16.0),
                PlayerTurnVisualEffect.RETURN_DELAY_MILLIS
        );
        float afterHold = effect.yawOffsetDegrees();
        assertEquals(atStop, afterHold, EPSILON);

        effect.update(Math.toRadians(16.0), 180f);
        float duringFade = Math.abs(effect.yawOffsetDegrees());
        assertTrue(duringFade < Math.abs(afterHold));

        effect.update(Math.toRadians(16.0), 1_000f);
        assertEquals(0f, effect.yawOffsetDegrees(), EPSILON);
    }

    @Test
    public void delayLatchesVisiblePoseInsteadOfSmallFinalRateTarget() {
        PlayerTurnVisualEffect effect = drivenEffect(2.0, 24);
        double stoppedYawDegrees = 48.0
                + PlayerTurnVisualEffect.MIN_ACTIVE_YAW_RATE_DEGREES_PER_SECOND
                * 1.1
                * FRAME_120_HZ_MILLIS
                / 1_000.0;

        // A small final interpolation tail creates a near-neutral rate-derived target.
        effect.update(
                Math.toRadians(stoppedYawDegrees),
                FRAME_120_HZ_MILLIS
        );
        float visibleWhenYawStops = effect.yawOffsetDegrees();
        assertTrue(Math.abs(visibleWhenYawStops) > 1f);

        effect.update(
                Math.toRadians(stoppedYawDegrees),
                PlayerTurnVisualEffect.RETURN_DELAY_MILLIS * 0.75f
        );

        assertEquals(
                visibleWhenYawStops,
                effect.yawOffsetDegrees(),
                EPSILON
        );
    }

    @Test
    public void sameDirectionDecelerationCannotCommandAnInwardTarget() {
        PlayerTurnVisualEffect effect = drivenEffect(2.0, 24);
        double yawDegrees = 48.0;
        float beforeDeceleration = Math.abs(effect.yawOffsetDegrees());
        double[] tailRatesDegreesPerSecond = {
                120.0, 80.0, 40.0, 20.0, 10.0, 5.0
        };

        for (double rate : tailRatesDegreesPerSecond) {
            yawDegrees += rate * FRAME_120_HZ_MILLIS / 1_000.0;
            effect.update(Math.toRadians(yawDegrees), FRAME_120_HZ_MILLIS);
        }

        float whenMotionStops = effect.yawOffsetDegrees();
        assertTrue(Math.abs(whenMotionStops) >= beforeDeceleration);
        effect.update(
                Math.toRadians(yawDegrees),
                PlayerTurnVisualEffect.RETURN_DELAY_MILLIS * 0.75f
        );
        assertEquals(whenMotionStops, effect.yawOffsetDegrees(), EPSILON);
    }

    @Test
    public void alternatingOnePixelTouchJitterCannotBypassDelay() {
        PlayerTurnVisualEffect effect = drivenEffect(2.0, 24);
        double yawDegrees = 48.0;

        effect.update(
                Math.toRadians(yawDegrees),
                PlayerTurnVisualEffect.QUIET_CONFIRM_MILLIS
        );
        float latchedOffset = effect.yawOffsetDegrees();
        double onePixelAtDefaultSensitivityDegrees = 240.0 * 0.2 / 1_080.0;
        int jitterFrames = Math.max(
                1,
                (int) (PlayerTurnVisualEffect.RETURN_DELAY_MILLIS
                        * 0.75f / FRAME_120_HZ_MILLIS)
        );
        for (int frame = 0; frame < jitterFrames; frame++) {
            yawDegrees += frame % 2 == 0
                    ? onePixelAtDefaultSensitivityDegrees
                    : -onePixelAtDefaultSensitivityDegrees;
            effect.update(Math.toRadians(yawDegrees), FRAME_120_HZ_MILLIS);
        }

        assertEquals(latchedOffset, effect.yawOffsetDegrees(), EPSILON);
    }

    @Test
    public void resumedTurnDuringReturnUsesFreshStrength() {
        PlayerTurnVisualEffect effect = drivenEffect(2.0, 48);
        double yawDegrees = 96.0;

        effect.update(
                Math.toRadians(yawDegrees),
                PlayerTurnVisualEffect.QUIET_CONFIRM_MILLIS
        );
        effect.update(
                Math.toRadians(yawDegrees),
                PlayerTurnVisualEffect.RETURN_DELAY_MILLIS
                        - PlayerTurnVisualEffect.QUIET_CONFIRM_MILLIS
                        + 100f
        );
        float duringReturn = Math.abs(effect.yawOffsetDegrees());

        yawDegrees += 1.0;
        effect.update(Math.toRadians(yawDegrees), 100f);

        assertTrue(Math.abs(effect.yawOffsetDegrees()) < duringReturn);
    }

    @Test
    public void oppositeTurnTransitionsSmoothlyThroughNeutral() {
        PlayerTurnVisualEffect effect = drivenEffect(2.0, 8);
        float beforeReversal = effect.yawOffsetDegrees();
        assertTrue(beforeReversal < 0f);
        double yawDegrees = 16.0;

        yawDegrees -= 2.0;
        effect.update(Math.toRadians(yawDegrees), FRAME_120_HZ_MILLIS);
        float firstReversalFrame = effect.yawOffsetDegrees();

        assertTrue(firstReversalFrame > beforeReversal);
        assertTrue(firstReversalFrame < 0f);
        for (int frame = 0; frame < 8; frame++) {
            yawDegrees -= 2.0;
            effect.update(
                    Math.toRadians(yawDegrees),
                    FRAME_120_HZ_MILLIS
            );
        }

        assertTrue(effect.yawOffsetDegrees() > 0f);
        assertTrue(effect.yawOffsetDegrees()
                <= PlayerTurnVisualEffect.MAX_YAW_DEGREES);
    }

    @Test
    public void sameContinuousTurnIsRenderRateIndependent() {
        PlayerTurnVisualEffect at120Hz = new PlayerTurnVisualEffect();
        PlayerTurnVisualEffect at60Hz = new PlayerTurnVisualEffect();
        simulateConstantYawRate(at120Hz, 180.0, 400f, 1_000f / 120f);
        simulateConstantYawRate(at60Hz, 180.0, 400f, 1_000f / 60f);

        assertEquals(
                at120Hz.yawOffsetDegrees(),
                at60Hz.yawOffsetDegrees(),
                2e-4f
        );
    }

    @Test
    public void slowContinuousTurnIsRenderRateIndependent() {
        PlayerTurnVisualEffect at240Hz = new PlayerTurnVisualEffect();
        PlayerTurnVisualEffect at120Hz = new PlayerTurnVisualEffect();
        PlayerTurnVisualEffect at60Hz = new PlayerTurnVisualEffect();
        simulateConstantYawRate(at240Hz, 60.0, 400f, 1_000f / 240f);
        simulateConstantYawRate(at120Hz, 60.0, 400f, 1_000f / 120f);
        simulateConstantYawRate(at60Hz, 60.0, 400f, 1_000f / 60f);

        assertEquals(
                at120Hz.yawOffsetDegrees(),
                at240Hz.yawOffsetDegrees(),
                2e-3f
        );
        assertEquals(
                at120Hz.yawOffsetDegrees(),
                at60Hz.yawOffsetDegrees(),
                2e-3f
        );
    }

    @Test
    public void duplicateRenderOfCanonicalTickDoesNotLookLikeStoppedYaw() {
        PlayerTurnVisualEffect at120Hz = new PlayerTurnVisualEffect();
        PlayerTurnVisualEffect at240Hz = new PlayerTurnVisualEffect();
        simulateCanonical120HzTurn(at120Hz, 60.0, false);
        simulateCanonical120HzTurn(at240Hz, 60.0, true);

        assertEquals(
                at120Hz.yawOffsetDegrees(),
                at240Hz.yawOffsetDegrees(),
                2e-3f
        );
    }

    @Test
    public void wrapBoundaryUsesTheShortestAngleChange() {
        PlayerTurnVisualEffect positiveAcrossWrap =
                new PlayerTurnVisualEffect();
        positiveAcrossWrap.beginHold(Math.toRadians(179.0));
        positiveAcrossWrap.update(
                Math.toRadians(-179.0),
                FRAME_120_HZ_MILLIS
        );
        assertTrue(positiveAcrossWrap.yawOffsetDegrees() < 0f);

        PlayerTurnVisualEffect negativeAcrossWrap =
                new PlayerTurnVisualEffect();
        negativeAcrossWrap.beginHold(Math.toRadians(-179.0));
        negativeAcrossWrap.update(
                Math.toRadians(179.0),
                FRAME_120_HZ_MILLIS
        );
        assertTrue(negativeAcrossWrap.yawOffsetDegrees() > 0f);
        assertEquals(
                -positiveAcrossWrap.yawOffsetDegrees(),
                negativeAcrossWrap.yawOffsetDegrees(),
                EPSILON
        );
    }

    @Test
    public void extremeRateAndDroppedFrameRemainFiniteAndClamped() {
        PlayerTurnVisualEffect effect = new PlayerTurnVisualEffect();
        effect.beginHold(0.0);
        effect.update(Math.toRadians(170.0), 0.01f);
        effect.update(Math.toRadians(171.0), 90f);

        assertTrue(Float.isFinite(effect.yawOffsetDegrees()));
        assertTrue(Math.abs(effect.yawOffsetDegrees())
                <= PlayerTurnVisualEffect.MAX_YAW_DEGREES);
    }

    @Test
    public void zeroElapsedSampleDoesNotLoseTheNextAngleChange() {
        PlayerTurnVisualEffect effect = new PlayerTurnVisualEffect();
        effect.beginHold(0.0);

        effect.update(Math.toRadians(2.0), 0f);
        assertEquals(0f, effect.yawOffsetDegrees(), EPSILON);
        effect.update(Math.toRadians(2.0), FRAME_120_HZ_MILLIS);

        assertTrue(effect.yawOffsetDegrees() < 0f);
    }

    @Test
    public void resetClearsMotionAndMakesNextSampleABaseline() {
        PlayerTurnVisualEffect effect = drivenEffect(2.0, 8);
        assertTrue(Math.abs(effect.yawOffsetDegrees()) > 0f);

        effect.reset();
        assertEquals(0f, effect.yawOffsetDegrees(), EPSILON);
        effect.update(Math.toRadians(-120.0), 500f);

        assertEquals(0f, effect.yawOffsetDegrees(), EPSILON);
    }

    @Test
    public void releaseReturnsWithoutDelayAndYawChangesStayInactive() {
        PlayerTurnVisualEffect effect = drivenEffect(2.0, 8);
        float offsetAtRelease = effect.yawOffsetDegrees();
        assertTrue(Math.abs(offsetAtRelease) > 0f);

        effect.endHold();
        assertEquals(offsetAtRelease, effect.yawOffsetDegrees(), EPSILON);

        effect.update(Math.toRadians(90.0), 80f);
        assertTrue(Math.abs(effect.yawOffsetDegrees())
                < Math.abs(offsetAtRelease));
        assertTrue(Math.abs(effect.yawOffsetDegrees()) > 0f);
        effect.update(Math.toRadians(90.0), 1_000f);
        assertEquals(0f, effect.yawOffsetDegrees(), EPSILON);

        effect.beginHold(Math.toRadians(90.0));
        effect.update(Math.toRadians(90.0), FRAME_120_HZ_MILLIS);
        assertEquals(0f, effect.yawOffsetDegrees(), EPSILON);
    }

    @Test
    public void newHoldClearsAnInProgressReleaseReturn() {
        PlayerTurnVisualEffect effect = drivenEffect(2.0, 8);
        effect.endHold();
        effect.update(Math.toRadians(16.0), 40f);
        assertTrue(Math.abs(effect.yawOffsetDegrees()) > 0f);

        effect.beginHold(Math.toRadians(16.0));

        assertEquals(0f, effect.yawOffsetDegrees(), EPSILON);
        effect.update(Math.toRadians(16.0), FRAME_120_HZ_MILLIS);
        assertEquals(0f, effect.yawOffsetDegrees(), EPSILON);
    }

    private static PlayerTurnVisualEffect drivenEffect(
            double degreesPerFrame,
            int frames) {
        PlayerTurnVisualEffect effect = new PlayerTurnVisualEffect();
        effect.beginHold(0.0);
        double yawDegrees = 0.0;
        for (int frame = 0; frame < frames; frame++) {
            yawDegrees += degreesPerFrame;
            effect.update(
                    Math.toRadians(yawDegrees),
                    FRAME_120_HZ_MILLIS
            );
        }
        return effect;
    }

    private static void simulateConstantYawRate(
            PlayerTurnVisualEffect effect,
            double rateDegreesPerSecond,
            float totalMillis,
            float stepMillis) {
        effect.beginHold(0.0);
        double yawDegrees = 0.0;
        float elapsedMillis = 0f;
        while (elapsedMillis < totalMillis) {
            float dtMillis =
                    Math.min(stepMillis, totalMillis - elapsedMillis);
            yawDegrees += rateDegreesPerSecond * dtMillis / 1000.0;
            effect.update(Math.toRadians(yawDegrees), dtMillis);
            elapsedMillis += dtMillis;
        }
    }

    private static void simulateCanonical120HzTurn(
            PlayerTurnVisualEffect effect,
            double rateDegreesPerSecond,
            boolean renderTwicePerTick) {
        long sampleTimeNanos = 0L;
        double yawDegrees = 0.0;
        effect.beginHold(0.0, sampleTimeNanos);
        for (int tick = 0; tick < 48; tick++) {
            sampleTimeNanos += 1_000_000_000L / 120L;
            yawDegrees += rateDegreesPerSecond / 120.0;
            if (renderTwicePerTick) {
                effect.update(
                        Math.toRadians(yawDegrees),
                        sampleTimeNanos,
                        1_000f / 240f
                );
                effect.update(
                        Math.toRadians(yawDegrees),
                        sampleTimeNanos,
                        1_000f / 240f
                );
            } else {
                effect.update(
                        Math.toRadians(yawDegrees),
                        sampleTimeNanos,
                        FRAME_120_HZ_MILLIS
                );
            }
        }
    }
}
