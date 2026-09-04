package com.example.game3d.terrain.editor.persistence;

import com.example.game3d.terrain.io.CodecException;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.resolve.FileTerrainDocumentRepository;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import com.example.game3d.terrain.io.resolve.TerrainProjectContentIndex;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Explicitly reloaded, immutable view of a project's saved terrain-content files.
 *
 * <p>Loading happens into a temporary repository first. A failed reload therefore leaves the
 * previous usable snapshot in place, and ordinary external file changes cannot silently alter an
 * already open editor session.</p>
 */
public final class ProjectTerrainDocumentRepository
        implements TerrainDocumentRepository {
    private static final TerrainDocumentRepository EMPTY =
            new TerrainDocumentRepository() {
                @Override public StructureDocument findStructure(String id) {
                    return null;
                }

                @Override public LevelDocument findLevel(String id) {
                    return null;
                }
            };

    private final TerrainJsonCodec codec;
    private volatile Generation generation = new Generation(EMPTY, null);

    public ProjectTerrainDocumentRepository(TerrainJsonCodec codec) {
        if (codec == null) {
            throw new IllegalArgumentException("codec == null");
        }
        this.codec = codec;
    }

    public void reload(Path projectRoot) throws IOException, CodecException {
        if (projectRoot == null) {
            throw new IllegalArgumentException("projectRoot == null");
        }
        Path nextRoot = projectRoot.toAbsolutePath().normalize()
                .resolve("terrain-content");
        TerrainDocumentRepository next =
                FileTerrainDocumentRepository.load(nextRoot, codec);
        generation = new Generation(next, nextRoot);
    }

    /** Atomically adopts a fully loaded candidate generation. */
    public void replaceWith(ProjectTerrainDocumentRepository candidate) {
        if (candidate == null || candidate.generation.contentRoot == null) {
            throw new IllegalArgumentException("candidate is not loaded");
        }
        generation = candidate.generation;
    }

    public Path contentRoot() {
        return generation.contentRoot;
    }

    /** Returns the exact immutable repository generation currently visible to the editor. */
    public TerrainDocumentRepository snapshotView() {
        return generation.snapshot;
    }

    @Override public StructureDocument findStructure(String id) {
        return generation.snapshot.findStructure(id);
    }

    @Override public LevelDocument findLevel(String id) {
        return generation.snapshot.findLevel(id);
    }

    /** The canonical saved-source index from the same immutable generation as lookups. */
    @Override public TerrainProjectContentIndex contentIndex() {
        return generation.snapshot.contentIndex();
    }

    private record Generation(TerrainDocumentRepository snapshot, Path contentRoot) { }
}
