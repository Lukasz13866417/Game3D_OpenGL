package com.example.game3d.terrain.io.resolve;

import com.example.game3d.terrain.io.CodecException;
import com.example.game3d.terrain.io.TerrainJsonCodec;
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

    private FileTerrainDocumentRepository(Map<String, StructureDocument> structures,
                                          Map<String, LevelDocument> levels) {
        this.structures = Collections.unmodifiableMap(structures);
        this.levels = Collections.unmodifiableMap(levels);
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
        for (Path path : paths) {
            String json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            TerrainSourceDocument document = codec.decode(json);
            if (document instanceof StructureDocument) {
                StructureDocument value = (StructureDocument) document;
                putUnique(structures, document.id(), value, path);
                putAlias(structures, root, path, value);
            } else if (document instanceof LevelDocument) {
                LevelDocument value = (LevelDocument) document;
                putUnique(levels, document.id(), value, path);
                putAlias(levels, root, path, value);
            }
            // Catalog documents may live beside sources but are not references.
        }
        return new FileTerrainDocumentRepository(structures, levels);
    }

    @Override public StructureDocument findStructure(String id) {
        return structures.get(id);
    }

    @Override public LevelDocument findLevel(String id) {
        return levels.get(id);
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
}
