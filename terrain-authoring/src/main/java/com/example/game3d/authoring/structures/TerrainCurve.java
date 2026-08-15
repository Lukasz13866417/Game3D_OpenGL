package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.Terrain;

/** Horizontal curve with legacy randomized spike stripes and potion placements. */
public class TerrainCurve extends AdvancedTerrainStructure {
    private final String sourcePrefix;
    private final float horizontalAngleDelta;

    public TerrainCurve(int tilesToMake, float horizontalAngleDelta) {
        this("handwritten:terrain-curve", tilesToMake, horizontalAngleDelta);
    }

    public TerrainCurve(
            String sourcePrefix, int tilesToMake, float horizontalAngleDelta) {
        super(StructureSupport.requirePositive(tilesToMake, "tilesToMake"));
        this.sourcePrefix = StructureSupport.requirePrefix(sourcePrefix);
        this.horizontalAngleDelta =
                StructureSupport.requireFinite(horizontalAngleDelta,
                        "horizontalAngleDelta");
        this.name = sourcePrefix;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        double step = horizontalAngleDelta / tilesToMake;
        for (int i = 0; i < tilesToMake; i++) {
            brush.addHorizontalAng(step);
            brush.addSegment(StructureSupport.tileId(sourcePrefix, i));
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            return;
        }
        int spikeLength = Math.min(rows, 5);
        for (int i = 0; i < 2; i++) {
            brush.reserveRandomFittingVertical(spikeLength,
                    StructureSupport.spikes(sourcePrefix, "spike-stripe-" + i,
                            spikeLength));
        }
        for (int i = 0; i < 2; i++) {
            brush.reserveRandomFittingVertical(1,
                    StructureSupport.potions(sourcePrefix, "potion-" + i, 1));
        }
    }
}
