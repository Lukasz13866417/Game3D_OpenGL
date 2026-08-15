package com.example.game3d.terrain.editor.compile;

import com.example.game3d.terrain.io.model.TerrainSourceDocument;

@FunctionalInterface
public interface DocumentCompiler {
    CompileResult compile(long revision, TerrainSourceDocument document) throws Exception;
}
