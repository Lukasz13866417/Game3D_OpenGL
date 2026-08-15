package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.Terrain;

/** Generates a sequence of gaps without solid geometry. */
public class TerrainEmptySegments extends AdvancedTerrainStructure {
    private final String sourcePrefix;

    public TerrainEmptySegments(int emptySegments) {
        this("handwritten:terrain-empty-segments", emptySegments);
    }

    public TerrainEmptySegments(String sourcePrefix, int emptySegments) {
        super(StructureSupport.requireNonNegative(emptySegments, "emptySegments"));
        this.sourcePrefix = StructureSupport.requirePrefix(sourcePrefix);
        this.name = sourcePrefix;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        for (int i = 0; i < tilesToMake; i++) {
            brush.addEmptySegment(StructureSupport.tileId(sourcePrefix, i));
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int rows, int columns) {
    }
}
