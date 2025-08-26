package com.example.game3d_opengl.game.terrain_structures;

import static java.lang.Math.min;

import com.example.game3d_opengl.game.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain_api.main.BaseTerrainStructure;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.track_elements.DeathSpike;

public class TerrainSpiral extends AdvancedTerrainStructure {
    private final float dAngHor, angVer;

    public TerrainSpiral(int tilesToMake,
                          float dAngHor, float angVer) {
        super(tilesToMake);
        this.dAngHor = dAngHor;
        this.angVer = angVer;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        float angHorPerTile = dAngHor / (float) (tilesToMake);
        brush.addVerticalAng(angVer);
        for(int i=0;i<tilesToMake;++i){
            brush.addHorizontalAng(angHorPerTile);
            brush.addSegment();
        }
        brush.addVerticalAng(-angVer);
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
        for(int i=0;i<min(nRows,2);++i){
            Addon[] addons = new Addon[nCols];
            for(int j = 0; j <addons.length; ++j){
                addons[j] = DeathSpike.createDeathSpike();
            }
            brush.reserveRandomFittingHorizontal(addons.length,addons);
        }
    }
}
