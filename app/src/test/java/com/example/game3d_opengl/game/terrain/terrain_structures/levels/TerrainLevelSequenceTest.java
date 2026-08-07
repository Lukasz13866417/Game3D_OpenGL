package com.example.game3d_opengl.game.terrain.terrain_structures.levels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.BasicTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainBoostRamp;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial.CurveStairsLevel;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial.StairsCurveLineLevel;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.late.DoubleCurveBoostLevel;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.late.LongStairArcLevel;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.mid.BoostRampLevel;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.mid.RectCurveSprintLevel;
import com.example.game3d_opengl.game.util.GameRandom;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TerrainLevelSequenceTest {
    private static final int MIN_GAMEPLAY_LEVEL_TILES = 120;
    private static final int MIN_SAFE_BOOST_LANDING_TILES = 12;
    private final List<Terrain> terrainsToCleanup = new ArrayList<>();

    @After
    public void cleanupTerrains() {
        for (int i = terrainsToCleanup.size() - 1; i >= 0; --i) {
            terrainsToCleanup.get(i).cleanupGPUResourcesRecursively();
        }
        terrainsToCleanup.clear();
    }

    @Test
    public void synthetic_nested_level_generates_cleanly_with_seed_409() throws Exception {
        setGameRandomSeed(409L);

        Terrain terrain = createTerrain(6);
        terrain.enqueueStructure(new SyntheticNestedLevel());
        terrain.generateChunks(-1);

        assertTrue("Expected nested level to generate tiles.", terrain.getTileCount() > 0);
        assertTrue("Expected nested level to generate addons.", terrain.getAddonCount() > 0);
    }

    @Test
    public void synthetic_nested_level_generates_cleanly_across_many_seeds() throws Exception {
        for (int seed = 0; seed < 64; ++seed) {
            setGameRandomSeed(seed);
            Terrain terrain = createTerrain(6);
            try {
                terrain.enqueueStructure(new SyntheticNestedLevel());
                terrain.generateChunks(-1);

                assertTrue("Seed " + seed + " should produce tiles.", terrain.getTileCount() > 0);
                assertTrue("Seed " + seed + " should produce addons.", terrain.getAddonCount() > 0);
            } finally {
                cleanupTerrain(terrain);
            }
        }
    }

    @Test
    public void advanced_child_under_basic_parent_generates_cleanly_inside_advanced_grandparent()
            throws Exception {
        setGameRandomSeed(7L);

        Terrain terrain = createTerrain(6);
        terrain.enqueueStructure(
                new AdvancedGrandparentWithBasicChild(new BasicParentWithAdvancedChild(new RandomCurveSection(8)))
        );
        terrain.generateChunks(-1);

        assertTrue("Expected mixed basic/advanced nesting to generate tiles.", terrain.getTileCount() > 0);
        assertTrue("Expected mixed basic/advanced nesting to generate addons.", terrain.getAddonCount() > 0);
    }

    @Test
    public void basic_parent_with_basic_and_advanced_children_generates_cleanly() throws Exception {
        setGameRandomSeed(31L);

        Terrain terrain = createTerrain(6);
        terrain.enqueueStructure(new AdvancedGrandparentWithBasicChild(new BasicParentWithMixedChildren()));
        terrain.generateChunks(-1);

        assertTrue("Expected basic/advanced mixed parent to generate tiles.", terrain.getTileCount() > 0);
        assertTrue("Expected basic/advanced mixed parent to generate addons.", terrain.getAddonCount() > 0);
    }

    @Test
    public void rectangle_section_stays_inside_rows_below_blocking_child() throws Exception {
        setGameRandomSeed(67L);

        Terrain terrain = createTerrain(6);
        terrain.enqueueStructure(new RectSectionWithBlockingChild(22, 25));
        terrain.generateChunks(-1);

        assertTrue("Expected tiles for the blocked-child rectangle section.", terrain.getTileCount() > 0);
        assertTrue("Expected addons for the blocked-child rectangle section.", terrain.getAddonCount() > 0);
    }

    @Test
    public void all_concrete_gameplay_levels_declare_substantial_track_length() {
        TerrainLevelSequence[] levels = new TerrainLevelSequence[]{
                new StairsCurveLineLevel(false, false),
                new CurveStairsLevel(false, false),
                new RectCurveSprintLevel(false, false),
                new BoostRampLevel(false, false),
                new DoubleCurveBoostLevel(false, false),
                new LongStairArcLevel(false, false)
        };

        for (TerrainLevelSequence level : levels) {
            assertTrue(
                    level.getDebugName()
                            + " should generate at least "
                            + MIN_GAMEPLAY_LEVEL_TILES + " track tiles.",
                    level.getMinimumGeneratedTileCount()
                            >= MIN_GAMEPLAY_LEVEL_TILES
            );
        }
    }

    @Test
    public void boost_levels_embed_contiguous_addon_free_landing_in_ramp() {
        assertBoostRampProvidesSafeLanding(new BoostRampLevel(false, false));
        assertBoostRampProvidesSafeLanding(new DoubleCurveBoostLevel(false, false));
    }

    private Terrain createTerrain(int nCols) {
        Terrain terrain = new Terrain(
                1024,
                nCols,
                new Vector3D(0f, 0f, 0f),
                3.2f,
                1.4f,
                1f,
                new LightSource(new FColor(1f, 1f, 1f))
        );
        terrainsToCleanup.add(terrain);
        return terrain;
    }

    private void cleanupTerrain(Terrain terrain) {
        if (terrain == null) {
            return;
        }
        terrain.cleanupGPUResourcesRecursively();
        terrainsToCleanup.remove(terrain);
    }

    private static void setGameRandomSeed(long seed) throws ReflectiveOperationException {
        Field randomField = GameRandom.class.getDeclaredField("RANDOM");
        randomField.setAccessible(true);
        randomField.set(null, new Random(seed));
    }

    private static Addon[] noOpAddons(int count) {
        Addon[] addons = new Addon[count];
        for (int i = 0; i < count; ++i) {
            addons[i] = new NoOpAddon();
        }
        return addons;
    }

    private void assertBoostRampProvidesSafeLanding(TerrainLevelSequence level) {
        Object[] sections = level.sectionsForTesting();
        for (Object section : sections) {
            if (section instanceof TerrainBoostRamp) {
                TerrainBoostRamp ramp = (TerrainBoostRamp) section;
                assertEquals(
                        level.getDebugName()
                                + " should not leave an empty gap after launch.",
                        0,
                        ramp.getGapTiles());
                assertTrue(
                        level.getDebugName()
                                + " should include a substantial safe landing.",
                        ramp.getLandingTiles() >= MIN_SAFE_BOOST_LANDING_TILES);

                Terrain terrain = createTerrain(6);
                try {
                    terrain.enqueueStructure(ramp);
                    terrain.generateChunks(-1);
                    assertEquals(
                            level.getDebugName()
                                    + " ramp and landing should not generate addons.",
                            0,
                            terrain.getAddonCount());
                } finally {
                    cleanupTerrain(terrain);
                }
                return;
            }
        }
        fail(level.getDebugName() + " should contain a boost ramp section.");
    }

    private static final class NoOpAddon extends Addon {
        @Override
        protected void onPlace(
                float nearLeftX, float nearLeftY, float nearLeftZ,
                float nearRightX, float nearRightY, float nearRightZ,
                float farLeftX, float farLeftY, float farLeftZ,
                float farRightX, float farRightY, float farRightZ
        ) {}

        @Override
        public void accept(Player player) {}

        @Override
        public void updateBeforeDraw(float dt) {}

        @Override
        public void updateAfterDraw(float dt) {}

        @Override
        public void cleanupGPUResourcesRecursively() {}

        @Override
        public void reloadGPUResourcesRecursivelyOnContextLoss() {}

        @Override
        public void draw(float[] mvpMatrix) {}

        @Override
        public void rebasePosition(Vector3D delta) {}
    }

    private static final class BlockingCorridorSection extends AdvancedTerrainStructure {
        BlockingCorridorSection(int rows) {
            super(rows);
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
            int len = Math.min(2, nCols);
            brush.reserveHorizontal(1, 1, len, noOpAddons(len));
        }

        @Override
        protected int[] getParentBlockedRowsRange(int nRows, int nCols) {
            return new int[]{1, nRows};
        }

        @Override
        protected boolean shouldPropagateReservationsToParent() {
            return false;
        }
    }

    private static final class RectSectionWithBlockingChild extends AdvancedTerrainStructure {
        private final BlockingCorridorSection child;
        private final int childRows;

        RectSectionWithBlockingChild(int childRows, int ownRows) {
            super(ownRows);
            this.childRows = childRows;
            this.child = new BlockingCorridorSection(childRows);
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            addChild(child, brush);
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
            int freeRows = Math.max(1, nRows - childRows);
            int sideSize = Math.max(2, Math.min(nCols - 1, freeRows));
            int topRow = childRows + 1 + Math.max(0, (freeRows - sideSize) / 2);
            int leftCol = 1 + Math.max(0, (nCols - sideSize) / 2);
            int span = Math.max(1, sideSize - 1);

            brush.reserveHorizontal(topRow, leftCol, span, noOpAddons(span));
            brush.reserveVertical(topRow, leftCol + sideSize - 1, span, noOpAddons(span));
            brush.reserveHorizontal(topRow + sideSize - 1, leftCol + 1, span, noOpAddons(span));
            brush.reserveVertical(topRow + 1, leftCol, span, noOpAddons(span));
        }
    }

    private static final class RandomCurveSection extends AdvancedTerrainStructure {
        RandomCurveSection(int rows) {
            super(rows);
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
            int len = Math.min(3, nCols);
            brush.reserveRandomFittingHorizontal(len, noOpAddons(len));
            if (nRows >= 2) {
                brush.reserveRandomFittingHorizontal(len, noOpAddons(len));
            }
        }
    }

    private static final class StairStepSection extends AdvancedTerrainStructure {
        StairStepSection(int rows) {
            super(rows);
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
            brush.reserveHorizontal(1, 1, nCols, noOpAddons(nCols));
        }
    }

    private static final class StairsSection extends AdvancedTerrainStructure {
        private final int rowsPerStep;
        private final int stepCount;
        private final int emptyBetween;

        StairsSection(int rowsPerStep, int stepCount, int emptyBetween) {
            super(rowsPerStep * stepCount);
            this.rowsPerStep = rowsPerStep;
            this.stepCount = stepCount;
            this.emptyBetween = emptyBetween;
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            for (int i = 0; i < stepCount; ++i) {
                addChild(new StairStepSection(rowsPerStep), brush);
                if (i < stepCount - 1) {
                    for (int j = 0; j < emptyBetween; ++j) {
                        brush.addEmptySegment();
                    }
                }
            }
        }

        @Override
        protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
        }
    }

    private static final class LineSection extends AdvancedTerrainStructure {
        LineSection(int rows) {
            super(rows);
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
            int hLen = Math.min(2, nCols);
            brush.reserveRandomFittingHorizontal(hLen, noOpAddons(hLen));
            brush.reserveRandomFittingHorizontal(hLen, noOpAddons(hLen));
            int vLen = Math.min(4, nRows);
            brush.reserveRandomFittingVertical(vLen, noOpAddons(vLen));
        }
    }

    private static final class SyntheticNestedLevel extends TerrainLevelSequence {
        SyntheticNestedLevel() {
            super(
                    "synthetic_nested_level",
                    new RectSectionWithBlockingChild(4, 8),
                    new RandomCurveSection(12),
                    new RectSectionWithBlockingChild(3, 7),
                    new StairsSection(6, 4, 1),
                    new LineSection(14)
            );
        }
    }

    private static final class BasicLeafSection extends BasicTerrainStructure {
        BasicLeafSection(int rows) {
            super(rows);
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.BasicGridBrush brush, int nRows, int nCols) {
            int len = Math.min(2, nRows);
            brush.reserveVertical(1, 1, len, noOpAddons(len));
        }
    }

    private static final class AdvancedGrandparentWithBasicChild extends AdvancedTerrainStructure {
        private final BasicTerrainStructure child;

        AdvancedGrandparentWithBasicChild(BasicTerrainStructure child) {
            super(4);
            this.child = child;
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            addChild(child, brush);
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
            int len = Math.min(3, nRows);
            brush.reserveRandomFittingVertical(len, noOpAddons(len));
        }
    }

    private static final class BasicParentWithAdvancedChild extends BasicTerrainStructure {
        private final AdvancedTerrainStructure child;

        BasicParentWithAdvancedChild(AdvancedTerrainStructure child) {
            super(4);
            this.child = child;
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            addChild(child, brush);
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.BasicGridBrush brush, int nRows, int nCols) {
            brush.reserveHorizontal(nRows, 1, Math.min(2, nCols), noOpAddons(Math.min(2, nCols)));
        }
    }

    private static final class BasicParentWithMixedChildren extends BasicTerrainStructure {
        private final BasicLeafSection basicChild = new BasicLeafSection(4);
        private final RandomCurveSection advancedChild = new RandomCurveSection(6);

        BasicParentWithMixedChildren() {
            super(3);
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            addChild(basicChild, brush);
            addChild(advancedChild, brush);
            for (int i = 0; i < tilesToMake; ++i) {
                brush.addSegment();
            }
        }

        @Override
        protected void generateAddons(Terrain.BasicGridBrush brush, int nRows, int nCols) {
            int len = Math.min(2, nCols);
            brush.reserveHorizontal(nRows, 1, len, noOpAddons(len));
        }
    }
}
