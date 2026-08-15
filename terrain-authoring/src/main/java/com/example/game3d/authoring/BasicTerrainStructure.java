package com.example.game3d.authoring;

public abstract class BasicTerrainStructure
        extends BaseTerrainStructure<Terrain.BasicGridBrush> {
    protected BasicTerrainStructure(int tilesToMake) {
        super(tilesToMake);
    }

    @Override
    protected final Terrain.BasicGridBrush selectBrush(Terrain.CaptureSession session) {
        return session.basicGridBrush();
    }
}
