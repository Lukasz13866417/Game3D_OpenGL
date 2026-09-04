package com.example.game3d.terrain.io.store;

import com.example.game3d.terrain.io.CodecException;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source persistence. Save deliberately performs no semantic validation. */
public final class TerrainDocumentStore {
    private final TerrainJsonCodec codec;
    private final AtomicFileStore files;

    public TerrainDocumentStore(TerrainJsonCodec codec, AtomicFileStore files) {
        this.codec = codec;
        this.files = files;
    }

    public String save(Path path, TerrainSourceDocument document) throws IOException {
        return save(path, document, null);
    }

    /** Saves after an optional last-moment target-version guard. */
    public String save(Path path, TerrainSourceDocument document,
                       AtomicFileStore.BeforeReplace beforeReplace) throws IOException {
        String encoded = codec.encodeRoundTripped(document);
        if (beforeReplace == null) files.writeUtf8(path, encoded);
        else files.writeUtf8(path, encoded, beforeReplace);
        return ContentDigests.sha256(encoded);
    }

    public TerrainSourceDocument load(Path path) throws IOException, CodecException {
        byte[] bytes = Files.readAllBytes(path);
        return codec.decode(new String(bytes, StandardCharsets.UTF_8));
    }
}
