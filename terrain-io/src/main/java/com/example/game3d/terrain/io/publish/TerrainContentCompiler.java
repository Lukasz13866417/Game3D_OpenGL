package com.example.game3d.terrain.io.publish;

import com.example.game3d.terrain.io.model.CatalogEntry;
import com.example.game3d.terrain.io.resolve.ResolvedLevel;

/** Narrow seam implemented with terrain-authoring's deterministic materializer. */
public interface TerrainContentCompiler {
    CompiledTerrainContent compileJavaProvider(CatalogEntry entry) throws Exception;
    CompiledTerrainContent compileJsonLevel(CatalogEntry entry, ResolvedLevel level) throws Exception;
}
