package com.example.game3d_opengl.game.terrain.terrain_structures;

import com.example.game3d_opengl.game.terrain.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TileProfile;

public class TerrainBoostRamp extends AdvancedTerrainStructure {
    private final int rampTiles;
    private final int gapTiles;
    private final int landingTiles;
    private final float launchAngleDelta;

    public static Builder builder() {
        return new Builder();
    }

    public TerrainBoostRamp(int rampTiles, int gapTiles, int landingTiles, float launchAngleDelta) {
        super(rampTiles + gapTiles + landingTiles);
        this.rampTiles = rampTiles;
        this.gapTiles = gapTiles;
        this.landingTiles = landingTiles;
        this.launchAngleDelta = launchAngleDelta;
    }

    public int getGapTiles() {
        return gapTiles;
    }

    public int getLandingTiles() {
        return landingTiles;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        float angleStep = rampTiles > 0 ? launchAngleDelta / (float) rampTiles : 0f;
        float normalBrightness = TileProfile.NORMAL.getBrightnessMultiplier();
        for (int i = 0; i < rampTiles; ++i) {
            TileProfile profile = (i == rampTiles - 1)
                    ? TileProfile.BOOST_RAMP_LAUNCH
                    : TileProfile.BOOST_RAMP;
            float brightnessT = rampTiles <= 1 ? 1f : (float) i / (float) (rampTiles - 1);
            float brightnessMultiplier =
                    normalBrightness + (profile.getBrightnessMultiplier() - normalBrightness) * brightnessT;
            brush.setUpcomingTileProfile(profile);
            brush.setUpcomingBrightnessMultiplier(brightnessMultiplier);
            brush.addVerticalAng(angleStep);
            brush.addSegment();
        }

        brush.setUpcomingTileProfile(TileProfile.NORMAL);
        brush.setUpcomingBrightnessMultiplier(normalBrightness);
        brush.setVerticalAng(0f);
        for (int i = 0; i < gapTiles; ++i) {
            brush.addEmptySegment();
        }
        for (int i = 0; i < landingTiles; ++i) {
            brush.addSegment();
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
        // Keep the landing predictable so the speed/air-control change reads clearly.
    }

    public static final class Builder {
        private Integer rampTiles;
        private Integer gapTiles;
        private Integer landingTiles;
        private Float launchAngleDelta;

        public Builder rampTiles(int rampTiles) {
            this.rampTiles = rampTiles;
            return this;
        }

        public Builder gapTiles(int gapTiles) {
            this.gapTiles = gapTiles;
            return this;
        }

        public Builder landingTiles(int landingTiles) {
            this.landingTiles = landingTiles;
            return this;
        }

        public Builder launchAngleDelta(float launchAngleDelta) {
            this.launchAngleDelta = launchAngleDelta;
            return this;
        }

        public TerrainBoostRamp build() {
            if (rampTiles == null || rampTiles <= 0) {
                throw new IllegalStateException("rampTiles must be > 0");
            }
            if (gapTiles == null || gapTiles < 0) {
                throw new IllegalStateException("gapTiles must be >= 0");
            }
            if (landingTiles == null || landingTiles <= 0) {
                throw new IllegalStateException("landingTiles must be > 0");
            }
            if (launchAngleDelta == null) {
                throw new IllegalStateException("launchAngleDelta not set");
            }
            return new TerrainBoostRamp(rampTiles, gapTiles, landingTiles, launchAngleDelta);
        }
    }
}
