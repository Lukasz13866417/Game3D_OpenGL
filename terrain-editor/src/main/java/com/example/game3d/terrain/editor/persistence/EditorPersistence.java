package com.example.game3d.terrain.editor.persistence;

import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.io.CodecException;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;
import com.example.game3d.terrain.io.store.AtomicFileStore;
import com.example.game3d.terrain.io.store.ContentDigests;
import com.example.game3d.terrain.io.store.TerrainDocumentStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

public final class EditorPersistence {
    private final TerrainJsonCodec codec;
    private final TerrainDocumentStore store;

    public EditorPersistence(TerrainJsonCodec codec) {
        this.codec = codec;
        this.store = new TerrainDocumentStore(codec, new AtomicFileStore());
    }

    public EditorState save(EditorState state, Path target) throws IOException {
        String digest = store.save(target, state.document());
        return state.markSaved(target.toAbsolutePath(), digest);
    }

    public LoadedDocument load(Path source) throws IOException, CodecException {
        TerrainSourceDocument document = store.load(source);
        String encoded = codec.encode(document);
        EditorState state = EditorState.unsaved(document).markSaved(source.toAbsolutePath(),
                ContentDigests.sha256(encoded));
        return new LoadedDocument(state, Files.getLastModifiedTime(source));
    }

    public boolean externallyChanged(Path source, FileTime knownWriteTime) throws IOException {
        return !Files.getLastModifiedTime(source).equals(knownWriteTime);
    }

    public record LoadedDocument(EditorState state, FileTime writeTime) {}
}
