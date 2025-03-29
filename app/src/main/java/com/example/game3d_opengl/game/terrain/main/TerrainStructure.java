package com.example.game3d_opengl.game.terrain.main;

public abstract class TerrainStructure {
    protected final int tilesToMake;

    public TerrainStructure(int nTiles) {
        this.tilesToMake = nTiles;
    }

    protected abstract void generateTiles(Terrain.TileBrush brush);
    protected abstract void generateAddons(Terrain.GridBrush brush, int nRows, int nCols);

}