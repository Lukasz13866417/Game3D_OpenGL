package com.example.game3d.authoring;

/** A level is an ordered structure composition, not a separate runtime representation. */
public class TerrainLevelSequence extends AdvancedTerrainStructure {
    private final String stableId;
    private final BaseTerrainStructure<?>[] sections;

    public TerrainLevelSequence(String stableId, BaseTerrainStructure<?>... sections) {
        super(0);
        if (stableId == null || stableId.isEmpty()) {
            throw new IllegalArgumentException("stableId is empty");
        }
        this.stableId = stableId;
        this.name = stableId;
        this.sections = sections == null
                ? new BaseTerrainStructure<?>[0] : sections.clone();
    }

    public final String stableId() {
        return stableId;
    }

    @Override
    protected final void generateTiles(Terrain.TileBrush brush) {
        for (BaseTerrainStructure<?> section : sections) {
            addChild(section, brush);
        }
    }

    @Override
    protected final void generateAddons(
            Terrain.AdvancedGridBrush brush, int rows, int columns) {
    }

    @Override
    public final int getMinimumGeneratedTileCount() {
        int result = 0;
        for (BaseTerrainStructure<?> section : sections) {
            if (section != null) {
                result = Math.addExact(result, section.getMinimumGeneratedTileCount());
            }
        }
        return result;
    }
}
