package com.example.game3d_opengl.game.settings;

public final class SlowFrameStatsSettings {
    public static final boolean DEFAULT_CAPTURE_ENABLED = false;
    static final String STORAGE_KEY = "slow_frame_stats_capture_enabled";

    private static volatile boolean captureEnabled = DEFAULT_CAPTURE_ENABLED;

    private SlowFrameStatsSettings() {}

    public static boolean isCaptureEnabled() {
        return captureEnabled;
    }

    public static void setCaptureEnabled(boolean enabled) {
        captureEnabled = enabled;
        SettingsStorage.putBoolean(STORAGE_KEY, enabled);
    }

    public static void toggleCaptureEnabled() {
        setCaptureEnabled(!captureEnabled);
    }

    static void reloadFromPersistence() {
        captureEnabled = SettingsStorage.getBoolean(STORAGE_KEY, DEFAULT_CAPTURE_ENABLED);
    }
}
