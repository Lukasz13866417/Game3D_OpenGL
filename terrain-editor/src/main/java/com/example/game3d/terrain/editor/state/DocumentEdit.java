package com.example.game3d.terrain.editor.state;

import com.example.game3d.terrain.io.model.TerrainSourceDocument;

@FunctionalInterface
public interface DocumentEdit {
    TerrainSourceDocument apply(TerrainSourceDocument document);
}
