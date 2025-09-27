package com.example.game3d_opengl.game.terrain.terrain_structures;

import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.terrain_api.main.BasicTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;
import com.example.game3d_opengl.game.util.GameRandom;

public class BasicTerrainStairs extends BasicTerrainStructure {

    private final float dAngHor, jump, tilesPerStair, cntStairs;
    private final int emptyBetween;

    public BasicTerrainStairs(int tilesPerStair, int cntStairs, float dAngHor, float jump) {
        this(tilesPerStair, cntStairs, 0, dAngHor, jump);
    }

    public BasicTerrainStairs(int tilesPerStair, int cntStairs, int emptyBetween, float dAngHor, float jump) {
        super(tilesPerStair * cntStairs);
        this.dAngHor = dAngHor;
        this.jump = jump;
        this.tilesPerStair = tilesPerStair;
        this.cntStairs = cntStairs;
        this.emptyBetween = emptyBetween;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        brush.setCornerAlphas(0.5f,0.5f);
        float angHorPerTile = dAngHor / (float) (tilesToMake);
        for (int i = 0; i < cntStairs; ++i) {
            for(int j=0;j<tilesPerStair;++j) {
                brush.addHorizontalAng(angHorPerTile);
                brush.addSegment();
            }
            // add empty tiles between levels (not after the last level)
            if (i < cntStairs - 1) {
                for (int e = 0; e < emptyBetween; ++e) {
                    brush.addEmptySegment();
                }
            }
            brush.liftUp(jump);
        }
        brush.setCornerAlphas(1f,1f);
    }

    @Override
    protected void generateAddons(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        if (nRows <= 0 || nCols <= 0) return;
        int variant = GameRandom.randInt(0, 2);
        switch (variant) {
            case 0:
                placeCenterColumn(brush, nRows, nCols);
                break;
            case 1:
                placeTopPotion(brush, nRows, nCols);
                break;
            case 2:
                placeDualColumns(brush, nRows, nCols);
                break;
        }
    }

    private void placeCenterColumn(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        int col = Math.max(1, Math.min(nCols, (nCols + 1) / 2));
        int len = Math.min(nRows, 5);
        if (len <= 0) return;
        Addon[] spikes = new Addon[len];
        for (int j = 0; j < spikes.length; ++j) spikes[j] = DeathSpike.createDeathSpike();
        brush.reserveVertical(1, col, len, spikes);
    }

    private void placeTopPotion(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        if (nRows <= 0) return;
        Potion[] one = new Potion[1]; one[0] = Potion.createPotion();
        int row = Math.max(1, nRows);
        int c = Math.max(1, Math.min(nCols, (nCols / 2) + 1));
        brush.reserveHorizontal(row, c, 1, one);
    }

    private void placeDualColumns(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        int len = Math.min(nRows, 4);
        if (len <= 0) return;
        int left = Math.max(1, Math.min(nCols, nCols / 3 + 1));
        int right = Math.max(1, Math.min(nCols, (2 * nCols) / 3 + 1));
        Addon[] a = new Addon[len];
        Addon[] b = new Addon[len];
        for (int j = 0; j < len; ++j) { a[j] = DeathSpike.createDeathSpike(); b[j] = DeathSpike.createDeathSpike(); }
        brush.reserveVertical(1, left, len, a);
        brush.reserveVertical(1, right, len, b);
    }
}


