package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.BasicTerrainStructure;
import com.example.game3d.authoring.Terrain;

/** Basic-grid horizontal curve with an explicitly selected legacy layout. */
public class BasicTerrainCurve extends BasicTerrainStructure {
    public enum Layout {
        START_VERTICAL_SET,
        END_POTION,
        ALTERNATING_COLUMNS
    }

    private final String sourcePrefix;
    private final float horizontalAngleDelta;
    private final Layout layout;

    public BasicTerrainCurve(int tilesToMake, float horizontalAngleDelta) {
        this("handwritten:basic-terrain-curve", tilesToMake,
                horizontalAngleDelta, Layout.START_VERTICAL_SET);
    }

    public BasicTerrainCurve(
            int tilesToMake, float horizontalAngleDelta, Layout layout) {
        this("handwritten:basic-terrain-curve", tilesToMake,
                horizontalAngleDelta, layout);
    }

    public BasicTerrainCurve(
            String sourcePrefix, int tilesToMake, float horizontalAngleDelta) {
        this(sourcePrefix, tilesToMake, horizontalAngleDelta,
                Layout.START_VERTICAL_SET);
    }

    public BasicTerrainCurve(
            String sourcePrefix, int tilesToMake, float horizontalAngleDelta,
            Layout layout) {
        super(StructureSupport.requirePositive(tilesToMake, "tilesToMake"));
        this.sourcePrefix = StructureSupport.requirePrefix(sourcePrefix);
        this.horizontalAngleDelta =
                StructureSupport.requireFinite(horizontalAngleDelta,
                        "horizontalAngleDelta");
        if (layout == null) {
            throw new IllegalArgumentException("layout == null");
        }
        this.layout = layout;
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
    protected void generateAddons(Terrain.BasicGridBrush brush, int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            return;
        }
        switch (layout) {
            case START_VERTICAL_SET:
                int startLength = Math.min(rows, 5);
                int startColumn = Math.max(1, Math.min(columns, columns / 3 + 1));
                brush.reserveVertical(1, startColumn, startLength,
                        StructureSupport.spikes(sourcePrefix, "start", startLength));
                break;
            case END_POTION:
                int potionColumn = Math.max(1,
                        Math.min(columns, (2 * columns) / 3 + 1));
                brush.reserveHorizontal(rows, potionColumn, 1,
                        StructureSupport.potions(sourcePrefix, "end", 1));
                break;
            case ALTERNATING_COLUMNS:
                int length = Math.min(rows, 4);
                for (int column = 1; column <= columns; column += 2) {
                    brush.reserveVertical(1, column, length,
                            StructureSupport.spikes(sourcePrefix,
                                    "column-" + column, length));
                }
                break;
            default:
                throw new AssertionError(layout);
        }
    }
}
