package com.example.game3d.terrain.editor.state;

import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;

import java.util.Collections;

public final class DocumentFactories {
    private DocumentFactories() {}

    public static StructureDocument blankStructure(String id, GridMode gridMode) {
        return new StructureDocument(TerrainSourceDocument.CURRENT_FORMAT_VERSION, id, gridMode,
                Collections.emptyList(), Collections.emptyList());
    }

    public static LevelDocument blankLevel(String id, String profileId) {
        return new LevelDocument(TerrainSourceDocument.CURRENT_FORMAT_VERSION, id, profileId,
                Collections.emptyList());
    }
}
