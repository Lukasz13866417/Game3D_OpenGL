package com.example.game3d_opengl.game.terrain.terrain_structures;

import com.example.game3d_opengl.game.terrain.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;
import com.example.game3d_opengl.game.terrain.track_elements.spike.SpikeInfillDrawArgs;

public class TerrainStairs extends AdvancedTerrainStructure {

    private final float dAngHor, jump, cntStairs;
    private final int emptyBetween, tilesPerStair;

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
            brush.addChild(new Terrain2DCurve(tilesPerStair, angHorPerTile*tilesPerStair, 0){
                @Override
                protected void generateAddons(Terrain.AdvancedGridBrush gridBrush,
                                                            int nRows, int nCols) {
                    DeathSpike[] spikes = new DeathSpike[nCols];
                    for (int j = 0; j < spikes.length; ++j) {
                        spikes[j] = DeathSpike.createDeathSpike();
                    }
                    gridBrush.reserveHorizontal(1, 1, nCols, spikes);
                }
            });
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
    }
}
