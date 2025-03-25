package com.example.game3d_opengl.game.terrain.structures;

import com.example.game3d_opengl.game.terrain.Terrain;
import com.example.game3d_opengl.game.terrain.TerrainStructure;
import com.example.game3d_opengl.game.terrain.grid.TerrainGrid;

public class TerrainCurve extends TerrainStructure {

    private final float dAngHor;

    public TerrainCurve(int tilesToMake, Terrain terrain, float dAngHor) {
        super(tilesToMake, terrain);
        this.dAngHor = dAngHor;
    }

    public TerrainCurve(int tilesToMake, TerrainStructure parent, float dAngHor) {
        super(tilesToMake, parent);
        this.dAngHor = dAngHor;
    }

    @Override
    protected void generateTiles(Terrain.TerrainBrush brush) {
        float angHorPerTile = dAngHor / (float) (tilesToMake);
        for(int i=0;i<tilesToMake;++i){
            brush.addHorizontalAng(angHorPerTile);
            brush.addSegment();
        }
    }
}
