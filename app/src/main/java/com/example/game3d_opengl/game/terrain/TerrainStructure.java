package com.example.game3d_opengl.game.terrain;

public abstract class TerrainStructure {
    protected final int tilesToMake;

    public TerrainStructure(int nTiles, Terrain terrain) {
        this.tilesToMake = nTiles;
    }

    public TerrainStructure(int nTiles, TerrainStructure parent) {
        this.tilesToMake = nTiles;
    }

    protected abstract void generateTiles(final Terrain.TileBrush brush);
    protected abstract void generateAddons(final Terrain.GridBrush brush, int nRows, int nCols);

}