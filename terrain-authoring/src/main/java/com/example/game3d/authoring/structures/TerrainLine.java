package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.AddonBlueprint;
import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.Terrain;

/** Straight advanced-grid terrain with the legacy randomized spike stripes. */
public class TerrainLine extends AdvancedTerrainStructure {
    private final String sourcePrefix;

    public TerrainLine(int tilesToMake) {
        this("handwritten:terrain-line", tilesToMake);
    }

    public TerrainLine(String sourcePrefix, int tilesToMake) {
        super(StructureSupport.requireNonNegative(tilesToMake, "tilesToMake"));
        this.sourcePrefix = StructureSupport.requirePrefix(sourcePrefix);
        this.name = sourcePrefix;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        for (int i = 0; i < tilesToMake; i++) {
            brush.addSegment(StructureSupport.tileId(sourcePrefix, i));
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            return;
        }
        int horizontalLength = Math.min(2, columns);
        for (int i = 0; i < Math.min(3, rows); i++) {
            brush.reserveRandomFittingHorizontal(
                    horizontalLength,
                    StructureSupport.spikes(sourcePrefix, "horizontal-" + i,
                            horizontalLength));
        }
        int verticalLength = Math.min(rows, 10);
        if (verticalLength > 0) {
            brush.reserveRandomFittingVertical(
                    verticalLength,
                    StructureSupport.spikes(sourcePrefix, "vertical", verticalLength));
        }
    }
}
