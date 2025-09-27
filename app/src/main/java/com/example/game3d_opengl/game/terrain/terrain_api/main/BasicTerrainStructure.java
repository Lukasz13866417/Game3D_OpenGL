package com.example.game3d_opengl.game.terrain.terrain_api.main;

public abstract class BasicTerrainStructure extends BaseTerrainStructure<Terrain.BasicGridBrush>{

    public BasicTerrainStructure(int nTiles) {
        super(nTiles);
    }

    protected final Terrain.BasicGridBrush selectBrush(Terrain terrain){
        return terrain.basicGridBrush;
    }

}
