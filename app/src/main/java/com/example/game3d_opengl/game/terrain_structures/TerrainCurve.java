package com.example.game3d_opengl.game.terrain_structures;

import android.graphics.Point;

import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain_api.main.TerrainStructure;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.track_elements.DeathSpike;
import com.example.game3d_opengl.game.track_elements.Potion;

public class TerrainCurve extends TerrainStructure {

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
    protected void generateAddons(Terrain.GridBrush brush, int nRows, int nCols) {
        for (int i = 0; i < 2; ++i) {
            Addon[] addons = new Addon[5];
            for (int j = 0; j < addons.length; ++j) {
                addons[j] = new DeathSpike();
            }
            brush.reserveRandomFittingVertical(addons.length, addons);
        }
        for (int i = 0; i < 2; ++i) {
            Potion[] addons = new Potion[1];
            for (int j = 0; j < addons.length; ++j) {
                addons[j] = new Potion();
            }
            brush.reserveRandomFittingVertical(addons.length, addons);
        }

    }
}
