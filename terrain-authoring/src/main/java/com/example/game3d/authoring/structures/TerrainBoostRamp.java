package com.example.game3d.authoring.structures;

import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.Terrain;
import com.example.game3d.core.terrain.SurfaceProperties;

/** Boost ramp, optional gap, and flat landing using canonical core surfaces. */
public class TerrainBoostRamp extends AdvancedTerrainStructure {
    private static final float BOOST_VISUAL_BRIGHTNESS = 1.32f;

    private final String sourcePrefix;
    private final int rampTiles;
    private final int gapTiles;
    private final int landingTiles;
    private final float launchAngleDelta;

    public static Builder builder() {
        return new Builder();
    }

    public TerrainBoostRamp(
            int rampTiles, int gapTiles, int landingTiles,
            float launchAngleDelta) {
        this("handwritten:terrain-boost-ramp", rampTiles, gapTiles,
                landingTiles, launchAngleDelta);
    }

    public TerrainBoostRamp(
            String sourcePrefix, int rampTiles, int gapTiles, int landingTiles,
            float launchAngleDelta) {
        super(totalTiles(rampTiles, gapTiles, landingTiles));
        this.sourcePrefix = StructureSupport.requirePrefix(sourcePrefix);
        this.rampTiles = rampTiles;
        this.gapTiles = gapTiles;
        this.landingTiles = landingTiles;
        this.launchAngleDelta = StructureSupport.requireFinite(
                launchAngleDelta, "launchAngleDelta");
        this.name = sourcePrefix;
    }

    public int getGapTiles() {
        return gapTiles;
    }

    public int getLandingTiles() {
        return landingTiles;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        double angleStep = launchAngleDelta / rampTiles;
        int ordinal = 0;
        for (int i = 0; i < rampTiles; i++) {
            float t = rampTiles <= 1 ? 1f : (float) i / (rampTiles - 1);
            brush.setUpcomingSurface(i == rampTiles - 1
                    ? SurfaceProperties.BOOST_RAMP_LAUNCH
                    : SurfaceProperties.BOOST_RAMP);
            brush.setUpcomingBrightnessMultiplier(
                    1f + (BOOST_VISUAL_BRIGHTNESS - 1f) * t);
            brush.addVerticalAng(angleStep);
            brush.addSegment(StructureSupport.tileId(sourcePrefix, ordinal++));
        }

        brush.setUpcomingSurface(SurfaceProperties.NORMAL);
        brush.setUpcomingBrightnessMultiplier(1f);
        brush.setVerticalAng(0.0);
        for (int i = 0; i < gapTiles; i++) {
            brush.addEmptySegment(StructureSupport.tileId(sourcePrefix, ordinal++));
        }
        for (int i = 0; i < landingTiles; i++) {
            brush.addSegment(StructureSupport.tileId(sourcePrefix, ordinal++));
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int rows, int columns) {
        // Intentionally clear so launch and landing remain readable and predictable.
    }

    private static int totalTiles(int rampTiles, int gapTiles, int landingTiles) {
        StructureSupport.requirePositive(rampTiles, "rampTiles");
        StructureSupport.requireNonNegative(gapTiles, "gapTiles");
        StructureSupport.requirePositive(landingTiles, "landingTiles");
        return Math.addExact(Math.addExact(rampTiles, gapTiles), landingTiles);
    }

    public static final class Builder {
        private String sourcePrefix = "handwritten:terrain-boost-ramp";
        private Integer rampTiles;
        private Integer gapTiles;
        private Integer landingTiles;
        private Float launchAngleDelta;

        public Builder sourcePrefix(String value) { sourcePrefix = value; return this; }
        public Builder rampTiles(int value) { rampTiles = value; return this; }
        public Builder gapTiles(int value) { gapTiles = value; return this; }
        public Builder landingTiles(int value) { landingTiles = value; return this; }
        public Builder launchAngleDelta(float value) {
            launchAngleDelta = value; return this;
        }

        public TerrainBoostRamp build() {
            if (rampTiles == null || gapTiles == null || landingTiles == null
                    || launchAngleDelta == null) {
                throw new IllegalStateException(
                        "rampTiles, gapTiles, landingTiles and launchAngleDelta must be set");
            }
            return new TerrainBoostRamp(sourcePrefix, rampTiles, gapTiles,
                    landingTiles, launchAngleDelta);
        }
    }
}
