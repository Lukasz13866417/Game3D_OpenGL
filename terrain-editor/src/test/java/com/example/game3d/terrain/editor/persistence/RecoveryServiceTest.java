package com.example.game3d.terrain.editor.persistence;

import com.example.game3d.terrain.editor.edit.RepeatSpec;
import com.example.game3d.terrain.editor.edit.TileEdits;
import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.editor.state.EditorHistory;
import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.StructureDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RecoveryServiceTest {
    @TempDir Path directory;

    @Test void focusLossWritesAtomicRecoveryAndClearRemovesIt() throws Exception {
        EditorState state = EditorState.unsaved(DocumentFactories.blankStructure("recover.me", GridMode.ADVANCED));
        try (RecoveryService recovery = new RecoveryService(new TerrainJsonCodec(), directory,
                Duration.ofHours(1))) {
            recovery.edited(state);
            recovery.focusLost();
            assertTrue(Files.exists(recovery.pathFor(state)));
            recovery.clear(state);
            assertFalse(Files.exists(recovery.pathFor(state)));
        }
        assertFalse(Files.exists(directory.resolve("recover.me.recovery.json")));
    }

    @Test void explicitDiscardIsNotRecreatedByServiceClose() throws Exception {
        EditorState state = EditorState.unsaved(
                DocumentFactories.blankStructure("discard.me", GridMode.ADVANCED));
        Path path;
        try (RecoveryService recovery = new RecoveryService(
                new TerrainJsonCodec(), directory, Duration.ofHours(1), "discard-tab")) {
            recovery.edited(state);
            recovery.focusLost();
            path = recovery.pathFor(state);
            assertTrue(Files.exists(path));
            recovery.clear(state);
            assertFalse(Files.exists(path));
        }
        assertFalse(Files.exists(path));
    }

    @Test void recoveryCenterListsNewestDraftFirst() throws Exception {
        Files.writeString(directory.resolve("old.recovery.json"), "{}");
        Files.writeString(directory.resolve("new.recovery.json"), "{}");
        Files.setLastModifiedTime(directory.resolve("old.recovery.json"),
                java.nio.file.attribute.FileTime.fromMillis(10));
        Files.setLastModifiedTime(directory.resolve("new.recovery.json"),
                java.nio.file.attribute.FileTime.fromMillis(20));

        java.util.List<RecoveryService.RecoveryDraft> drafts = RecoveryService.list(directory);

        assertEquals("new.recovery.json", drafts.get(0).path().getFileName().toString());
        assertEquals("old.recovery.json", drafts.get(1).path().getFileName().toString());
    }

    @Test void undoAndRedoReplaceThePendingRecoveryState() throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        EditorHistory history = new EditorHistory(EditorState.unsaved(
                DocumentFactories.blankStructure("recover.history", GridMode.ADVANCED)));
        history.apply(TileEdits.repeat(0, true, "NORMAL",
                new RepeatSpec(1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0),
                () -> "00000000-0000-0000-0000-000000000001"));

        try (RecoveryService recovery = new RecoveryService(codec, directory,
                Duration.ofHours(1))) {
            recovery.edited(history.state());

            history.undo();
            recovery.edited(history.state());
            recovery.focusLost();
            StructureDocument afterUndo = (StructureDocument) RecoveryService.restore(
                    RecoveryService.list(directory).get(0), codec,
                    new EditorPersistence(codec)).state().document();
            assertEquals(0, afterUndo.tiles().size());

            history.redo();
            recovery.edited(history.state());
            recovery.focusLost();
            StructureDocument afterRedo = (StructureDocument) RecoveryService.restore(
                    RecoveryService.list(directory).get(0), codec,
                    new EditorPersistence(codec)).state().document();
            assertEquals(1, afterRedo.tiles().size());
        }
    }

    @Test void sameDocumentIdsInDifferentTabsUseDifferentRecoveryRecords() throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        EditorState state = EditorState.unsaved(
                DocumentFactories.blankStructure("same.id", GridMode.ADVANCED));
        try (RecoveryService first = new RecoveryService(
                codec, directory, Duration.ofHours(1), "tab-one");
             RecoveryService second = new RecoveryService(
                     codec, directory, Duration.ofHours(1), "tab-two")) {
            first.edited(state);
            second.edited(state);
            first.focusLost();
            second.focusLost();
            assertFalse(first.pathFor(state).equals(second.pathFor(state)));
            assertTrue(Files.exists(first.pathFor(state)));
            assertTrue(Files.exists(second.pathFor(state)));
            assertEquals(2, RecoveryService.list(directory).size());
        }
    }

    @Test void restoreRebindsOnlyWhileOriginalMatchesRecordedBase() throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        EditorPersistence persistence = new EditorPersistence(codec);
        Path original = directory.resolve("original.json");
        persistence.save(EditorState.unsaved(
                DocumentFactories.blankStructure("bound", GridMode.ADVANCED)), original);
        EditorState loaded = persistence.load(original).state();
        EditorHistory history = new EditorHistory(loaded);
        history.apply(TileEdits.repeat(0, true, "NORMAL",
                new RepeatSpec(1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0),
                () -> "00000000-0000-0000-0000-000000000001"));

        try (RecoveryService recovery = new RecoveryService(
                codec, directory.resolve("drafts"), Duration.ofHours(1), "bound-tab")) {
            recovery.edited(history.state());
            recovery.focusLost();
            RecoveryService.RecoveryDraft draft = RecoveryService
                    .list(directory.resolve("drafts")).get(0);

            RecoveryService.RestoreResult safe = RecoveryService.restore(
                    draft, codec, persistence);
            assertTrue(safe.reboundToOriginal());
            assertEquals(original.toAbsolutePath(), safe.state().sourcePath());
            assertTrue(safe.state().isDirty(codec));
            assertEquals(1, ((StructureDocument) safe.state().document()).tiles().size());

            persistence.save(EditorState.unsaved(
                    DocumentFactories.blankStructure("externally.changed", GridMode.ADVANCED)),
                    original);
            RecoveryService.RestoreResult changed = RecoveryService.restore(
                    draft, codec, persistence);
            assertFalse(changed.reboundToOriginal());
            assertTrue(changed.requiresSaveAs());
            assertEquals(null, changed.state().sourcePath());
            assertEquals(original.toAbsolutePath(), changed.originalSourcePath());
            assertEquals(1,
                    ((StructureDocument) changed.state().document()).tiles().size());
        }
    }

    @Test void envelopeStoresSourceAndBaseDigestMetadata() throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        EditorPersistence persistence = new EditorPersistence(codec);
        Path source = directory.resolve("metadata.json");
        EditorState saved = persistence.save(EditorState.unsaved(
                DocumentFactories.blankStructure("metadata", GridMode.BASIC)), source);

        String envelope = RecoveryService.encodeEnvelope(saved, codec);

        assertTrue(envelope.contains("\"recoveryFormatVersion\": 1"));
        assertTrue(envelope.contains(source.toAbsolutePath().toString()));
        assertTrue(envelope.contains(saved.savedContentDigest()));
        assertTrue(envelope.contains("\"document\""));
    }

    @Test void restoredDraftCanBeReplacedThenRetiredWithoutCollision() throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        EditorState state = EditorState.unsaved(
                DocumentFactories.blankStructure("restored", GridMode.ADVANCED));
        RecoveryService.RecoveryDraft old;
        try (RecoveryService recovery = new RecoveryService(
                codec, directory, Duration.ofHours(1), "old-tab")) {
            recovery.edited(state);
            recovery.focusLost();
            old = RecoveryService.list(directory).get(0);
        }
        try (RecoveryService replacement = new RecoveryService(
                codec, directory, Duration.ofHours(1), "new-tab")) {
            Path replacementPath = replacement.checkpoint(state);
            assertFalse(old.path().equals(replacement.pathFor(state)));
            assertEquals(replacementPath, replacement.pathFor(state));
            RecoveryService.deleteDraft(old);
            assertFalse(Files.exists(old.path()));
            assertTrue(Files.exists(replacement.pathFor(state)));
        }
    }
}
