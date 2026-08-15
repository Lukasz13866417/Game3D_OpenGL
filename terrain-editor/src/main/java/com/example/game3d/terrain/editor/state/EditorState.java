package com.example.game3d.terrain.editor.state;

import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;
import com.example.game3d.terrain.io.store.ContentDigests;
import com.example.game3d.terrain.io.validation.ValidationProblem;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable state for one editor tab. */
public record EditorState(
        TerrainSourceDocument document,
        Path sourcePath,
        String savedContentDigest,
        Set<String> selectedSourceIds,
        long revision,
        List<ValidationProblem> problems) {

    public EditorState {
        Objects.requireNonNull(document, "document");
        selectedSourceIds = Set.copyOf(selectedSourceIds);
        problems = List.copyOf(problems);
    }

    public static EditorState unsaved(TerrainSourceDocument document) {
        return new EditorState(document, null, null, Set.of(), 0, List.of());
    }

    public EditorState withDocument(TerrainSourceDocument value) {
        return new EditorState(value, sourcePath, savedContentDigest, selectedSourceIds,
                revision + 1, problems);
    }

    /** Starts a fresh asynchronous compile without changing document or dirty state. */
    public EditorState nextCompileRevision() {
        return new EditorState(document, sourcePath, savedContentDigest, selectedSourceIds,
                revision + 1, problems);
    }

    public EditorState withSelection(Set<String> value) {
        return new EditorState(document, sourcePath, savedContentDigest, value, revision, problems);
    }

    public EditorState withProblems(List<ValidationProblem> value) {
        return new EditorState(document, sourcePath, savedContentDigest, selectedSourceIds,
                revision, value);
    }

    public EditorState markSaved(Path path, String digest) {
        return new EditorState(document, path, digest, selectedSourceIds, revision, problems);
    }

    public boolean isDirty(TerrainJsonCodec codec) {
        return savedContentDigest == null
                || !savedContentDigest.equals(ContentDigests.sha256(codec.encode(document)));
    }
}
