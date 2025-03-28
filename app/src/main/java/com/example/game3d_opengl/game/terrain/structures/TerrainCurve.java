package com.example.game3d_opengl.game.terrain.structures;

import com.example.game3d_opengl.game.terrain.Terrain;
import com.example.game3d_opengl.game.terrain.TerrainStructure;
import com.example.game3d_opengl.game.terrain.addon.Addon;
import com.example.game3d_opengl.game.track_elements.DeathSpike;

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
    protected void generateTiles(Terrain.TileBrush brush) {
        float angHorPerTile = dAngHor / (float) (tilesToMake);
        for(int i=0;i<tilesToMake;++i){
            brush.addHorizontalAng(angHorPerTile);
            brush.addSegment();
        }
    }

    @Override
    protected void generateAddons(Terrain.GridBrush brush, int nRows, int nCols) {
        for(int i=0;i<nCols;++i){
            brush.reserveRandomFittingHorizontal(1,new Addon[]{new DeathSpike()});
        }
    }
}
