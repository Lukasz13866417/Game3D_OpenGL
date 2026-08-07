package com.example.game3d_opengl.game.settings;

import android.content.Context;
import android.content.SharedPreferences;

final class SettingsStorage {
    private static final String PREFS_NAME = "game3d_settings";

    interface Backend {
        float getFloat(String key, float defaultValue);

        boolean getBoolean(String key, boolean defaultValue);

        void putFloat(String key, float value);

        void putBoolean(String key, boolean value);
    }

    private static volatile Backend backend;

    private SettingsStorage() {}

    static void initialize(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        backend = new SharedPreferencesBackend(
                (appContext != null ? appContext : context)
                        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        );
    }

    static float getFloat(String key, float defaultValue) {
        Backend activeBackend = backend;
        return activeBackend != null ? activeBackend.getFloat(key, defaultValue) : defaultValue;
    }

    static boolean getBoolean(String key, boolean defaultValue) {
        Backend activeBackend = backend;
        return activeBackend != null ? activeBackend.getBoolean(key, defaultValue) : defaultValue;
    }

    static void putFloat(String key, float value) {
        Backend activeBackend = backend;
        if (activeBackend != null) {
            activeBackend.putFloat(key, value);
        }
    }

    static void putBoolean(String key, boolean value) {
        Backend activeBackend = backend;
        if (activeBackend != null) {
            activeBackend.putBoolean(key, value);
        }
    }

    static void installBackendForTests(Backend testBackend) {
        backend = testBackend;
    }

    static void clearBackendForTests() {
        backend = null;
    }

    private static final class SharedPreferencesBackend implements Backend {
        private final SharedPreferences sharedPreferences;

        private SharedPreferencesBackend(SharedPreferences sharedPreferences) {
            if (sharedPreferences == null) {
                throw new IllegalArgumentException("sharedPreferences == null");
            }
            this.sharedPreferences = sharedPreferences;
        }

        @Override
        public float getFloat(String key, float defaultValue) {
            return sharedPreferences.getFloat(key, defaultValue);
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            return sharedPreferences.getBoolean(key, defaultValue);
        }

        @Override
        public void putFloat(String key, float value) {
            sharedPreferences.edit().putFloat(key, value).apply();
        }

        @Override
        public void putBoolean(String key, boolean value) {
            sharedPreferences.edit().putBoolean(key, value).apply();
        }
    }
}
