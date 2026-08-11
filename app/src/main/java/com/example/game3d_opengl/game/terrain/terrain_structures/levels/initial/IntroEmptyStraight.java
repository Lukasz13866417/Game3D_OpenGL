package com.example.game3d_opengl.game.terrain.terrain_structures.levels.initial;

import com.example.game3d_opengl.game.terrain.terrain_api.main.BasicTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;

public final class IntroEmptyStraight extends BasicTerrainStructure {
    public IntroEmptyStraight(int rows) {
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
        // Intentionally empty: the run should start with a safe straight.
    }
}
