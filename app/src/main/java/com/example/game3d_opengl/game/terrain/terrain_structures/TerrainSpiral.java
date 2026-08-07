package com.example.game3d_opengl.game.terrain.terrain_structures;

import static java.lang.Math.min;

import com.example.game3d_opengl.game.terrain.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;

public class TerrainSpiral extends AdvancedTerrainStructure {
    private final float dAngHor, angVer;

    public static Builder builder() {
        return new Builder();
    }

    public TerrainSpiral(int tilesToMake,
                          float dAngHor, float angVer) {
        super(tilesToMake);
        this.dAngHor = dAngHor;
        this.angVer = angVer;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        float angHorPerTile = dAngHor / (float) (tilesToMake);
        brush.addVerticalAng(angVer);
        for(int i=0;i<tilesToMake;++i){
            brush.addHorizontalAng(angHorPerTile);
            brush.addSegment();
        }
        brush.addVerticalAng(-angVer);
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
        for(int i=0;i<min(nRows,2);++i){
            Addon[] addons = new Addon[nCols];
            for(int j = 0; j <addons.length; ++j){
                addons[j] = DeathSpike.createDeathSpike();
            }
            brush.reserveRandomFittingHorizontal(addons.length,addons);
        }
    }

    public static final class Builder {
        private Integer tilesToMake;
        private Float dAngHor;
        private Float angVer;

        public Builder tilesToMake(int tilesToMake) {
            this.tilesToMake = tilesToMake;
            return this;
        }

        public Builder horizontalAngleDelta(float dAngHor) {
            this.dAngHor = dAngHor;
            return this;
        }

        public Builder verticalAngle(float angVer) {
            this.angVer = angVer;
            return this;
        }

        public TerrainSpiral build() {
            if (tilesToMake == null) {
                throw new IllegalStateException("tilesToMake not set");
            }
            if (dAngHor == null) {
                throw new IllegalStateException("horizontalAngleDelta not set");
            }
            if (angVer == null) {
                throw new IllegalStateException("verticalAngle not set");
            }
            return new TerrainSpiral(tilesToMake, dAngHor, angVer);
        }
    }
}
