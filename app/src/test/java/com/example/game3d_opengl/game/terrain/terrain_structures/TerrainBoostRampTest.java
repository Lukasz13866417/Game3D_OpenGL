package com.example.game3d_opengl.game.terrain.terrain_structures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Tile;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TileProfile;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TerrainBoostRampTest {
    private final List<Terrain> terrainsToCleanup = new ArrayList<>();

    @After
    public void cleanupTerrains() {
        for (int i = terrainsToCleanup.size() - 1; i >= 0; --i) {
            terrainsToCleanup.get(i).cleanupGPUResourcesRecursively();
        }
        terrainsToCleanup.clear();
    }

    @Test
    public void boost_ramp_marks_ramp_tiles_and_restores_normal_profile_after_gap() {
        Terrain terrain = createTerrain();
        TerrainBoostRamp ramp = TerrainBoostRamp.builder()
                .rampTiles(4)
                .gapTiles(2)
                .landingTiles(3)
                .launchAngleDelta(0.45f)
                .build();

        terrain.enqueueStructure(ramp);
        terrain.generateChunks(-1);

        assertEquals(1 + 4 + 2 + 3, terrain.getTileCount());

        float previousBrightness = Float.NEGATIVE_INFINITY;
        for (int i = 1; i <= 3; ++i) {
            Tile tile = terrain.getTile(i);
            assertEquals(TileProfile.BOOST_RAMP, tile.getProfile());
            assertFalse(tile.isEmptySegment());
            assertTrue(tile.getBrightnessMultiplier() >= previousBrightness);
            previousBrightness = tile.getBrightnessMultiplier();
        }
        Tile launchTile = terrain.getTile(4);
        assertEquals(TileProfile.BOOST_RAMP_LAUNCH, launchTile.getProfile());
        assertFalse(launchTile.isEmptySegment());
        assertTrue(
                launchTile.getHorizontalSpeedMultiplier()
                        > TileProfile.BOOST_RAMP.getHorizontalSpeedMultiplier()
        );
        assertEquals(TileProfile.NORMAL.getBrightnessMultiplier(), terrain.getTile(1).getBrightnessMultiplier(), 1e-6f);
        assertTrue(terrain.getTile(2).getBrightnessMultiplier() > terrain.getTile(1).getBrightnessMultiplier());
        assertTrue(terrain.getTile(3).getBrightnessMultiplier() > terrain.getTile(2).getBrightnessMultiplier());
        assertTrue(launchTile.getBrightnessMultiplier() > terrain.getTile(3).getBrightnessMultiplier());
        for (int i = 5; i <= 6; ++i) {
            Tile tile = terrain.getTile(i);
            assertEquals(TileProfile.NORMAL, tile.getProfile());
            assertTrue(tile.isEmptySegment());
            assertEquals(TileProfile.NORMAL.getBrightnessMultiplier(), tile.getBrightnessMultiplier(), 1e-6f);
        }
        for (int i = 7; i <= 9; ++i) {
            Tile tile = terrain.getTile(i);
            assertEquals(TileProfile.NORMAL, tile.getProfile());
            assertFalse(tile.isEmptySegment());
            assertEquals(TileProfile.NORMAL.getBrightnessMultiplier(), tile.getBrightnessMultiplier(), 1e-6f);
        }
    }

    private Terrain createTerrain() {
        Terrain terrain = new Terrain(
                256,
                4,
                new Vector3D(0f, 0f, 0f),
                3.2f,
                1.4f,
                1f,
                new LightSource(new FColor(1f, 1f, 1f))
        );
        terrainsToCleanup.add(terrain);
        return terrain;
    }
}
