package com.example.game3d_opengl.game.terrain.terrain_structures;

import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.terrain_api.main.BasicTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.game.util.GameRandom;

public class BasicTerrain2DCurve extends BasicTerrainStructure {
    private static final boolean DEFAULT_RESET_HORIZONTAL_ANGLE = false;
    private static final boolean DEFAULT_RESET_VERTICAL_ANGLE = true;
    private static final int DEFAULT_HORIZONTAL_FADEOUT_TILES = 0;
    private static final int DEFAULT_VERTICAL_FADEOUT_TILES = 0;

    private final int curveTilesToMake;
    private final float dAngHor, dAngVer;
    private final boolean resetHorizontalAngle;
    private final boolean resetVerticalAngle;
    private final int horizontalFadeoutTiles;
    private final int verticalFadeoutTiles;

    public static Builder builder() {
        return new Builder();
    }

    public BasicTerrain2DCurve(int tilesToMake, float dAngHor, float dAngVer) {
        this(
                tilesToMake,
                dAngHor,
                dAngVer,
                DEFAULT_RESET_HORIZONTAL_ANGLE,
                DEFAULT_RESET_VERTICAL_ANGLE,
                DEFAULT_HORIZONTAL_FADEOUT_TILES,
                DEFAULT_VERTICAL_FADEOUT_TILES
        );
    }

    public BasicTerrain2DCurve(
            int tilesToMake,
            float dAngHor,
            float dAngVer,
            boolean resetHorizontalAngle,
            boolean resetVerticalAngle
    ) {
        this(
                tilesToMake,
                dAngHor,
                dAngVer,
                resetHorizontalAngle,
                resetVerticalAngle,
                DEFAULT_HORIZONTAL_FADEOUT_TILES,
                DEFAULT_VERTICAL_FADEOUT_TILES
        );
    }

    public BasicTerrain2DCurve(
            int tilesToMake,
            float dAngHor,
            float dAngVer,
            boolean resetHorizontalAngle,
            boolean resetVerticalAngle,
            int horizontalFadeoutTiles,
            int verticalFadeoutTiles
    ) {
        super(computeTotalTiles(
                tilesToMake,
                resetHorizontalAngle,
                horizontalFadeoutTiles,
                resetVerticalAngle,
                verticalFadeoutTiles
        ));
        this.curveTilesToMake = tilesToMake;
        this.dAngHor = dAngHor;
        this.dAngVer = dAngVer;
        this.resetHorizontalAngle = resetHorizontalAngle;
        this.resetVerticalAngle = resetVerticalAngle;
        this.horizontalFadeoutTiles = requireNonNegative(horizontalFadeoutTiles, "horizontalFadeoutTiles");
        this.verticalFadeoutTiles = requireNonNegative(verticalFadeoutTiles, "verticalFadeoutTiles");
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        float angHorPerTile = dAngHor / (float) (curveTilesToMake);
        float angVerPerTile = dAngVer / (float) (curveTilesToMake);
        for (int i = 0; i < curveTilesToMake; ++i) {
            brush.addHorizontalAng(angHorPerTile);
            brush.addVerticalAng(angVerPerTile);
            brush.addSegment();
        }
        int activeHorizontalFadeoutTiles = resetHorizontalAngle ? horizontalFadeoutTiles : 0;
        int activeVerticalFadeoutTiles = resetVerticalAngle ? verticalFadeoutTiles : 0;
        if (resetVerticalAngle && activeVerticalFadeoutTiles == 0) {
            brush.addVerticalAng(-dAngVer);
        }
        if (resetHorizontalAngle && activeHorizontalFadeoutTiles == 0) {
            brush.addHorizontalAng(-dAngHor);
        }
        int fadeoutTileCount = Math.max(activeHorizontalFadeoutTiles, activeVerticalFadeoutTiles);
        if (fadeoutTileCount <= 0) {
            return;
        }
        float horizontalFadeoutStep = activeHorizontalFadeoutTiles > 0
                ? -dAngHor / (float) activeHorizontalFadeoutTiles
                : 0f;
        float verticalFadeoutStep = activeVerticalFadeoutTiles > 0
                ? -dAngVer / (float) activeVerticalFadeoutTiles
                : 0f;
        for (int i = 0; i < fadeoutTileCount; ++i) {
            if (i < activeHorizontalFadeoutTiles) {
                brush.addHorizontalAng(horizontalFadeoutStep);
            }
            if (i < activeVerticalFadeoutTiles) {
                brush.addVerticalAng(verticalFadeoutStep);
            }
            brush.addSegment();
        }
    }

