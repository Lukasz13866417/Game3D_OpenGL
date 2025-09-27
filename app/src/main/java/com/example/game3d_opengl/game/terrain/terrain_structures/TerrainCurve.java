package com.example.game3d_opengl.game.terrain.terrain_structures;

import com.example.game3d_opengl.game.terrain.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;

public class TerrainCurve extends AdvancedTerrainStructure {

    private final float dAngHor;

    public TerrainCurve(int tilesToMake, float dAngHor) {
        super(tilesToMake);
        this.dAngHor = dAngHor;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        float angHorPerTile = dAngHor / (float) (tilesToMake);
        for (int i = 0; i < tilesToMake; ++i) {
            brush.addHorizontalAng(angHorPerTile);
            brush.addSegment();
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
        for (int i = 0; i < 2; ++i) {
            Addon[] addons = new Addon[5];
            for (int j = 0; j < addons.length; ++j) {
                addons[j] = DeathSpike.createDeathSpike();
            }
            brush.reserveRandomFittingVertical(addons.length, addons);
        }
        for (int i = 0; i < 2; ++i) {
            Potion[] addons = new Potion[1];
            for (int j = 0; j < addons.length; ++j) {
                addons[j] = Potion.createPotion();
            }
            brush.reserveRandomFittingVertical(addons.length, addons);
        }

    }
}
