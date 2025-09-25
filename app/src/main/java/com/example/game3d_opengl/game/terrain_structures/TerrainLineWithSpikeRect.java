package com.example.game3d_opengl.game.terrain_structures;

import static java.lang.Math.min;

import com.example.game3d_opengl.game.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.track_elements.spike.DeathSpike;

public class TerrainLineWithSpikeRect extends AdvancedTerrainStructure {

    public TerrainLineWithSpikeRect(int nTiles) {
        super(nTiles);
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        for (int i = 0; i < tilesToMake; ++i) {
            brush.addSegment();
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
        final int sideSize = min(nCols - 2, nRows - 2); // usually will be nCols - 2
        // 4 = sides in rectangle
        DeathSpike[][] spikes = new DeathSpike[4][sideSize - 1];
        for (int i = 0; i < spikes.length; ++i) {
            for (int j = 0; j < spikes[i].length; ++j) {
                spikes[i][j] = DeathSpike.createDeathSpike();
            }
        }
        final int topLeftRow = 1 + (nRows - sideSize) / 2;
        final int topLeftCol = 1 + (nCols - sideSize) / 2;
        brush.reserveHorizontal(
                topLeftRow, topLeftCol, sideSize - 1, spikes[0]
        );
        brush.reserveVertical(
                topLeftRow, topLeftCol + sideSize - 1, sideSize - 1, spikes[1]
        );
        brush.reserveHorizontal(
                topLeftRow + sideSize - 1, topLeftCol + 1, sideSize - 1, spikes[2]
        );
        brush.reserveVertical(
                topLeftRow + 1, topLeftCol, sideSize - 1, spikes[3]
        );
    }
}
