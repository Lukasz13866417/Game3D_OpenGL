package com.example.game3d_opengl.game.terrain_structures;

import static java.lang.Math.min;

import com.example.game3d_opengl.game.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain_api.main.BaseTerrainStructure;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.track_elements.DeathSpike;

public class TerrainLine extends AdvancedTerrainStructure {

    public TerrainLine(int tilesToMake) {
        super(tilesToMake);
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        for(int i=0;i<tilesToMake;++i){
            brush.addSegment();
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols){
        for(int i=0;i<min(3,nRows);++i){
            brush.reserveRandomFittingHorizontal(2,new Addon[]{
                    DeathSpike.createDeathSpike(), DeathSpike.createDeathSpike()
            });
        }
        for(int i=0;i<min(1,nCols);++i) {
            Addon[] addons = new Addon[min(nRows,10)];
            for (int j = 0; j < addons.length; ++j) {
                addons[j] = DeathSpike.createDeathSpike();
            }
            brush.reserveRandomFittingVertical(addons.length, addons);
        }
    }
}
