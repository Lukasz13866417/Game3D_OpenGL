package com.example.game3d_opengl.game.player.player_character;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.game3d_opengl.rendering.BloomConfig;

import org.junit.Test;

public class WheelBloomEnergyTest {
    private static final float EPSILON = 1.0e-6f;

    @Test
    public void residualSuppliesOnlyBloomMissingFromOrdinaryPrefilter() {
        float factor = WheelBloomEnergy.brightPassFactor(
                0.26f, 0.98f, 0.72f, BloomConfig.BRIGHT_THRESHOLD);
        float[] coverages = new float[]{0f, 0.1f, 0.35f, 0.7f, 1f};
        float[] ordinaryValues = new float[]{0f, 0.04f, 0.3f, 1f};

        for (float coverage : coverages) {
            float target = coverage * 0.98f * factor;
            for (float ordinary : ordinaryValues) {
                float residual = WheelBloomEnergy.residual(
                        coverage * 0.98f,
                        factor,
                        ordinary);
                assertTrue(Float.isFinite(residual));
                assertTrue(residual >= 0f);
                assertEquals(
                        Math.max(ordinary, target),
                        ordinary + residual,
                        EPSILON);
            }
        }
    }

    @Test
    public void absentExposureAndCompleteOrdinaryBloomNeedNoTopUp() {
        float factor = WheelBloomEnergy.brightPassFactor(
                0.26f, 0.98f, 0.72f, BloomConfig.BRIGHT_THRESHOLD);
        float fullTarget = 0.98f * factor;

        assertEquals(0f, WheelBloomEnergy.residual(0f, factor, 0f), 0f);
        assertEquals(
                0f,
                WheelBloomEnergy.residual(0.98f, factor, fullTarget),
                EPSILON);
        assertEquals(
                0f,
                WheelBloomEnergy.residual(0.98f, factor, fullTarget + 0.1f),
                EPSILON);
    }
}
