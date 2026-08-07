package com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial;

import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.terrain_api.main.BasicTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.game.util.GameRandom;

public class IntroSparseSpikeStraight extends BasicTerrainStructure {
    public IntroSparseSpikeStraight(int rows) {
        super(rows);
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        for (int i = 0; i < tilesToMake; ++i) {
            brush.addSegment();
        }
    }

    @Override
    protected void generateAddons(Terrain.BasicGridBrush brush, int nRows, int nCols) {
        int spikeCount = Math.min(3, Math.max(1, nRows / 3));
        for (int i = 0; i < spikeCount; ++i) {
            int row = 1 + ((i + 1) * nRows) / (spikeCount + 1);
            int col = GameRandom.randInt(1, nCols);
            brush.reserveHorizontal(row, col, 1, new Addon[]{createSpike()});
        }
    }

    protected Addon createSpike() {
        return DeathSpike.createDeathSpike();
    }
}
