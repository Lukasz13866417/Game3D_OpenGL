package com.example.game3d.terrain.io.resolve;

import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable canonical-ID index of saved project terrain sources.
 *
 * <p>Lookup aliases remain a repository concern. This index deliberately contains each source
 * file exactly once and retains its canonical absolute path for editor project browsers.</p>
 */
public final class TerrainProjectContentIndex {
    private final Map<String, Entry<StructureDocument>> structuresById;
    private final Map<String, Entry<LevelDocument>> levelsById;
    private final Map<String, Entry<CatalogDocument>> catalogsById;

    TerrainProjectContentIndex(
            Map<String, Entry<StructureDocument>> structuresById,
            Map<String, Entry<LevelDocument>> levelsById,
            Map<String, Entry<CatalogDocument>> catalogsById) {
        this.structuresById = immutableCopy(structuresById);
        this.levelsById = immutableCopy(levelsById);
        this.catalogsById = immutableCopy(catalogsById);
    }

    public static TerrainProjectContentIndex empty() {
        return new TerrainProjectContentIndex(
                Collections.<String, Entry<StructureDocument>>emptyMap(),
                Collections.<String, Entry<LevelDocument>>emptyMap(),
                Collections.<String, Entry<CatalogDocument>>emptyMap());
    }

    public Map<String, Entry<StructureDocument>> structuresById() {
        return structuresById;
    }

    public Map<String, Entry<LevelDocument>> levelsById() {
        return levelsById;
    }

    public Map<String, Entry<CatalogDocument>> catalogsById() {
        return catalogsById;
    }

    private static <T extends TerrainSourceDocument> Map<String, Entry<T>> immutableCopy(
            Map<String, Entry<T>> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Entry<T>>(source));
    }

    /** One canonical saved source, independent of any repository lookup aliases. */
    public static final class Entry<T extends TerrainSourceDocument> {
        private final T document;
        private final Path sourcePath;

        public Entry(T document, Path sourcePath) {
            this.document = Objects.requireNonNull(document, "document");
            this.sourcePath = Objects.requireNonNull(sourcePath, "sourcePath")
                    .toAbsolutePath().normalize();
        }

        public String id() {
            return document.id();
        }

        public T document() {
            return document;
        }

        public Path sourcePath() {
            return sourcePath;
        }
    }
}
