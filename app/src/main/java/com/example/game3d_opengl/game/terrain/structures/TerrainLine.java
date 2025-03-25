package com.example.game3d_opengl.game.terrain.structures;

import com.example.game3d_opengl.game.terrain.Terrain;
import com.example.game3d_opengl.game.terrain.TerrainStructure;
import com.example.game3d_opengl.game.terrain.grid.TerrainGrid;

public class TerrainLine extends TerrainStructure {

    public TerrainLine(int tilesToMake, Terrain terrain) {
        super(tilesToMake, terrain);
    }

    public TerrainLine(int tilesToMake, TerrainStructure parent) {
        super(tilesToMake, parent);
    }

    @Override
    protected void generateTiles(Terrain.TerrainBrush brush) {
        for(int i=0;i<tilesToMake;++i){
            brush.addSegment();
        }
    }
}
