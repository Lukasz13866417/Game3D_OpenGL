package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.AddonBlueprint;
import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.BasicTerrainStructure;
import com.example.game3d.authoring.Terrain;

/** Raised stair flights with gaps and one seeded random spike per flight. */
public class TerrainStairs extends BasicTerrainStructure {
    private final String sourcePrefix;
    private final int tilesPerStair;
    private final int stairCount;
    private final int emptyBetween;
    private final float horizontalAngleDelta;
    private final float jump;

    public static Builder builder() {
        return new Builder();
    }

    public TerrainStairs(
            int tilesPerStair, int stairCount,
            float horizontalAngleDelta, float jump) {
        this("handwritten:terrain-stairs", tilesPerStair, stairCount,
                0, horizontalAngleDelta, jump);
    }

    public TerrainStairs(
            int tilesPerStair, int stairCount, int emptyBetween,
            float horizontalAngleDelta, float jump) {
        this("handwritten:terrain-stairs", tilesPerStair, stairCount,
                emptyBetween, horizontalAngleDelta, jump);
    }

    public TerrainStairs(
            String sourcePrefix, int tilesPerStair, int stairCount,
            int emptyBetween, float horizontalAngleDelta, float jump) {
        super(totalTiles(tilesPerStair, stairCount, emptyBetween));
        this.sourcePrefix = StructureSupport.requirePrefix(sourcePrefix);
        this.tilesPerStair = tilesPerStair;
        this.stairCount = stairCount;
        this.emptyBetween = emptyBetween;
        this.horizontalAngleDelta = StructureSupport.requireFinite(
                horizontalAngleDelta, "horizontalAngleDelta");
        this.jump = StructureSupport.requireFinite(jump, "jump");
        this.name = sourcePrefix;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        brush.setCornerAlphas(0.5f, 0.5f);
        double horizontalStep = horizontalAngleDelta
                / (double) (tilesPerStair * stairCount);
        brush.liftUp(jump);
        int gapOrdinal = 0;
        for (int stair = 0; stair < stairCount; stair++) {
            addChild(new RandomSpikeFlight(
                    sourcePrefix + ":flight:" + stair,
                    tilesPerStair, horizontalStep), brush);
            if (stair < stairCount - 1) {
                for (int gap = 0; gap < emptyBetween; gap++) {
                    brush.addEmptySegment(
                            sourcePrefix + ":gap:" + gapOrdinal++);
                }
            }
            // The pending lift after the last flight intentionally advances the next sibling.
            brush.liftUp(jump);
        }
        brush.setCornerAlphas(1f, 1f);
    }

    @Override
    protected void generateAddons(Terrain.BasicGridBrush brush, int rows, int columns) {
        // Each flight owns its seeded placement scope.
    }

    private static int totalTiles(int tilesPerStair, int stairCount, int emptyBetween) {
        StructureSupport.requirePositive(tilesPerStair, "tilesPerStair");
        StructureSupport.requirePositive(stairCount, "stairCount");
        StructureSupport.requireNonNegative(emptyBetween, "emptyBetween");
        return Math.addExact(Math.multiplyExact(tilesPerStair, stairCount),
                Math.multiplyExact(emptyBetween, stairCount - 1));
    }

    private static final class RandomSpikeFlight extends AdvancedTerrainStructure {
        private final String sourcePrefix;
        private final double horizontalStep;

        RandomSpikeFlight(String sourcePrefix, int tiles, double horizontalStep) {
            super(tiles);
            this.sourcePrefix = sourcePrefix;
            this.horizontalStep = horizontalStep;
            this.name = sourcePrefix;
        }

        @Override
        protected void generateTiles(Terrain.TileBrush brush) {
            for (int i = 0; i < tilesToMake; i++) {
                brush.addHorizontalAng(horizontalStep);
                brush.addSegment(StructureSupport.tileId(sourcePrefix, i));
            }
        }

        @Override
        protected void generateAddons(
                Terrain.AdvancedGridBrush brush, int rows, int columns) {
            if (rows > 0 && columns > 0) {
                brush.reserveKRandomFields(new AddonBlueprint[] {
                        AddonBlueprint.deathSpike(sourcePrefix + ":spike:0")
                });
            }
        }
    }

    public static final class Builder {
        private String sourcePrefix = "handwritten:terrain-stairs";
        private Integer tilesPerStair;
        private Integer stairCount;
        private int emptyBetween;
        private Float horizontalAngleDelta;
        private Float jump;

        public Builder sourcePrefix(String value) { sourcePrefix = value; return this; }
        public Builder tilesPerStair(int value) { tilesPerStair = value; return this; }
        public Builder stairCount(int value) { stairCount = value; return this; }
        public Builder emptyBetween(int value) { emptyBetween = value; return this; }
        public Builder horizontalAngleDelta(float value) {
            horizontalAngleDelta = value; return this;
        }
        public Builder jump(float value) { jump = value; return this; }

        public TerrainStairs build() {
            if (tilesPerStair == null || stairCount == null
                    || horizontalAngleDelta == null || jump == null) {
                throw new IllegalStateException(
                        "tilesPerStair, stairCount, horizontalAngleDelta and jump must be set");
            }
            return new TerrainStairs(sourcePrefix, tilesPerStair, stairCount,
                    emptyBetween, horizontalAngleDelta, jump);
        }
    }
}
