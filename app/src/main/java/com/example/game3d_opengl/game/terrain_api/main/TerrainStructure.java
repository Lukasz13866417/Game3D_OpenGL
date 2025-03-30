package com.example.game3d_opengl.game.terrain_api.main;

public abstract class TerrainStructure {
    protected final int tilesToMake;

    private static final String DEFAULT_NAME = "SOME_STRUCTURE"; // just for debug
    private final String name;
    public TerrainStructure(int nTiles, String name) {
        this.tilesToMake = nTiles;
        this.name = name;
    }

    public TerrainStructure(int nTiles){
        this(nTiles,DEFAULT_NAME);
    }

    protected abstract void generateTiles(Terrain.TileBrush brush);
    protected abstract void generateAddons(Terrain.GridBrush brush, int nRows, int nCols);

    protected void addChild(TerrainStructure child, Terrain.TileBrush what){
        what.addChild(child);
    }

    @Override
    public String toString(){
        return name;
    }

}