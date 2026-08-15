package com.example.game3d.authoring;

public abstract class AdvancedTerrainStructure
        extends BaseTerrainStructure<Terrain.AdvancedGridBrush> {
    protected AdvancedTerrainStructure(int tilesToMake) {
        super(tilesToMake);
    }

    @Override
    protected final Terrain.AdvancedGridBrush selectBrush(Terrain.CaptureSession session) {
        return session.advancedGridBrush();
    }
}
