package com.example.game3d_opengl.game.settings;

import android.content.Context;

/**
 * Connects all game settings to the shared Android preference storage.
 */
public final class GameSettingsPersistence {
    private GameSettingsPersistence() {}

    public static void initialize(Context context) {
        SettingsStorage.initialize(context);
        reloadPersistedValues();
    }

    static void initializeForTests(SettingsStorage.Backend backend) {
        SettingsStorage.installBackendForTests(backend);
        reloadPersistedValues();
    }

    static void clearForTests() {
        SettingsStorage.clearBackendForTests();
        reloadPersistedValues();
    }

    static void reloadPersistedValues() {
        TouchSensitivitySettings.reloadFromPersistence();
        PortalTestSettings.reloadFromPersistence();
        SlowFrameStatsSettings.reloadFromPersistence();
    }
}
