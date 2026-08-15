package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.AddonBlueprint;
import com.example.game3d.authoring.BasicTerrainStructure;
import com.example.game3d.authoring.Terrain;

/** Straight basic-grid terrain with one explicitly selected legacy layout. */
public class BasicTerrainLine extends BasicTerrainStructure {
    public enum Layout {
        CENTER_VERTICAL_STRIPE,
        TWO_VERTICAL_STRIPES,
        MIDDLE_HORIZONTAL_BAND
    }

    private final String sourcePrefix;
    private final Layout layout;

    public BasicTerrainLine(int tilesToMake) {
        this("handwritten:basic-terrain-line", tilesToMake,
                Layout.CENTER_VERTICAL_STRIPE);
    }

    public BasicTerrainLine(int tilesToMake, Layout layout) {
        this("handwritten:basic-terrain-line", tilesToMake, layout);
    }

    public BasicTerrainLine(String sourcePrefix, int tilesToMake) {
        this(sourcePrefix, tilesToMake, Layout.CENTER_VERTICAL_STRIPE);
    }

    public BasicTerrainLine(String sourcePrefix, int tilesToMake, Layout layout) {
        super(StructureSupport.requireNonNegative(tilesToMake, "tilesToMake"));
        this.sourcePrefix = StructureSupport.requirePrefix(sourcePrefix);
        if (layout == null) {
            throw new IllegalArgumentException("layout == null");
        }
        this.layout = layout;
        this.name = sourcePrefix;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        for (int i = 0; i < tilesToMake; i++) {
            brush.addSegment(StructureSupport.tileId(sourcePrefix, i));
        }
    }

    @Override
    protected void generateAddons(Terrain.BasicGridBrush brush, int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            return;
        }
        switch (layout) {
            case CENTER_VERTICAL_STRIPE:
                placeCenterVerticalStripe(brush, rows, columns);
                break;
            case TWO_VERTICAL_STRIPES:
                placeTwoVerticalStripes(brush, rows, columns);
                break;
            case MIDDLE_HORIZONTAL_BAND:
                placeMiddleHorizontalBand(brush, rows, columns);
                break;
            default:
                throw new AssertionError(layout);
        }
    }

    private void placeCenterVerticalStripe(
            Terrain.BasicGridBrush brush, int rows, int columns) {
        int column = Math.max(1, Math.min(columns, (columns + 1) / 2));
        int length = Math.min(rows, 10);
        brush.reserveVertical(1, column, length,
                StructureSupport.spikes(sourcePrefix, "center", length));
    }

    private void placeTwoVerticalStripes(
            Terrain.BasicGridBrush brush, int rows, int columns) {
        int left = Math.max(1, Math.min(columns, columns / 3 + 1));
        int right = Math.max(1, Math.min(columns, (2 * columns) / 3 + 1));
        int length = Math.min(rows, 6);
        AddonBlueprint[] leftSpikes =
                StructureSupport.spikes(sourcePrefix, "left", length);
        AddonBlueprint[] rightSpikes =
                StructureSupport.spikes(sourcePrefix, "right", length);
        brush.reserveVertical(1, left, length, leftSpikes);
        brush.reserveVertical(1, right, length, rightSpikes);
    }

    private void placeMiddleHorizontalBand(
            Terrain.BasicGridBrush brush, int rows, int columns) {
        int row = Math.max(1, Math.min(rows, (rows + 1) / 2));
        int length = Math.max(1, Math.min(columns, Math.max(3, columns / 2)));
        int start = Math.max(1,
                Math.min(columns - length + 1, (columns - length) / 2 + 1));
        brush.reserveHorizontal(row, start, length,
                StructureSupport.spikes(sourcePrefix, "middle", length));
    }
}
