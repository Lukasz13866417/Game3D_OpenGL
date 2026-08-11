package com.example.game3d_opengl.game.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.game3d.core.simulation.PhysicsConfig;

import org.junit.After;
import org.junit.Test;

public class TouchSensitivitySettingsTest {
    private static final float EPSILON = 1e-6f;

    @After
    public void resetDefaultSensitivity() {
        GameSettingsPersistence.clearForTests();
    }

    @Test
    public void horizontalAndVerticalSettingsClampIndependently() {
        TouchSensitivitySettings.setHorizontalSensitivity(-10f);
        TouchSensitivitySettings.setVerticalSensitivity(10f);

        assertEquals(
                TouchSensitivitySettings.MIN_SENSITIVITY,
                TouchSensitivitySettings.getHorizontalSensitivity(),
                EPSILON
        );
        assertEquals(
                TouchSensitivitySettings.MAX_SENSITIVITY,
                TouchSensitivitySettings.getVerticalSensitivity(),
                EPSILON
        );
    }

    @Test
    public void normalizedConversionRoundTripsDefaultValue() {
        float normalized = TouchSensitivitySettings.toNormalized(
                TouchSensitivitySettings.DEFAULT_SENSITIVITY
        );
        float restored = TouchSensitivitySettings.fromNormalized(normalized);

        assertEquals(TouchSensitivitySettings.DEFAULT_SENSITIVITY, restored, EPSILON);
    }

    @Test
    public void defaultDisplayValueMapsToRequestedLegacyAxisScales() {
        assertEquals(
                0.5f,
                TouchSensitivitySettings.getHorizontalSensitivity(),
                EPSILON
        );
        assertEquals(
                0.5f,
                TouchSensitivitySettings.getVerticalSensitivity(),
                EPSILON
        );
        assertEquals(
                0.20f,
                TouchSensitivitySettings.getHorizontalInputScale(),
                EPSILON
        );
        assertEquals(
                0.80f,
                TouchSensitivitySettings.getVerticalInputScale(),
                EPSILON
        );
    }

    @Test
    public void sensitivityEndpointsAreOneFifthAndTwiceTheDefaults() {
        TouchSensitivitySettings.setHorizontalSensitivity(0.1f);
        TouchSensitivitySettings.setVerticalSensitivity(0.1f);
        assertEquals(0.04f, TouchSensitivitySettings.getHorizontalInputScale(), EPSILON);
        assertEquals(0.16f, TouchSensitivitySettings.getVerticalInputScale(), EPSILON);

        TouchSensitivitySettings.setHorizontalSensitivity(1.0f);
        TouchSensitivitySettings.setVerticalSensitivity(1.0f);
        assertEquals(0.40f, TouchSensitivitySettings.getHorizontalInputScale(), EPSILON);
        assertEquals(1.60f, TouchSensitivitySettings.getVerticalInputScale(), EPSILON);
    }

    @Test
    public void defaultJumpThresholdUsesOldPointEightVerticalSensitivity() {
        PhysicsConfig config = new PhysicsConfig();
        double rawSwipeScreenHeights = config.jumpChargeThreshold
                / (config.swipeChargePerScreenHeight
                * TouchSensitivitySettings.DEFAULT_VERTICAL_INPUT_SCALE);

        assertEquals(
                (0.9 / 5.0) / (6.5 * 0.8),
                rawSwipeScreenHeights,
                1.0e-9
        );

        TouchSensitivitySettings.setVerticalSensitivity(
                TouchSensitivitySettings.MIN_SENSITIVITY);
        double leastSensitiveSwipeScreenHeights = config.jumpChargeThreshold
                / (config.swipeChargePerScreenHeight
                * TouchSensitivitySettings.getVerticalInputScale());
        assertTrue(
                "jump must remain achievable at minimum vertical sensitivity",
                leastSensitiveSwipeScreenHeights < 1.0
        );
    }
}
