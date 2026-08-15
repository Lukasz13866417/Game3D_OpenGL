package com.example.game3d.terrain.io.resolve;

import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.StructureDocument;

import java.util.LinkedHashMap;
import java.util.Map;

public final class InMemoryTerrainDocumentRepository implements TerrainDocumentRepository {
    private final Map<String, StructureDocument> structures;
    private final Map<String, LevelDocument> levels;

    public InMemoryTerrainDocumentRepository(Iterable<StructureDocument> structures,
                                             Iterable<LevelDocument> levels) {
        this.structures = new LinkedHashMap<>();
        this.levels = new LinkedHashMap<>();
        for (StructureDocument value : structures) this.structures.put(value.id(), value);
        for (LevelDocument value : levels) this.levels.put(value.id(), value);
    }

    @Override public StructureDocument findStructure(String id) { return structures.get(id); }
    @Override public LevelDocument findLevel(String id) { return levels.get(id); }
}
