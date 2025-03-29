package com.example.game3d_opengl.game.terrain.structures;

import com.example.game3d_opengl.game.terrain.main.Terrain;
import com.example.game3d_opengl.game.terrain.main.TerrainStructure;
import com.example.game3d_opengl.game.terrain.addon.Addon;
import com.example.game3d_opengl.game.track_elements.DeathSpike;

public class Terrain2DCurve extends TerrainStructure {

    private final float dAngHor, dAngVer;

    public Terrain2DCurve(int tilesToMake,
                          float dAngHor, float dAngVer) {
        super(tilesToMake);
        this.dAngHor = dAngHor;
        this.dAngVer = dAngVer;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        float angHorPerTile = dAngHor / (float) (tilesToMake);
        float angVerPerTile = dAngVer / (float) (tilesToMake);
        for(int i=0;i<tilesToMake;++i){
            brush.addHorizontalAng(angHorPerTile);
            brush.addVerticalAng(angVerPerTile);
            brush.addSegment();
        }
    }

    @Override
    protected void generateAddons(Terrain.GridBrush brush, int nRows, int nCols) {
        for(int i=0;i<2;++i){
            Addon[] addons = new Addon[5];
            for(int j = 0; j <addons.length; ++j){
                addons[j] = new DeathSpike();
            }
            brush.reserveRandomFittingVertical(addons.length,addons);
        }
    }
}
