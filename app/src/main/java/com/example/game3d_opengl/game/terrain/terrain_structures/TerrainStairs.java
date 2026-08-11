package com.example.game3d_opengl.game.terrain.terrain_structures;

import com.example.game3d_opengl.game.terrain.terrain_api.main.BasicTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.game.util.GameRandom;


public class TerrainStairs extends BasicTerrainStructure {

    private final float dAngHor, jump, cntStairs;
    private final int emptyBetween, tilesPerStair;

    public static Builder builder() {
        return new Builder();
    }

    public TerrainStairs(int tilesPerStair, int cntStairs, float dAngHor, float jump) {
        this(tilesPerStair, cntStairs, 0, dAngHor, jump);
    }

    public TerrainStairs(int tilesPerStair, int cntStairs, int emptyBetween, float dAngHor, float jump) {
        super(tilesPerStair*cntStairs);
        this.dAngHor = dAngHor;
        this.jump = jump;
        this.tilesPerStair = tilesPerStair;
        this.cntStairs = cntStairs;
        this.emptyBetween = emptyBetween;
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        brush.setCornerAlphas(0.5f,0.5f);
        float angHorPerTile = dAngHor / (float) (tilesToMake);
        brush.liftUp(jump);
        for (int i = 0; i < cntStairs; ++i) {
            brush.addChild(new BasicTerrainCurve(tilesPerStair, angHorPerTile*tilesPerStair){
                @Override
                protected void generateAddons(Terrain.BasicGridBrush gridBrush,
                                                            int nRows, int nCols) {
                    DeathSpike[] spikes = new DeathSpike[1];
                    for (int j = 0; j < spikes.length; ++j) {
                        spikes[j] = DeathSpike.createDeathSpike();
                    }
                    int[][] points = GameRandom.sampleDistinctGridPoints(nRows, nCols, 1);
                    int row = points[0][0];
                    int col = points[0][1];
                    gridBrush.reserveHorizontal(row, col, spikes.length, spikes);
                }
            });
            brush.addHorizontalAng(angHorPerTile*tilesPerStair);
            // add empty tiles between levels (not after the last level)
            if (i < cntStairs - 1) {
                for (int e = 0; e < emptyBetween; ++e) {
                    brush.addEmptySegment();
                }
            }
            brush.liftUp(jump);
        }
        brush.setCornerAlphas(1f,1f);
    }


    @Override
    protected void generateAddons(Terrain.BasicGridBrush brush, int nRows, int nCols) {
    }

    public static final class Builder {
        private Integer tilesPerStair;
        private Integer cntStairs;
        private int emptyBetween = 0;
        private Float dAngHor;
        private Float jump;

        public Builder tilesPerStair(int tilesPerStair) {
            this.tilesPerStair = tilesPerStair;
            return this;
        }

        public Builder stairCount(int cntStairs) {
            this.cntStairs = cntStairs;
            return this;
        }

        public Builder emptyBetween(int emptyBetween) {
            this.emptyBetween = emptyBetween;
            return this;
        }

        public Builder horizontalAngleDelta(float dAngHor) {
            this.dAngHor = dAngHor;
            return this;
        }

        public Builder jump(float jump) {
            this.jump = jump;
            return this;
        }

        public TerrainStairs build() {
            if (tilesPerStair == null) {
                throw new IllegalStateException("tilesPerStair not set");
            }
            if (cntStairs == null) {
                throw new IllegalStateException("stairCount not set");
            }
            if (dAngHor == null) {
                throw new IllegalStateException("horizontalAngleDelta not set");
            }
            if (jump == null) {
                throw new IllegalStateException("jump not set");
            }
            return new TerrainStairs(tilesPerStair, cntStairs, emptyBetween, dAngHor, jump);
        }
    }
}
