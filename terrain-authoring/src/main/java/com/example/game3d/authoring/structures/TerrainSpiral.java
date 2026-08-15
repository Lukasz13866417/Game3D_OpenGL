package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.Terrain;

/** Constant-pitch horizontal spiral with seeded full-width spike rows. */
public class TerrainSpiral extends AdvancedTerrainStructure {
    private final String sourcePrefix;
    private final float horizontalAngleDelta;
    private final float verticalAngle;

    public static Builder builder() {
        return new Builder();
    }

    public TerrainSpiral(
            int tilesToMake, float horizontalAngleDelta, float verticalAngle) {
        this("handwritten:terrain-spiral", tilesToMake,
                horizontalAngleDelta, verticalAngle);
    }

    public TerrainSpiral(
            String sourcePrefix, int tilesToMake,
            float horizontalAngleDelta, float verticalAngle) {
        super(StructureSupport.requirePositive(tilesToMake, "tilesToMake"));
        this.sourcePrefix = StructureSupport.requirePrefix(sourcePrefix);
        this.horizontalAngleDelta = StructureSupport.requireFinite(
                horizontalAngleDelta, "horizontalAngleDelta");
        this.verticalAngle = StructureSupport.requireFinite(
                verticalAngle, "verticalAngle");
        this.name = sourcePrefix;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        double horizontalStep = horizontalAngleDelta / tilesToMake;
        brush.addVerticalAng(verticalAngle);
        for (int i = 0; i < tilesToMake; i++) {
            brush.addHorizontalAng(horizontalStep);
            brush.addSegment(StructureSupport.tileId(sourcePrefix, i));
        }
        brush.addVerticalAng(-verticalAngle);
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            return;
        }
        for (int i = 0; i < Math.min(rows, 2); i++) {
            brush.reserveRandomFittingHorizontal(columns,
                    StructureSupport.spikes(sourcePrefix, "spike-row-" + i, columns));
        }
    }

    public static final class Builder {
        private String sourcePrefix = "handwritten:terrain-spiral";
        private Integer tilesToMake;
        private Float horizontalAngleDelta;
        private Float verticalAngle;

        public Builder sourcePrefix(String value) { sourcePrefix = value; return this; }
        public Builder tilesToMake(int value) { tilesToMake = value; return this; }
        public Builder horizontalAngleDelta(float value) {
            horizontalAngleDelta = value; return this;
        }
        public Builder verticalAngle(float value) { verticalAngle = value; return this; }

        public TerrainSpiral build() {
            if (tilesToMake == null || horizontalAngleDelta == null
                    || verticalAngle == null) {
                throw new IllegalStateException(
                        "tilesToMake, horizontalAngleDelta and verticalAngle must be set");
            }
            return new TerrainSpiral(sourcePrefix, tilesToMake,
                    horizontalAngleDelta, verticalAngle);
        }
    }
}
