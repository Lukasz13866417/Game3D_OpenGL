package com.example.game3d_opengl.game.settings;

public final class PortalTestSettings {
    static final String STORAGE_KEY = "test_portal_enabled";

    private static volatile boolean testPortalEnabled = false;

    private PortalTestSettings() {}

    public static boolean isTestPortalEnabled() {
        return testPortalEnabled;
    }

    public static void setTestPortalEnabled(boolean enabled) {
        testPortalEnabled = enabled;
        SettingsStorage.putBoolean(STORAGE_KEY, enabled);
    }

    public static void toggleTestPortalEnabled() {
        setTestPortalEnabled(!testPortalEnabled);
    }

    static void reloadFromPersistence() {
        testPortalEnabled = SettingsStorage.getBoolean(STORAGE_KEY, false);
    }
}
