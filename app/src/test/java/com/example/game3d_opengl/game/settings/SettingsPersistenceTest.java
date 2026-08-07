package com.example.game3d_opengl.game.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

/**
 * Verifies that each user-facing setting loads from and writes to shared storage.
 */
public class SettingsPersistenceTest {
    @After
    public void tearDown() {
        GameSettingsPersistence.clearForTests();
    }

    @Test
    public void initializeForTests_loads_persisted_setting_values() {
        InMemoryBackend backend = new InMemoryBackend();
        backend.putFloat(TouchSensitivitySettings.HORIZONTAL_STORAGE_KEY, 0.35f);
        backend.putFloat(TouchSensitivitySettings.VERTICAL_STORAGE_KEY, 0.85f);
        backend.putBoolean(PortalTestSettings.STORAGE_KEY, true);
        backend.putBoolean(SlowFrameStatsSettings.STORAGE_KEY, true);

        GameSettingsPersistence.initializeForTests(backend);

        assertEquals(0.35f, TouchSensitivitySettings.getHorizontalSensitivity(), 1e-6f);
        assertEquals(0.85f, TouchSensitivitySettings.getVerticalSensitivity(), 1e-6f);
        assertTrue(PortalTestSettings.isTestPortalEnabled());
        assertTrue(SlowFrameStatsSettings.isCaptureEnabled());
    }

    @Test
    public void setting_mutators_write_through_to_persistence_backend() {
        InMemoryBackend backend = new InMemoryBackend();
        GameSettingsPersistence.initializeForTests(backend);

        TouchSensitivitySettings.setHorizontalSensitivity(0.25f);
        TouchSensitivitySettings.setVerticalSensitivity(0.75f);
        PortalTestSettings.setTestPortalEnabled(true);
        SlowFrameStatsSettings.setCaptureEnabled(true);

        assertEquals(
                0.25f,
                backend.getStoredFloat(TouchSensitivitySettings.HORIZONTAL_STORAGE_KEY),
                1e-6f
        );
        assertEquals(
                0.75f,
                backend.getStoredFloat(TouchSensitivitySettings.VERTICAL_STORAGE_KEY),
                1e-6f
        );
        assertTrue(backend.getStoredBoolean(PortalTestSettings.STORAGE_KEY));
        assertTrue(backend.getStoredBoolean(SlowFrameStatsSettings.STORAGE_KEY));
    }

    /**
     * Keeps test preference values in memory without an Android Context.
     */
    private static final class InMemoryBackend implements SettingsStorage.Backend {
        private final Map<String, Float> floats = new HashMap<>();
        private final Map<String, Boolean> booleans = new HashMap<>();

        @Override
        public float getFloat(String key, float defaultValue) {
            Float value = floats.get(key);
            return value != null ? value : defaultValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            Boolean value = booleans.get(key);
            return value != null ? value : defaultValue;
        }

        @Override
        public void putFloat(String key, float value) {
            floats.put(key, value);
        }

        @Override
        public void putBoolean(String key, boolean value) {
            booleans.put(key, value);
        }

        private float getStoredFloat(String key) {
            Float value = floats.get(key);
            if (value == null) {
                throw new AssertionError("Missing float for key: " + key);
            }
            return value;
        }

        private boolean getStoredBoolean(String key) {
            Boolean value = booleans.get(key);
            if (value == null) {
                throw new AssertionError("Missing boolean for key: " + key);
            }
            return value;
        }
    }
}
