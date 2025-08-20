package com.example.game3d_opengl.game.terrain_structures;

import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain_api.main.BasicTerrainStructure;
import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.track_elements.DeathSpike;
import com.example.game3d_opengl.game.track_elements.Potion;
import com.example.game3d_opengl.rendering.util3d.GameRandom;

public class BasicTerrainCurve extends BasicTerrainStructure {

    private final float dAngHor;

    public BasicTerrainCurve(int tilesToMake, float dAngHor) {
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
    protected void generateAddons(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        if (nRows <= 0 || nCols <= 0) return;
        int variant = GameRandom.randInt(0, 2);
        switch (variant) {
            case 0:
                placeStartVerticalSet(brush, nRows, nCols);
                break;
            case 1:
                placeEndPotion(brush, nRows, nCols);
                break;
            case 2:
                placeAlternatingColumns(brush, nRows, nCols);
                break;
        }
    }

    private void placeStartVerticalSet(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        int col = Math.max(0, Math.min(nCols - 1, nCols / 3));
        int length = Math.min(nRows, 5);
        Addon[] addons = new Addon[length];
        for (int j = 0; j < addons.length; ++j) addons[j] = DeathSpike.createDeathSpike();
        brush.reserveVertical(0, col, length, addons);
    }

    private void placeEndPotion(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        Potion[] potions = new Potion[1];
        potions[0] = new Potion();
        int row = Math.max(0, nRows - 1);
        int col2 = Math.max(0, Math.min(nCols - 1, (2 * nCols) / 3));
        brush.reserveHorizontal(row, col2, 1, potions);
    }

    private void placeAlternatingColumns(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        int length = Math.min(nRows, 4);
        for (int c = 0; c < nCols; c += 2) {
            Addon[] addons = new Addon[length];
            for (int j = 0; j < addons.length; ++j) addons[j] = DeathSpike.createDeathSpike();
            brush.reserveVertical(0, c, length, addons);
        }
    }
}


