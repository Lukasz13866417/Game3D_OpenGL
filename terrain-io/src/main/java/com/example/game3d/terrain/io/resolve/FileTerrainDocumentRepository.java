package com.example.game3d.terrain.io.resolve;

import com.example.game3d.terrain.io.CodecException;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Immutable repository loaded from all JSON documents below one source root. */
public final class FileTerrainDocumentRepository implements TerrainDocumentRepository {
    private final Map<String, StructureDocument> structures;
    private final Map<String, LevelDocument> levels;
    private final TerrainProjectContentIndex contentIndex;

    private FileTerrainDocumentRepository(Map<String, StructureDocument> structures,
                                          Map<String, LevelDocument> levels,
                                          TerrainProjectContentIndex contentIndex) {
        this.structures = Collections.unmodifiableMap(structures);
        this.levels = Collections.unmodifiableMap(levels);
        this.contentIndex = contentIndex;
    }

    public static FileTerrainDocumentRepository load(Path root, TerrainJsonCodec codec)
            throws IOException, CodecException {
        if (root == null || codec == null || !Files.isDirectory(root)) {
            throw new IOException("Terrain source root is not a directory: " + root);
        }
        List<Path> paths = new ArrayList<Path>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !isPublishedArtifact(root, path))
                    .forEach(paths::add);
        }
        Collections.sort(paths);
        Map<String, StructureDocument> structures = new LinkedHashMap<String, StructureDocument>();
        Map<String, LevelDocument> levels = new LinkedHashMap<String, LevelDocument>();
        Map<String, TerrainProjectContentIndex.Entry<StructureDocument>> structureSources =
                new LinkedHashMap<String, TerrainProjectContentIndex.Entry<StructureDocument>>();
        Map<String, TerrainProjectContentIndex.Entry<LevelDocument>> levelSources =
                new LinkedHashMap<String, TerrainProjectContentIndex.Entry<LevelDocument>>();
        Map<String, TerrainProjectContentIndex.Entry<CatalogDocument>> catalogSources =
                new LinkedHashMap<String, TerrainProjectContentIndex.Entry<CatalogDocument>>();
        for (Path path : paths) {
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            TerrainSourceDocument document = codec.decode(json);
            if (document instanceof StructureDocument) {
                StructureDocument value = (StructureDocument) document;
                putUnique(structures, document.id(), value, path);
                putAlias(structures, root, path, value);
                putSource(structureSources, value, path);
            } else if (document instanceof LevelDocument) {
                LevelDocument value = (LevelDocument) document;
                putUnique(levels, document.id(), value, path);
                putAlias(levels, root, path, value);
                putSource(levelSources, value, path);
            } else if (document instanceof CatalogDocument) {
                putSource(catalogSources, (CatalogDocument) document, path);
            }
        }
        return new FileTerrainDocumentRepository(structures, levels,
                new TerrainProjectContentIndex(
                        structureSources, levelSources, catalogSources));
    }

    @Override public StructureDocument findStructure(String id) {
        return structures.get(id);
    }

    @Override public LevelDocument findLevel(String id) {
        return levels.get(id);
    }

    @Override public TerrainProjectContentIndex contentIndex() {
        return contentIndex;
    }

    private static IOException duplicate(String id, Path path) {
        return new IOException("Duplicate terrain document ID '" + id + "' at " + path);
    }

    private static boolean isPublishedArtifact(Path root, Path path) {
        Path relative = root.relativize(path);
        return relative.getNameCount() > 0
                && "published".equals(relative.getName(0).toString());
    }

    private static <T> void putUnique(Map<String, T> values, String key, T value, Path path)
            throws IOException {
        T previous = values.put(key, value);
        if (previous != null && previous != value) throw duplicate(key, path);
    }

    private static <T> void putAlias(Map<String, T> values, Path root, Path path, T value)
            throws IOException {
        String relative = root.relativize(path).toString().replace('\\', '/');
        putUnique(values, relative, value, path);
        if (relative.endsWith(".json")) {
            putUnique(values, relative.substring(0, relative.length() - 5), value, path);
        }
    }

    private static <T extends TerrainSourceDocument> void putSource(
            Map<String, TerrainProjectContentIndex.Entry<T>> values,
            T value,
            Path path) throws IOException {
        TerrainProjectContentIndex.Entry<T> previous = values.put(
                value.id(), new TerrainProjectContentIndex.Entry<T>(value, path));
        if (previous != null) {
            throw duplicate(value.id(), path);
        }
    }
}
