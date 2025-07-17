package com.example.game3d_opengl.game.terrain_structures;

import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain_api.main.TerrainStructure;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.track_elements.DeathSpike;

public class TerrainSpiral extends TerrainStructure {

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
    }

    @Override
    protected void generateAddons(Terrain.GridBrush brush, int nRows, int nCols) {
        for(int i=0;i<2;++i){
            Addon[] addons = new Addon[3];
            for(int j = 0; j <addons.length; ++j){
                addons[j] = new DeathSpike();
            }
            brush.reserveRandomFittingHorizontal(addons.length,addons);
        }
    }
}
