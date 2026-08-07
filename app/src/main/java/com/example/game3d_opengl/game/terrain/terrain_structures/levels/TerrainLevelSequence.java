package com.example.game3d_opengl.game.terrain.terrain_structures.levels;

import com.example.game3d_opengl.game.terrain.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.BaseTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;

public abstract class TerrainLevelSequence extends AdvancedTerrainStructure {
    private final String debugName;
    private final BaseTerrainStructure<?>[] sections;

    protected TerrainLevelSequence(String debugName, BaseTerrainStructure<?>... sections) {
        super(0);
        this.debugName = debugName != null ? debugName : getClass().getSimpleName();
        this.name = this.debugName;
        this.sections = sections != null ? sections : new BaseTerrainStructure<?>[0];
        assignSectionDebugNames();
    }

    @Override
    protected final void generateTiles(Terrain.TileBrush brush) {
        for (BaseTerrainStructure<?> section : sections) {
            if (section != null) {
                addChild(section, brush);
            }
        }
    }

    @Override
    protected final void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
        // Levels currently exist only to sequence child structures.
    }

    public final String getDebugName() {
        return debugName;
    }

    @Override
    public final int getMinimumGeneratedTileCount() {
        int total = 0;
        for (BaseTerrainStructure<?> section : sections) {
            if (section != null) {
                total = Math.addExact(
                        total, section.getMinimumGeneratedTileCount());
            }
        }
        return total;
    }

    final BaseTerrainStructure<?>[] sectionsForTesting() {
        return sections.clone();
    }

    private void assignSectionDebugNames() {
        for (int i = 0; i < sections.length; ++i) {
            BaseTerrainStructure<?> section = sections[i];
            if (section == null) {
                continue;
            }
            if ("NOT SET".equals(section.name)) {
                section.name = debugName + "/section_" + i + "_" + section.getClass().getSimpleName();
            }
        }
    }
}
