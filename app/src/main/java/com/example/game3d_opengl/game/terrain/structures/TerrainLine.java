package com.example.game3d_opengl.game.terrain.structures;

import com.example.game3d_opengl.game.terrain.Terrain;
import com.example.game3d_opengl.game.terrain.TerrainStructure;
import com.example.game3d_opengl.game.terrain.grid.TerrainGrid;

public class TerrainLine extends TerrainStructure {

    public TerrainLine(int tilesToMake, int nCols, Terrain terrain) {
        super(tilesToMake, nCols, terrain);
    }

    public TerrainLine(int tilesToMake, TerrainStructure parent) {
        super(tilesToMake, parent);
    }

    @Override
    protected void generateElements(TerrainGrid grid) {

    }

    @Override
    protected void generateTiles(Terrain.TerrainBrush brush) {
        for(int i=0;i<tilesToMake;++i){
            brush.addSegment();
        }
    }
}
