package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.BasicTerrainStructure;
import com.example.game3d.authoring.Terrain;

/** Basic-grid stairs retaining the three legacy layouts as explicit choices. */
public class BasicTerrainStairs extends BasicTerrainStructure {
    public enum Layout {
        CENTER_COLUMN,
        TOP_POTION,
        DUAL_COLUMNS
    }

    private final String sourcePrefix;
    private final int tilesPerStair;
    private final int stairCount;
    private final int emptyBetween;
    private final float horizontalAngleDelta;
    private final float jump;
    private final Layout layout;

    public static Builder builder() {
        return new Builder();
    }

    public BasicTerrainStairs(
            int tilesPerStair, int stairCount,
            float horizontalAngleDelta, float jump) {
        this("handwritten:basic-terrain-stairs", tilesPerStair, stairCount,
                0, horizontalAngleDelta, jump, Layout.CENTER_COLUMN);
    }

    public BasicTerrainStairs(
            int tilesPerStair, int stairCount, int emptyBetween,
            float horizontalAngleDelta, float jump) {
        this("handwritten:basic-terrain-stairs", tilesPerStair, stairCount,
                emptyBetween, horizontalAngleDelta, jump, Layout.CENTER_COLUMN);
    }

    public BasicTerrainStairs(
            String sourcePrefix, int tilesPerStair, int stairCount,
            int emptyBetween, float horizontalAngleDelta, float jump,
            Layout layout) {
        super(totalTiles(tilesPerStair, stairCount, emptyBetween));
        this.sourcePrefix = StructureSupport.requirePrefix(sourcePrefix);
        this.tilesPerStair = tilesPerStair;
        this.stairCount = stairCount;
        this.emptyBetween = emptyBetween;
        this.horizontalAngleDelta = StructureSupport.requireFinite(
                horizontalAngleDelta, "horizontalAngleDelta");
        this.jump = StructureSupport.requireFinite(jump, "jump");
        if (layout == null) {
            throw new IllegalArgumentException("layout == null");
        }
        this.layout = layout;
        this.name = sourcePrefix;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        brush.setCornerAlphas(0.5f, 0.5f);
        double horizontalStep = horizontalAngleDelta
                / (double) (tilesPerStair * stairCount);
        int ordinal = 0;
        for (int stair = 0; stair < stairCount; stair++) {
            for (int tile = 0; tile < tilesPerStair; tile++) {
                brush.addHorizontalAng(horizontalStep);
                brush.addSegment(StructureSupport.tileId(sourcePrefix, ordinal++));
            }
            if (stair < stairCount - 1) {
                for (int gap = 0; gap < emptyBetween; gap++) {
                    brush.addEmptySegment(StructureSupport.tileId(sourcePrefix, ordinal++));
                }
            }
            brush.liftUp(jump);
        }
        brush.setCornerAlphas(1f, 1f);
    }

    @Override
    protected void generateAddons(Terrain.BasicGridBrush brush, int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            return;
        }
        switch (layout) {
            case CENTER_COLUMN:
                int centerLength = Math.min(rows, 5);
                int centerColumn = Math.max(1, Math.min(columns, (columns + 1) / 2));
                brush.reserveVertical(1, centerColumn, centerLength,
                        StructureSupport.spikes(sourcePrefix, "center", centerLength));
                break;
            case TOP_POTION:
                int potionColumn = Math.max(1, Math.min(columns, columns / 2 + 1));
                brush.reserveHorizontal(rows, potionColumn, 1,
                        StructureSupport.potions(sourcePrefix, "top", 1));
                break;
            case DUAL_COLUMNS:
                int length = Math.min(rows, 4);
                int left = Math.max(1, Math.min(columns, columns / 3 + 1));
                int right = Math.max(1,
                        Math.min(columns, (2 * columns) / 3 + 1));
                brush.reserveVertical(1, left, length,
                        StructureSupport.spikes(sourcePrefix, "left", length));
                brush.reserveVertical(1, right, length,
                        StructureSupport.spikes(sourcePrefix, "right", length));
                break;
            default:
                throw new AssertionError(layout);
        }
    }

    private static int totalTiles(int tilesPerStair, int stairCount, int emptyBetween) {
        StructureSupport.requirePositive(tilesPerStair, "tilesPerStair");
        StructureSupport.requirePositive(stairCount, "stairCount");
        StructureSupport.requireNonNegative(emptyBetween, "emptyBetween");
        return Math.addExact(Math.multiplyExact(tilesPerStair, stairCount),
                Math.multiplyExact(emptyBetween, stairCount - 1));
    }

    public static final class Builder {
        private String sourcePrefix = "handwritten:basic-terrain-stairs";
        private Integer tilesPerStair;
        private Integer stairCount;
        private int emptyBetween;
        private Float horizontalAngleDelta;
        private Float jump;
        private Layout layout = Layout.CENTER_COLUMN;

        public Builder sourcePrefix(String value) { sourcePrefix = value; return this; }
        public Builder tilesPerStair(int value) { tilesPerStair = value; return this; }
        public Builder stairCount(int value) { stairCount = value; return this; }
        public Builder emptyBetween(int value) { emptyBetween = value; return this; }
        public Builder horizontalAngleDelta(float value) {
            horizontalAngleDelta = value; return this;
        }
        public Builder jump(float value) { jump = value; return this; }
        public Builder layout(Layout value) { layout = value; return this; }

        public BasicTerrainStairs build() {
            if (tilesPerStair == null || stairCount == null
                    || horizontalAngleDelta == null || jump == null) {
                throw new IllegalStateException(
                        "tilesPerStair, stairCount, horizontalAngleDelta and jump must be set");
            }
            return new BasicTerrainStairs(sourcePrefix, tilesPerStair, stairCount,
                    emptyBetween, horizontalAngleDelta, jump, layout);
        }
    }
}
