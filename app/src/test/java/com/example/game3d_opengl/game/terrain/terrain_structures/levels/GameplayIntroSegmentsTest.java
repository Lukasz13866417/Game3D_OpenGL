package com.example.game3d_opengl.game.terrain.terrain_structures.levels;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial.IntroEmptyStraight;
import com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial.IntroSparseSpikeStraight;
import com.example.game3d_opengl.game.util.GameRandom;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import org.junit.After;
import org.junit.Test;

public class GameplayIntroSegmentsTest {
    private final List<Terrain> terrainsToCleanup = new ArrayList<>();

    @After
    public void cleanupTerrains() {
        for (int i = terrainsToCleanup.size() - 1; i >= 0; --i) {
            terrainsToCleanup.get(i).cleanupGPUResourcesRecursively();
        }
        terrainsToCleanup.clear();
    }

    @Test
    public void intro_empty_straight_has_no_addons() {
        Terrain terrain = createTerrain(6);
        terrain.enqueueStructure(new IntroEmptyStraight(8));
        terrain.generateChunks(-1);

        assertTrue("Expected intro straight to generate tiles.", terrain.getTileCount() > 0);
        assertEquals("Empty intro straight should not place addons.", 0, terrain.getAddonCount());
    }

    @Test
    public void intro_sparse_spike_straight_places_some_spikes() throws Exception {
        setGameRandomSeed(17L);

        Terrain terrain = createTerrain(6);
        terrain.enqueueStructure(new TestableIntroSparseSpikeStraight(8));
        terrain.generateChunks(-1);

        assertTrue("Expected sparse spike intro to generate tiles.", terrain.getTileCount() > 0);
        assertTrue("Sparse spike intro should place at least one addon.", terrain.getAddonCount() > 0);
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

    private static void setGameRandomSeed(long seed) throws ReflectiveOperationException {
        Field randomField = GameRandom.class.getDeclaredField("RANDOM");
        randomField.setAccessible(true);
        randomField.set(null, new Random(seed));
    }

    private static final class TestableIntroSparseSpikeStraight extends IntroSparseSpikeStraight {
        TestableIntroSparseSpikeStraight(int rows) {
            super(rows);
        }

        @Override
        protected Addon createSpike() {
            return new NoOpAddon();
        }
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
}
