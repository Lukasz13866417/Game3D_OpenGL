package com.example.game3d_opengl.game.terrain_structures;

import com.example.game3d_opengl.game.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain_api.main.Terrain;

/**
 * Generates a sequence of empty segments (gaps) without geometry.
 */
public class TerrainEmptySegments extends AdvancedTerrainStructure {

    public TerrainEmptySegments(int emptySegments) {
        super(emptySegments);
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        for (int i = 0; i < tilesToMake; ++i) {
            brush.addEmptySegment();
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {

    }
}


