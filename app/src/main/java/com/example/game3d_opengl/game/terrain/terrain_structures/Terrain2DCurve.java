package com.example.game3d_opengl.game.terrain.terrain_structures;

import static java.lang.Math.min;

import com.example.game3d_opengl.game.terrain.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;

public class Terrain2DCurve extends AdvancedTerrainStructure {

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
        brush.addVerticalAng(-dAngVer);
        brush.addHorizontalAng(-dAngHor);
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
        for(int i=0;i<min(2,nRows);++i){
            Addon[] addons = new Addon[min(3,nCols)];
            for(int j = 0; j <addons.length; ++j){
                addons[j] = DeathSpike.createDeathSpike();
            }
            brush.reserveRandomFittingHorizontal(addons.length,addons);
        }
    }
}
