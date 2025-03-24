package com.example.game3d_opengl.game.terrain;

import com.example.game3d_opengl.game.terrain.grid.TerrainGrid;

import java.util.ArrayList;

public abstract class TerrainStructure {

    private static final int PREALLOCATED_CHILD_SLOTS = 10;

    private final int nGridCols, rowOffsetFromParent;
    protected final int tilesToMake;
    private final Terrain terrain;
    private final Terrain.TerrainBrush brush;
    private final TerrainStructure parent;

    private boolean areTilesFinished;
    private int cntGridRows;  // Depends on the generated tiles. Initialized after they're generated
    private TerrainGrid grid; // Not initialized until we know number of rows ^.

    private final ArrayList<TerrainStructure> children;

    public TerrainStructure(int nTiles, int nGridCols, Terrain terrain) {
        this.tilesToMake = nTiles;
        this.nGridCols = nGridCols;
        this.terrain = terrain;
        this.brush = this.terrain.getBrush();
        this.parent = null;
        this.rowOffsetFromParent = 0;
        this.children = new ArrayList<>(PREALLOCATED_CHILD_SLOTS);
        this.areTilesFinished = false;
    }

    public TerrainStructure(int nTiles, TerrainStructure parent) {
        this.tilesToMake = nTiles;
        this.nGridCols = parent.nGridCols;
        this.terrain = parent.terrain;
        this.brush = this.terrain.getBrush();
        this.rowOffsetFromParent = this.brush.countRows();
        this.parent = parent;
        this.children = new ArrayList<>(PREALLOCATED_CHILD_SLOTS);
        this.areTilesFinished = false;
    }

    protected abstract void generateElements(TerrainGrid grid);

    protected abstract void generateTiles(Terrain.TerrainBrush brush);

    private void tileGenerationWrapper() {
        int cntRowsBefore = brush.countRows();
        generateTiles(brush);
        this.cntGridRows = brush.countRows() - cntRowsBefore;
        this.areTilesFinished = true;
    }

    protected final void addChild(TerrainStructure child) {
        child.tileGenerationWrapper();
        children.add(child);
    }

    // Generates child addons in pre-order.
    // Generates own addons in post-order.
    private void recursiveGenAddons() {
        assert(areTilesFinished);

        if(parent != null) {
            this.grid = new TerrainGrid(cntGridRows, nGridCols, parent.grid, rowOffsetFromParent);
        }else{
            this.grid = new TerrainGrid(cntGridRows,nGridCols);
        }

        for (TerrainStructure child : children) {
            child.recursiveGenAddons();
        }
        generateElements(grid);
        grid.destroy();
    }

    public void generate() {
        tileGenerationWrapper();
        recursiveGenAddons();
    }
}