    @Override
    protected void generateAddons(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        if (nRows <= 0 || nCols <= 0) return;
        int variant = GameRandom.randInt(0, 2);
        switch (variant) {
            case 0:
                placeCenterShort(brush, nRows, nCols);
                break;
            case 1:
                placeLeftRight(brush, nRows, nCols);
                break;
            case 2:
                placeHorizontalRow(brush, nRows, nCols);
                break;
        }
    }

    private void placeCenterShort(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        int length = Math.min(3, nRows);
        int col = Math.max(1, Math.min(nCols, (nCols + 1) / 2));
        Addon[] addons = makeSpikes(length);
        brush.reserveVertical(1, col, length, addons);
    }

    private void placeLeftRight(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        int length = Math.min(2, nRows);
        int left = Math.max(1, Math.min(nCols, nCols / 4 + 1));
        int right = Math.max(1, Math.min(nCols, (3 * nCols) / 4 + 1));
        Addon[] a = makeSpikes(length);
        Addon[] b = makeSpikes(length);
        brush.reserveVertical(1, left, length, a);
        brush.reserveVertical(1, right, length, b);
    }

    private void placeHorizontalRow(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        int row = Math.max(1, Math.min(nRows, nRows / 3 + 1));
        int length = Math.min(3, nCols);
        int startCol = Math.max(1, Math.min(nCols - length + 1, nCols / 3 + 1));
        Addon[] addons = makeSpikes(length);
        brush.reserveHorizontal(row, startCol, length, addons);
    }

    private Addon[] makeSpikes(int length) {
        Addon[] addons = new Addon[length];
        for (int j = 0; j < addons.length; ++j) addons[j] = DeathSpike.createDeathSpike();
        return addons;
    }

    public static final class Builder {
        private Integer tilesToMake;
        private Float dAngHor;
        private Float dAngVer;
        private boolean resetHorizontalAngle = DEFAULT_RESET_HORIZONTAL_ANGLE;
        private boolean resetVerticalAngle = DEFAULT_RESET_VERTICAL_ANGLE;
        private int horizontalFadeoutTiles = DEFAULT_HORIZONTAL_FADEOUT_TILES;
        private int verticalFadeoutTiles = DEFAULT_VERTICAL_FADEOUT_TILES;

        public Builder tilesToMake(int tilesToMake) {
            this.tilesToMake = tilesToMake;
            return this;
        }

        public Builder horizontalAngleDelta(float dAngHor) {
            this.dAngHor = dAngHor;
            return this;
        }

        public Builder verticalAngleDelta(float dAngVer) {
            this.dAngVer = dAngVer;
            return this;
        }

        public Builder resetHorizontalAngle(boolean resetHorizontalAngle) {
            this.resetHorizontalAngle = resetHorizontalAngle;
            return this;
        }

        public Builder resetVerticalAngle(boolean resetVerticalAngle) {
            this.resetVerticalAngle = resetVerticalAngle;
            return this;
        }

        public Builder horizontalAngleFadeoutTiles(int horizontalFadeoutTiles) {
            this.horizontalFadeoutTiles = horizontalFadeoutTiles;
            return this;
        }

        public Builder verticalAngleFadeoutTiles(int verticalFadeoutTiles) {
            this.verticalFadeoutTiles = verticalFadeoutTiles;
            return this;
        }

        public BasicTerrain2DCurve build() {
            if (tilesToMake == null) {
                throw new IllegalStateException("tilesToMake not set");
            }
            if (dAngHor == null) {
                throw new IllegalStateException("horizontalAngleDelta not set");
            }
            if (dAngVer == null) {
                throw new IllegalStateException("verticalAngleDelta not set");
            }
            return new BasicTerrain2DCurve(
                    tilesToMake,
                    dAngHor,
                    dAngVer,
                    resetHorizontalAngle,
                    resetVerticalAngle,
                    horizontalFadeoutTiles,
                    verticalFadeoutTiles
            );
        }
    }

    private static int computeTotalTiles(
            int curveTilesToMake,
            boolean resetHorizontalAngle,
            int horizontalFadeoutTiles,
            boolean resetVerticalAngle,
            int verticalFadeoutTiles
    ) {
        int horizontalFadeout = resetHorizontalAngle
                ? requireNonNegative(horizontalFadeoutTiles, "horizontalFadeoutTiles")
                : 0;
        int verticalFadeout = resetVerticalAngle
                ? requireNonNegative(verticalFadeoutTiles, "verticalFadeoutTiles")
                : 0;
        return curveTilesToMake + Math.max(horizontalFadeout, verticalFadeout);
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
        return value;
    }
}


