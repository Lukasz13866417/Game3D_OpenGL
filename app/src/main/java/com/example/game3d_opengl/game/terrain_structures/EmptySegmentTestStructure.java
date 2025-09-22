package com.example.game3d_opengl.game.terrain_structures;

import com.example.game3d_opengl.game.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.track_elements.spike.DeathSpike;

/**
 * A test terrain structure that alternates between regular tiles and empty segments
 * to verify that addons are not placed in empty segments.
 */
public class EmptySegmentTestStructure extends AdvancedTerrainStructure {

    public EmptySegmentTestStructure(int tilesToMake) {
        super(tilesToMake);
    }

    @Override
    protected void generateTiles(Terrain.TileBrush brush) {
        // Generate pattern: 2 tiles, 1 empty, 2 tiles, 1 empty, etc.
        int generated = 0;
        while (generated < tilesToMake) {
            // Add 2 regular tiles
            for (int i = 0; i < 2 && generated < tilesToMake; i++) {
                brush.addSegment();
                generated++;
            }
            
            // Add 1 empty segment if we still have tiles to make
            if (generated < tilesToMake) {
                brush.addEmptySegment();
                generated++; // Empty segments count towards tilesToMake
            }
        }
    }

    @Override
    protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
        // Try to place addons - they should be prevented from appearing in empty segments
        Addon[] addons = new Addon[3];
        for (int j = 0; j < addons.length; j++) {
            addons[j] = DeathSpike.createDeathSpike();
        }
        brush.reserveRandomFittingVertical(addons.length, addons);
    }
} 