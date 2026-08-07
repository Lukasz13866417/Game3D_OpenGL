package com.example.game3d_opengl.game.terrain.terrain_structures.levels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.game3d_opengl.game.settings.PortalTestSettings;
import com.example.game3d_opengl.game.terrain.track_elements.portal.PortalConfig;
import com.example.game3d_opengl.game.util.GameRandom;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Random;

public class GameplayLevelFactoryTest {
    @After
    public void resetPortalTestMode() {
        PortalTestSettings.setTestPortalEnabled(false);
    }

    @Test
    public void early_random_levels_do_not_enable_portal_sections() throws Exception {
        setGameRandomSeed(7L);

        for (int levelIndex = 0; levelIndex < PortalConfig.PORTAL_UNLOCK_LEVEL_INDEX; ++levelIndex) {
            assertEquals(
                    "Early random level " + levelIndex + " should not enable portal sections.",
                    -1,
                    GameplayLevelFactory.selectEnabledPortalSectionIndexForLevel(levelIndex)
            );
        }
    }

    @Test
    public void unlocked_random_levels_enable_at_most_one_portal_section() throws Exception {
        setGameRandomSeed(17L);

        for (int i = 0; i < 64; ++i) {
            int levelIndex = PortalConfig.PORTAL_UNLOCK_LEVEL_INDEX + i;
            int enabledPortalSection = GameplayLevelFactory.selectEnabledPortalSectionIndexForLevel(levelIndex);
            assertTrue(
                    "Level " + levelIndex + " should enable at most one portal section.",
                    enabledPortalSection >= -1 && enabledPortalSection < 2
            );
        }
    }

    @Test
    public void unlocked_random_levels_can_still_emit_portal_encounters() throws Exception {
        setGameRandomSeed(29L);

        boolean foundPortalEncounter = false;
        for (int i = 0; i < 64; ++i) {
            int levelIndex = PortalConfig.PORTAL_UNLOCK_LEVEL_INDEX + i;
            if (GameplayLevelFactory.selectEnabledPortalSectionIndexForLevel(levelIndex) >= 0) {
                foundPortalEncounter = true;
                break;
            }
        }

        assertTrue("Unlocked levels should still occasionally enable a portal encounter.", foundPortalEncounter);
    }

    @Test
    public void test_portal_mode_enables_portal_sections_even_for_early_levels() throws Exception {
        setGameRandomSeed(5L);
        PortalTestSettings.setTestPortalEnabled(true);

        int enabledPortalSection = GameplayLevelFactory.selectEnabledPortalSectionIndexForLevel(0);
        assertTrue(
                "Test portal mode should force a portal section even for early levels.",
                enabledPortalSection >= 0 && enabledPortalSection < 2
        );
    }

    private static void setGameRandomSeed(long seed) throws ReflectiveOperationException {
        Field randomField = GameRandom.class.getDeclaredField("RANDOM");
        randomField.setAccessible(true);
        randomField.set(null, new Random(seed));
    }
}
