package com.example.game3d_opengl.game.terrain;

import com.example.game3d_opengl.game.terrain.grid.TerrainGrid;

import java.util.ArrayList;

public abstract class TerrainStructure {
    protected final int tilesToMake;
    private final Terrain terrain;
    private final TerrainStructure parent;

    public TerrainStructure(int nTiles, Terrain terrain) {
        this.tilesToMake = nTiles;
        this.terrain = terrain;
        this.parent = null;
    }

    public TerrainStructure(int nTiles, TerrainStructure parent) {
        this.tilesToMake = nTiles;
        this.terrain = parent.terrain;
        this.parent = parent;
    }

    protected abstract void generateTiles(Terrain.TerrainBrush brush);

}