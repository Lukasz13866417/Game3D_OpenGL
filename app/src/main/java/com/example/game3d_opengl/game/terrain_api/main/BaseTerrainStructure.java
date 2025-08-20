package com.example.game3d_opengl.game.terrain_api.main;

public abstract class BaseTerrainStructure<GridBrushType extends Terrain.BaseGridBrush> {
    protected final int tilesToMake;

    public BaseTerrainStructure(int nTiles) {
        this.tilesToMake = nTiles;
    }

    protected abstract void generateTiles(Terrain.TileBrush brush);
    protected abstract void generateAddons(GridBrushType brush, int nRows, int nCols);

    protected abstract GridBrushType selectBrush(Terrain terrain);

    public final void generateAddons(Terrain terrain, int nRows, int nCols){
        generateAddons(selectBrush(terrain), nRows, nCols);
    }

    protected void addChild(BaseTerrainStructure<? extends Terrain.BaseGridBrush> child, Terrain.TileBrush what){
        what.addChild(child);
    }

}