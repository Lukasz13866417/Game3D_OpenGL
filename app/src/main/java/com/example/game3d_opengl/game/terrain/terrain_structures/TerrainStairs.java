package com.example.game3d_opengl.game.terrain.terrain_structures;

import com.example.game3d_opengl.game.terrain.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;

public class TerrainStairs extends AdvancedTerrainStructure {

    private final float dAngHor, jump, tilesPerStair, cntStairs;
    private final int emptyBetween;

    public TerrainStairs(int tilesPerStair, int cntStairs, float dAngHor, float jump) {
        this(tilesPerStair, cntStairs, 0, dAngHor, jump);
    }

    public TerrainStairs(int tilesPerStair, int cntStairs, int emptyBetween, float dAngHor, float jump) {
        super(tilesPerStair*cntStairs);
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
        brush.liftUp(jump);
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
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
        for (int i = 0; i < 2; ++i) {
            Addon[] addons = new Addon[5];
            for (int j = 0; j < addons.length; ++j) {
                addons[j] = DeathSpike.createDeathSpike();
            }
            brush.reserveRandomFittingVertical(addons.length, addons);
        }
        for (int i = 0; i < 8; ++i) {
            Potion[] addons = new Potion[1];
            for (int j = 0; j < addons.length; ++j) {
                addons[j] = Potion.createPotion();
            }
            brush.reserveRandomFittingVertical(addons.length, addons);
        }

    }
}
