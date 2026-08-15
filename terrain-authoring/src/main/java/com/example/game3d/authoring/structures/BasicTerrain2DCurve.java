package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.BasicTerrainStructure;
import com.example.game3d.authoring.Terrain;

/** Basic-grid 2D curve with optional angle fadeout and an explicit addon layout. */
public class BasicTerrain2DCurve extends BasicTerrainStructure {
    public enum Layout {
        CENTER_SHORT,
        LEFT_RIGHT,
        HORIZONTAL_ROW
    }

    private static final boolean DEFAULT_RESET_HORIZONTAL_ANGLE = false;
    private static final boolean DEFAULT_RESET_VERTICAL_ANGLE = true;

    private final String sourcePrefix;
    private final int curveTiles;
    private final float horizontalAngleDelta;
    private final float verticalAngleDelta;
    private final boolean resetHorizontalAngle;
    private final boolean resetVerticalAngle;
    private final int horizontalFadeoutTiles;
    private final int verticalFadeoutTiles;
    private final Layout layout;

    public static Builder builder() {
        return new Builder();
    }

    public BasicTerrain2DCurve(
            int tilesToMake, float horizontalAngleDelta, float verticalAngleDelta) {
        this("handwritten:basic-terrain-2d-curve", tilesToMake,
                horizontalAngleDelta, verticalAngleDelta,
                DEFAULT_RESET_HORIZONTAL_ANGLE, DEFAULT_RESET_VERTICAL_ANGLE,
                0, 0, Layout.CENTER_SHORT);
    }

    public BasicTerrain2DCurve(
            int tilesToMake, float horizontalAngleDelta, float verticalAngleDelta,
            boolean resetHorizontalAngle, boolean resetVerticalAngle) {
        this("handwritten:basic-terrain-2d-curve", tilesToMake,
                horizontalAngleDelta, verticalAngleDelta,
                resetHorizontalAngle, resetVerticalAngle,
                0, 0, Layout.CENTER_SHORT);
    }

    public BasicTerrain2DCurve(
            int tilesToMake, float horizontalAngleDelta, float verticalAngleDelta,
            boolean resetHorizontalAngle, boolean resetVerticalAngle,
            int horizontalFadeoutTiles, int verticalFadeoutTiles) {
        this("handwritten:basic-terrain-2d-curve", tilesToMake,
                horizontalAngleDelta, verticalAngleDelta,
                resetHorizontalAngle, resetVerticalAngle,
                horizontalFadeoutTiles, verticalFadeoutTiles,
                Layout.CENTER_SHORT);
    }

    public BasicTerrain2DCurve(
            String sourcePrefix, int tilesToMake,
            float horizontalAngleDelta, float verticalAngleDelta,
            boolean resetHorizontalAngle, boolean resetVerticalAngle,
            int horizontalFadeoutTiles, int verticalFadeoutTiles,
            Layout layout) {
        super(CurveCommandEmitter.totalTiles(
                tilesToMake, resetHorizontalAngle, horizontalFadeoutTiles,
                resetVerticalAngle, verticalFadeoutTiles));
        this.sourcePrefix = StructureSupport.requirePrefix(sourcePrefix);
        this.curveTiles = tilesToMake;
        this.horizontalAngleDelta = StructureSupport.requireFinite(
                horizontalAngleDelta, "horizontalAngleDelta");
        this.verticalAngleDelta = StructureSupport.requireFinite(
                verticalAngleDelta, "verticalAngleDelta");
        this.resetHorizontalAngle = resetHorizontalAngle;
        this.resetVerticalAngle = resetVerticalAngle;
        this.horizontalFadeoutTiles = horizontalFadeoutTiles;
        this.verticalFadeoutTiles = verticalFadeoutTiles;
        if (layout == null) {
            throw new IllegalArgumentException("layout == null");
        }
        this.layout = layout;
        this.name = sourcePrefix;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        CurveCommandEmitter.emit(brush, sourcePrefix, curveTiles,
                horizontalAngleDelta, verticalAngleDelta,
                resetHorizontalAngle, resetVerticalAngle,
                horizontalFadeoutTiles, verticalFadeoutTiles);
    }

    @Override
    protected void generateAddons(Terrain.BasicGridBrush brush, int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            return;
        }
        switch (layout) {
            case CENTER_SHORT:
                int centerLength = Math.min(3, rows);
                int centerColumn = Math.max(1, Math.min(columns, (columns + 1) / 2));
                brush.reserveVertical(1, centerColumn, centerLength,
                        StructureSupport.spikes(sourcePrefix, "center", centerLength));
                break;
            case LEFT_RIGHT:
                int sideLength = Math.min(2, rows);
                int left = Math.max(1, Math.min(columns, columns / 4 + 1));
                int right = Math.max(1,
                        Math.min(columns, (3 * columns) / 4 + 1));
                brush.reserveVertical(1, left, sideLength,
                        StructureSupport.spikes(sourcePrefix, "left", sideLength));
                brush.reserveVertical(1, right, sideLength,
                        StructureSupport.spikes(sourcePrefix, "right", sideLength));
                break;
            case HORIZONTAL_ROW:
                int row = Math.max(1, Math.min(rows, rows / 3 + 1));
                int length = Math.min(3, columns);
                int start = Math.max(1,
                        Math.min(columns - length + 1, columns / 3 + 1));
                brush.reserveHorizontal(row, start, length,
                        StructureSupport.spikes(sourcePrefix, "row", length));
                break;
            default:
                throw new AssertionError(layout);
        }
    }

    public static final class Builder {
        private String sourcePrefix = "handwritten:basic-terrain-2d-curve";
        private Integer tilesToMake;
        private Float horizontalAngleDelta;
        private Float verticalAngleDelta;
        private boolean resetHorizontalAngle = DEFAULT_RESET_HORIZONTAL_ANGLE;
        private boolean resetVerticalAngle = DEFAULT_RESET_VERTICAL_ANGLE;
        private int horizontalFadeoutTiles;
        private int verticalFadeoutTiles;
        private Layout layout = Layout.CENTER_SHORT;

        public Builder sourcePrefix(String value) { sourcePrefix = value; return this; }
        public Builder tilesToMake(int value) { tilesToMake = value; return this; }
        public Builder horizontalAngleDelta(float value) {
            horizontalAngleDelta = value; return this;
        }
        public Builder verticalAngleDelta(float value) {
            verticalAngleDelta = value; return this;
        }
        public Builder resetHorizontalAngle(boolean value) {
            resetHorizontalAngle = value; return this;
        }
        public Builder resetVerticalAngle(boolean value) {
            resetVerticalAngle = value; return this;
        }
        public Builder horizontalAngleFadeoutTiles(int value) {
            horizontalFadeoutTiles = value; return this;
        }
        public Builder verticalAngleFadeoutTiles(int value) {
            verticalFadeoutTiles = value; return this;
        }
        public Builder layout(Layout value) { layout = value; return this; }

        public BasicTerrain2DCurve build() {
            if (tilesToMake == null || horizontalAngleDelta == null
                    || verticalAngleDelta == null) {
                throw new IllegalStateException(
                        "tilesToMake and both angle deltas must be set");
            }
            return new BasicTerrain2DCurve(sourcePrefix, tilesToMake,
                    horizontalAngleDelta, verticalAngleDelta,
                    resetHorizontalAngle, resetVerticalAngle,
                    horizontalFadeoutTiles, verticalFadeoutTiles, layout);
        }
    }
}
