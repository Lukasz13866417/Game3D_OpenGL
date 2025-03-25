package com.example.game3d_opengl.game.terrain.structures;

import com.example.game3d_opengl.game.terrain.Terrain;
import com.example.game3d_opengl.game.terrain.TerrainStructure;
import com.example.game3d_opengl.game.terrain.grid.TerrainGrid;

public class Terrain2DCurve extends TerrainStructure {

    private final float dAngHor, dAngVer;

    public Terrain2DCurve(int tilesToMake, Terrain terrain,
                          float dAngHor, float dAngVer) {
        super(tilesToMake, terrain);
        this.dAngHor = dAngHor;
        this.dAngVer = dAngVer;
    }

    public Terrain2DCurve(int tilesToMake, TerrainStructure parent,
                          float dAngHor, float dAngVer) {
        super(tilesToMake, parent);
        this.dAngVer = dAngVer;
        this.dAngHor = dAngHor;
    }

    @Override
    protected void generateTiles(Terrain.TerrainBrush brush) {
        float angHorPerTile = dAngHor / (float) (tilesToMake);
        float angVerPerTile = dAngVer / (float) (tilesToMake);
        for(int i=0;i<tilesToMake;++i){
            brush.addHorizontalAng(angHorPerTile);
            brush.addVerticalAng(angVerPerTile);
            brush.addSegment();
        }
    }
}
