package com.example.game3d_opengl.game.terrain.structures;

import com.example.game3d_opengl.game.terrain.main.Terrain;
import com.example.game3d_opengl.game.terrain.main.TerrainStructure;
import com.example.game3d_opengl.game.terrain.addon.Addon;
import com.example.game3d_opengl.game.track_elements.DeathSpike;

public class TerrainLine extends TerrainStructure {

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
    protected void generateAddons(Terrain.GridBrush brush, int nRows, int nCols){
        for(int i=0;i<3;++i){
            brush.reserveRandomFittingHorizontal(2,new Addon[]{
                    new DeathSpike(), new DeathSpike()
            });
        }
        for(int i=0;i<3;++i){
            brush.reserveRandomFittingVertical(4,new Addon[]{
                    new DeathSpike(), new DeathSpike(), new DeathSpike(), new DeathSpike()
            });
        }

    }
}
