package com.example.game3d_opengl.game.terrain_structures;

import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain_api.main.TerrainStructure;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.track_elements.DeathSpike;

public class TerrainLine extends TerrainStructure {

    public TerrainLine(int tilesToMake) {
        super(tilesToMake);
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        System.out.println("### "+tilesToMake);
        for(int i=0;i<tilesToMake;++i){
            brush.addSegment();
        }
    }

    @Override
    protected void generateAddons(Terrain.GridBrush brush, int nRows, int nCols){
        /*for(int i=0;i<3;++i){
            brush.reserveRandomFittingHorizontal(2,new Addon[]{
                    new DeathSpike(), new DeathSpike()
            });
        }*/
        for(int i=0;i<1;++i) {
            Addon[] addons = new Addon[10];
            for (int j = 0; j < addons.length; ++j) {
                addons[j] = new DeathSpike();
            }
            brush.reserveRandomFittingVertical(addons.length, addons);
        }
    }
}
