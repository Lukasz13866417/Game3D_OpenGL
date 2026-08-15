package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.Terrain;

/** Alternates two solids and one gap, then exercises seeded fitting placement. */
public class EmptySegmentTestStructure extends AdvancedTerrainStructure {
    private final String sourcePrefix;

    public EmptySegmentTestStructure(int tilesToMake) {
        this("handwritten:empty-segment-test", tilesToMake);
    }

    public EmptySegmentTestStructure(String sourcePrefix, int tilesToMake) {
        super(StructureSupport.requireNonNegative(tilesToMake, "tilesToMake"));
        this.sourcePrefix = StructureSupport.requirePrefix(sourcePrefix);
        this.name = sourcePrefix;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        for (int i = 0; i < tilesToMake; i++) {
            if (i % 3 == 2) {
                brush.addEmptySegment(StructureSupport.tileId(sourcePrefix, i));
            } else {
                brush.addSegment(StructureSupport.tileId(sourcePrefix, i));
            }
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int rows, int columns) {
        int length = Math.min(3, rows);
        if (length > 0 && columns > 0) {
            brush.reserveRandomFittingVertical(length,
                    StructureSupport.spikes(sourcePrefix, "spike", length));
        }
    }
}
