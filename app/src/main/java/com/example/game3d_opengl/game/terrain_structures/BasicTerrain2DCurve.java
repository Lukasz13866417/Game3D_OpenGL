package com.example.game3d_opengl.game.terrain_structures;

import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain_api.main.BasicTerrainStructure;
import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.track_elements.DeathSpike;
import com.example.game3d_opengl.rendering.util3d.GameRandom;

public class BasicTerrain2DCurve extends BasicTerrainStructure {

    private final float dAngHor, dAngVer;

    public BasicTerrain2DCurve(int tilesToMake, float dAngHor, float dAngVer) {
        super(tilesToMake);
        this.dAngHor = dAngHor;
        this.dAngVer = dAngVer;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        float angHorPerTile = dAngHor / (float) (tilesToMake);
        float angVerPerTile = dAngVer / (float) (tilesToMake);
        for (int i = 0; i < tilesToMake; ++i) {
            brush.addHorizontalAng(angHorPerTile);
            brush.addVerticalAng(angVerPerTile);
            brush.addSegment();
        }
    }

    @Override
    protected void generateAddons(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        if (nRows <= 0 || nCols <= 0) return;
        int variant = GameRandom.randInt(0, 2);
        switch (variant) {
            case 0:
                placeCenterShort(brush, nRows, nCols);
                break;
            case 1:
                placeLeftRight(brush, nRows, nCols);
                break;
            case 2:
                placeHorizontalRow(brush, nRows, nCols);
                break;
        }
    }

    private void placeCenterShort(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        int length = Math.min(3, nRows);
        int col = Math.max(0, Math.min(nCols - 1, nCols / 2));
        Addon[] addons = makeSpikes(length);
        brush.reserveVertical(0, col, length, addons);
    }

    private void placeLeftRight(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        int length = Math.min(2, nRows);
        int left = Math.max(0, nCols / 4);
        int right = Math.max(0, Math.min(nCols - 1, (3 * nCols) / 4));
        Addon[] a = makeSpikes(length);
        Addon[] b = makeSpikes(length);
        brush.reserveVertical(0, left, length, a);
        brush.reserveVertical(0, right, length, b);
    }

    private void placeHorizontalRow(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        int row = Math.max(0, nRows / 3);
        int length = Math.min(3, nCols);
        int startCol = Math.max(0, Math.min(nCols - length, nCols / 3));
        Addon[] addons = makeSpikes(length);
        brush.reserveHorizontal(row, startCol, length, addons);
    }

    private Addon[] makeSpikes(int length) {
        Addon[] addons = new Addon[length];
        for (int j = 0; j < addons.length; ++j) addons[j] = DeathSpike.createDeathSpike();
        return addons;
    }
}


