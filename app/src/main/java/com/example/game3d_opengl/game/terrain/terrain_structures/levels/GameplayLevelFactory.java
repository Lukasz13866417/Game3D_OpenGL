package com.example.game3d_opengl.game.terrain.terrain_structures.levels;

import com.example.game3d_opengl.game.settings.PortalTestSettings;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial.CurveStairsLevel;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial.IntroEmptyStraight;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial.IntroSparseSpikeStraight;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial.StairsCurveLineLevel;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.late.DoubleCurveBoostLevel;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.late.LongStairArcLevel;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.mid.BoostRampLevel;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.mid.RectCurveSprintLevel;
import com.example.game3d_opengl.game.terrain.track_elements.portal.PortalConfig;
import com.example.game3d_opengl.game.util.GameRandom;

public final class GameplayLevelFactory {
    private static final int INTRO_EMPTY_ROWS = 80;
    private static final int INTRO_SPARSE_SPIKE_ROWS = 80;
    private static final int RANDOM_LEVEL_TEMPLATE_COUNT = 6;
    private static final int PORTAL_SECTION_COUNT_PER_LEVEL = 2;

    private GameplayLevelFactory() {}

    public static void enqueueIntroSegments(Terrain terrain) {
        if (terrain == null) {
            return;
        }
        terrain.enqueueStructure(new IntroEmptyStraight(INTRO_EMPTY_ROWS));
        terrain.enqueueStructure(new IntroSparseSpikeStraight(INTRO_SPARSE_SPIKE_ROWS));
    }

    public static TerrainLevelSequence createRandomLevel() {
        return createRandomLevelInternal(true, true);
    }

    public static TerrainLevelSequence createRandomLevel(int randomLevelIndex) {
        int enabledPortalSection = selectEnabledPortalSectionIndexForLevel(randomLevelIndex);
        boolean enableFirstPortalSection = enabledPortalSection == 0;
        boolean enableSecondPortalSection = enabledPortalSection == 1;
        return createRandomLevelInternal(enableFirstPortalSection, enableSecondPortalSection);
    }

    public static void enqueueRandomLevel(Terrain terrain) {
        if (terrain == null) {
            return;
        }
        terrain.enqueueStructure(createRandomLevel());
    }

    public static void enqueueRandomLevel(Terrain terrain, int randomLevelIndex) {
        if (terrain == null) {
            return;
        }
        terrain.enqueueStructure(createRandomLevel(randomLevelIndex));
    }

    static boolean isPortalEncounterUnlockedForLevel(int randomLevelIndex) {
        if (PortalTestSettings.isTestPortalEnabled()) {
            return true;
        }
        return randomLevelIndex >= PortalConfig.PORTAL_UNLOCK_LEVEL_INDEX;
    }

    static boolean shouldEnablePortalEncounterForLevel(int randomLevelIndex) {
        if (PortalTestSettings.isTestPortalEnabled()) {
            return true;
        }
        if (!isPortalEncounterUnlockedForLevel(randomLevelIndex)) {
            return false;
        }
        return GameRandom.nextFloat() < PortalConfig.PORTAL_LEVEL_CHANCE;
    }

    static int selectEnabledPortalSectionIndexForLevel(int randomLevelIndex) {
        boolean portalEncounterEnabled = shouldEnablePortalEncounterForLevel(randomLevelIndex);
        if (!portalEncounterEnabled
                || PortalConfig.MAX_PORTAL_SECTIONS_PER_LEVEL <= 0
                || PORTAL_SECTION_COUNT_PER_LEVEL <= 0) {
            return -1;
        }
        return GameRandom.randInt(0, PORTAL_SECTION_COUNT_PER_LEVEL - 1);
    }

    private static TerrainLevelSequence createRandomLevelInternal(
            boolean enableFirstPortalSection,
            boolean enableSecondPortalSection) {
        switch (GameRandom.randInt(0, RANDOM_LEVEL_TEMPLATE_COUNT - 1)) {
            case 0:
                return new StairsCurveLineLevel(enableFirstPortalSection, enableSecondPortalSection);
            case 1:
                return new CurveStairsLevel(enableFirstPortalSection, enableSecondPortalSection);
            case 2:
                return new BoostRampLevel(enableFirstPortalSection, enableSecondPortalSection);
            case 3:
                return new DoubleCurveBoostLevel(enableFirstPortalSection, enableSecondPortalSection);
            case 4:
                return new LongStairArcLevel(enableFirstPortalSection, enableSecondPortalSection);
            default:
                return new RectCurveSprintLevel(enableFirstPortalSection, enableSecondPortalSection);
        }
    }
}
