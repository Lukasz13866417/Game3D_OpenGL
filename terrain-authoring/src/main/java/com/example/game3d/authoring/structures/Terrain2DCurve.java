package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.Terrain;

/** Curve with independent yaw and pitch deltas plus optional angle fadeout. */
public class Terrain2DCurve extends AdvancedTerrainStructure {
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

    public static Builder builder() {
        return new Builder();
    }

    public Terrain2DCurve(
            int tilesToMake, float horizontalAngleDelta, float verticalAngleDelta) {
        this("handwritten:terrain-2d-curve", tilesToMake,
                horizontalAngleDelta, verticalAngleDelta,
                DEFAULT_RESET_HORIZONTAL_ANGLE, DEFAULT_RESET_VERTICAL_ANGLE, 0, 0);
    }

    public Terrain2DCurve(
            int tilesToMake, float horizontalAngleDelta, float verticalAngleDelta,
            boolean resetHorizontalAngle, boolean resetVerticalAngle) {
        this("handwritten:terrain-2d-curve", tilesToMake,
                horizontalAngleDelta, verticalAngleDelta,
                resetHorizontalAngle, resetVerticalAngle, 0, 0);
    }

    public Terrain2DCurve(
            int tilesToMake, float horizontalAngleDelta, float verticalAngleDelta,
            boolean resetHorizontalAngle, boolean resetVerticalAngle,
            int horizontalFadeoutTiles, int verticalFadeoutTiles) {
        this("handwritten:terrain-2d-curve", tilesToMake,
                horizontalAngleDelta, verticalAngleDelta,
                resetHorizontalAngle, resetVerticalAngle,
                horizontalFadeoutTiles, verticalFadeoutTiles);
    }

    public Terrain2DCurve(
            String sourcePrefix, int tilesToMake,
            float horizontalAngleDelta, float verticalAngleDelta,
            boolean resetHorizontalAngle, boolean resetVerticalAngle,
            int horizontalFadeoutTiles, int verticalFadeoutTiles) {
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
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            return;
        }
        int length = Math.min(3, columns);
        for (int i = 0; i < Math.min(2, rows); i++) {
            brush.reserveRandomFittingHorizontal(length,
                    StructureSupport.spikes(sourcePrefix, "spike-row-" + i, length));
        }
    }

    public static final class Builder {
        private String sourcePrefix = "handwritten:terrain-2d-curve";
        private Integer tilesToMake;
        private Float horizontalAngleDelta;
        private Float verticalAngleDelta;
        private boolean resetHorizontalAngle = DEFAULT_RESET_HORIZONTAL_ANGLE;
        private boolean resetVerticalAngle = DEFAULT_RESET_VERTICAL_ANGLE;
        private int horizontalFadeoutTiles;
        private int verticalFadeoutTiles;

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

        public Terrain2DCurve build() {
            if (tilesToMake == null || horizontalAngleDelta == null
                    || verticalAngleDelta == null) {
                throw new IllegalStateException(
                        "tilesToMake and both angle deltas must be set");
            }
            return new Terrain2DCurve(sourcePrefix, tilesToMake,
                    horizontalAngleDelta, verticalAngleDelta,
                    resetHorizontalAngle, resetVerticalAngle,
                    horizontalFadeoutTiles, verticalFadeoutTiles);
        }
    }
}
