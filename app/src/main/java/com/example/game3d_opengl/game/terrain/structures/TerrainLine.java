package com.example.game3d_opengl.game.terrain.structures;

import com.example.game3d_opengl.game.terrain.Terrain;
import com.example.game3d_opengl.game.terrain.TerrainStructure;
import com.example.game3d_opengl.game.terrain.addon.Addon;
import com.example.game3d_opengl.game.track_elements.DeathSpike;

public class TerrainLine extends TerrainStructure {

    public TerrainLine(int tilesToMake, Terrain terrain) {
        super(tilesToMake, terrain);
    }

    public TerrainLine(int tilesToMake, TerrainStructure parent) {
        super(tilesToMake, parent);
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        for(int i=0;i<tilesToMake;++i){
            brush.addSegment();
        }
    }

    @Override
    protected void generateAddons(Terrain.GridBrush brush, int nRows, int nCols){
        for(int i=0;i<nCols;++i){
            brush.reserveRandomFittingHorizontal(1,new Addon[]{new DeathSpike()});
        }
    }
}
