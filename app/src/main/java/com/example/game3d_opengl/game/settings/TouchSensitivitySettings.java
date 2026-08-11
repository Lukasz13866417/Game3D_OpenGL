package com.example.game3d_opengl.game.settings;

/**
 * User-facing touch sensitivity and its conversion to gameplay input scales.
 *
 * <p>The displayed value is deliberately not the raw multiplier used by the simulation. A
 * displayed value of {@value #DEFAULT_SENSITIVITY} is the neutral/default setting for both
 * axes. At that setting horizontal input behaves like the old 0.20 setting, while vertical
 * input behaves like the old 0.80 setting.</p>
 */
public final class TouchSensitivitySettings {
    public static final float MIN_SENSITIVITY = 0.1f;
    public static final float MAX_SENSITIVITY = 1.0f;
    public static final float DEFAULT_SENSITIVITY = 0.5f;

    public static final float DEFAULT_HORIZONTAL_INPUT_SCALE = 0.20f;
    public static final float DEFAULT_VERTICAL_INPUT_SCALE = 0.80f;

    static final String HORIZONTAL_STORAGE_KEY = "horizontal_touch_sensitivity";
    static final String VERTICAL_STORAGE_KEY = "vertical_touch_sensitivity";

    private static final float HORIZONTAL_INPUT_SCALE_PER_DISPLAY_UNIT =
            DEFAULT_HORIZONTAL_INPUT_SCALE / DEFAULT_SENSITIVITY;
    private static final float VERTICAL_INPUT_SCALE_PER_DISPLAY_UNIT =
            DEFAULT_VERTICAL_INPUT_SCALE / DEFAULT_SENSITIVITY;

    private static volatile float horizontalSensitivity = DEFAULT_SENSITIVITY;
    private static volatile float verticalSensitivity = DEFAULT_SENSITIVITY;

    private TouchSensitivitySettings() {}

    /** Returns the displayed horizontal value in the inclusive 0.1..1 range. */
    public static float getHorizontalSensitivity() {
        return horizontalSensitivity;
    }

    /** Returns the displayed vertical value in the inclusive 0.1..1 range. */
    public static float getVerticalSensitivity() {
        return verticalSensitivity;
    }

    /** Returns the multiplier applied to raw horizontal touch pixels. */
    public static float getHorizontalInputScale() {
        return horizontalSensitivity * HORIZONTAL_INPUT_SCALE_PER_DISPLAY_UNIT;
    }

    /** Returns the multiplier applied to raw vertical touch pixels. */
    public static float getVerticalInputScale() {
        return verticalSensitivity * VERTICAL_INPUT_SCALE_PER_DISPLAY_UNIT;
    }

    public static void setHorizontalSensitivity(float sensitivity) {
        horizontalSensitivity = clamp(sensitivity);
        SettingsStorage.putFloat(HORIZONTAL_STORAGE_KEY, horizontalSensitivity);
    }

    public static void setVerticalSensitivity(float sensitivity) {
        verticalSensitivity = clamp(sensitivity);
        SettingsStorage.putFloat(VERTICAL_STORAGE_KEY, verticalSensitivity);
    }

    public static float toNormalized(float sensitivity) {
        float span = MAX_SENSITIVITY - MIN_SENSITIVITY;
        if (span <= 1e-6f) {
            return 0f;
        }
        return clamp01((clamp(sensitivity) - MIN_SENSITIVITY) / span);
    }

    public static float fromNormalized(float normalized) {
        return MIN_SENSITIVITY
                + clamp01(normalized) * (MAX_SENSITIVITY - MIN_SENSITIVITY);
    }

    private static float clamp(float sensitivity) {
        if (!Float.isFinite(sensitivity)) {
            return DEFAULT_SENSITIVITY;
        }
        return Math.max(MIN_SENSITIVITY, Math.min(MAX_SENSITIVITY, sensitivity));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    static void reloadFromPersistence() {
        horizontalSensitivity = clamp(SettingsStorage.getFloat(
                HORIZONTAL_STORAGE_KEY,
                DEFAULT_SENSITIVITY
        ));
        verticalSensitivity = clamp(SettingsStorage.getFloat(
                VERTICAL_STORAGE_KEY,
                DEFAULT_SENSITIVITY
        ));
    }
}
