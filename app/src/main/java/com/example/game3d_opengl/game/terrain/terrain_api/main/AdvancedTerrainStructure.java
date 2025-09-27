package com.example.game3d_opengl.game.terrain.terrain_api.main;

public abstract class AdvancedTerrainStructure
                                            extends BaseTerrainStructure<Terrain.AdvancedGridBrush>{

    public AdvancedTerrainStructure(int nTiles) {
        super(nTiles);
    }


    protected final Terrain.AdvancedGridBrush selectBrush(Terrain terrain){
        return terrain.advancedGridBrush;
    }
}
