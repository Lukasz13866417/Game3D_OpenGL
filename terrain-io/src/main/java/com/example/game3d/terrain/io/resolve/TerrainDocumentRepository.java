package com.example.game3d.terrain.io.resolve;

import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.StructureDocument;

public interface TerrainDocumentRepository {
    StructureDocument findStructure(String id);
    LevelDocument findLevel(String id);
}
