package com.example.game3d.terrain.editor.persistence;

import com.example.game3d.terrain.editor.edit.RepeatSpec;
import com.example.game3d.terrain.editor.edit.TileEdits;
import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.editor.state.EditorHistory;
import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import com.example.game3d.terrain.io.TerrainEncodingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecoveryServiceTest {
    @TempDir Path directory;

    @Test void focusLossWritesAtomicRecoveryAndClearRemovesIt() throws Exception {
        EditorState state = EditorState.unsaved(DocumentFactories.blankStructure("recover.me", GridMode.ADVANCED));
        try (RecoveryService recovery = new RecoveryService(new TerrainJsonCodec(), directory,
                Duration.ofHours(1))) {
            recovery.edited(state);
            recovery.focusLost();
            awaitHealth(recovery, RecoveryHealth.SAVED);
            assertTrue(Files.exists(recovery.pathFor(state)));
            assertEquals(RecoveryHealth.SAVED, recovery.status().health());
            recovery.clear(state);
            assertFalse(Files.exists(recovery.pathFor(state)));
            assertEquals(RecoveryHealth.NOT_NEEDED, recovery.status().health());
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
            awaitHealth(recovery, RecoveryHealth.SAVED);
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
            awaitHealth(recovery, RecoveryHealth.SAVED);
            StructureDocument afterUndo = (StructureDocument) RecoveryService.restore(
                    RecoveryService.list(directory).get(0), codec,
                    new EditorPersistence(codec)).state().document();
            assertEquals(0, afterUndo.tiles().size());

            history.redo();
            recovery.edited(history.state());
            recovery.focusLost();
            awaitHealth(recovery, RecoveryHealth.SAVED);
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
            awaitHealth(first, RecoveryHealth.SAVED);
            awaitHealth(second, RecoveryHealth.SAVED);
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
            awaitHealth(recovery, RecoveryHealth.SAVED);
            RecoveryService.RecoveryDraft draft = RecoveryService
                    .list(directory.resolve("drafts")).get(0);

            RecoveryService.RestoreResult safe = RecoveryService.restore(
                    draft, codec, persistence);
            assertTrue(safe.reboundToOriginal());
            assertEquals(persistence.diskVersion(original), safe.diskVersion());
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

    @Test void absentIntendedSaveTargetSurvivesRecoveryWithoutWeakeningCreateNew()
            throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        EditorPersistence persistence = new EditorPersistence(codec);
        Path intended = directory.resolve("project/terrain-content/catalog.json")
                .toAbsolutePath();
        EditorState state = new EditorState(
                DocumentFactories.blankStructure("intended", GridMode.ADVANCED),
                intended, null, Collections.emptySet(), 0L, Collections.emptyList());
        RecoveryService.RecoveryDraft draft;
        try (RecoveryService recovery = new RecoveryService(
                codec, directory.resolve("drafts-intended"),
                Duration.ofHours(1), "intended-tab")) {
            recovery.checkpoint(state);
            draft = RecoveryService.list(directory.resolve("drafts-intended")).get(0);
        }

        RecoveryService.RestoreResult restored = RecoveryService.restore(
                draft, codec, persistence);

        assertTrue(restored.reboundToOriginal());
        assertEquals(intended, restored.state().sourcePath());
        assertEquals(null, restored.diskVersion());
        assertTrue(restored.state().isDirty(codec));

        Files.createDirectories(intended.getParent());
        Files.writeString(intended, "external");
        RecoveryService.RestoreResult conflict = RecoveryService.restore(
                draft, codec, persistence);
        assertFalse(conflict.reboundToOriginal());
        assertTrue(conflict.requiresSaveAs());
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
            awaitHealth(recovery, RecoveryHealth.SAVED);
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

    @Test void automaticWriteFailureIsObservableAndDoesNotPretendToBeSaved() throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        EditorState state = EditorState.unsaved(
                DocumentFactories.blankStructure("recovery.failure", GridMode.ADVANCED));
        CopyOnWriteArrayList<RecoveryStatus> events = new CopyOnWriteArrayList<>();
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        RecoveryStorage failing = new RecoveryStorage() {
            @Override public void writeUtf8(Path path, String content) throws IOException {
                throw new IOException("disk full");
            }
            @Override public void deleteIfExists(Path path) {
            }
        };
        RecoveryService recovery = new RecoveryService(codec, directory,
                Duration.ofHours(1), "failure-tab", failing, executor,
                Runnable::run, events::add);
        try {
            recovery.edited(state);
            assertEquals(RecoveryHealth.PENDING, recovery.status().health());

            recovery.focusLost();
            awaitHealth(recovery, RecoveryHealth.FAILED);

            assertEquals(RecoveryHealth.FAILED, recovery.status().health());
            assertEquals("disk full", recovery.status().failure().getMessage());
            assertTrue(events.stream().anyMatch(
                    event -> event.health() == RecoveryHealth.FAILED));
        } finally {
            recovery.close();
        }
        assertTrue(executor.isShutdown());
    }

    @Test void failureWarningSurvivesEditsAndScheduledRetriesUntilAWriteSucceeds()
            throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        EditorState state = EditorState.unsaved(
                DocumentFactories.blankStructure("recovery.retry", GridMode.ADVANCED));
        AtomicBoolean failWrites = new AtomicBoolean(true);
        AtomicInteger writes = new AtomicInteger();
        CopyOnWriteArrayList<RecoveryStatus> events = new CopyOnWriteArrayList<>();
        RecoveryStorage storage = new RecoveryStorage() {
            @Override public void writeUtf8(Path path, String content) throws IOException {
                writes.incrementAndGet();
                if (failWrites.get()) throw new IOException("temporarily unavailable");
            }

            @Override public void deleteIfExists(Path path) {
            }
        };
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        RecoveryService recovery = new RecoveryService(codec, directory,
                Duration.ofHours(1), "retry-tab", storage, executor,
                Runnable::run, events::add);
        try {
            recovery.edited(state);
            recovery.focusLost();
            awaitHealth(recovery, RecoveryHealth.FAILED);
            int afterFailure = events.size();

            recovery.edited(state);
            assertEquals(RecoveryHealth.FAILED, recovery.status().health(),
                    "editing must not hide the persistent recovery warning");
            recovery.focusLost();
            awaitWrites(writes, 2);
            assertEquals(RecoveryHealth.FAILED, recovery.status().health());
            assertTrue(events.subList(afterFailure, events.size()).stream()
                            .noneMatch(event -> event.health() == RecoveryHealth.PENDING),
                    "a scheduled retry must not claim that the previous failure is resolved");

            failWrites.set(false);
            int beforeSuccessfulRetry = events.size();
            recovery.edited(state);
            assertEquals(RecoveryHealth.FAILED, recovery.status().health());
            recovery.focusLost();
            awaitHealth(recovery, RecoveryHealth.SAVED);
            assertTrue(events.subList(beforeSuccessfulRetry, events.size()).stream()
                    .noneMatch(event -> event.health() == RecoveryHealth.PENDING));
            assertEquals(3, writes.get());
        } finally {
            recovery.clear(state);
            recovery.close();
        }
        assertTrue(executor.isShutdown());
    }

    @Test void unencodableCheckpointPreservesPreviousGoodDraftAndReportsFailure()
            throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        EditorState good = EditorState.unsaved(
                DocumentFactories.blankStructure("recovery.encoding", GridMode.ADVANCED));
        try (RecoveryService recovery = new RecoveryService(
                codec, directory, Duration.ofHours(1), "encoding-tab")) {
            Path path = recovery.checkpoint(good);
            String before = Files.readString(path);
            StructureDocument badDocument = new StructureDocument(
                    1, good.document().id(), GridMode.ADVANCED,
                    Collections.singletonList(new TileRecord(
                            "00000000-0000-0000-0000-000000000001", true,
                            Double.NaN, 0, 0, "NORMAL", 1, 1)),
                    Collections.emptyList());

            assertThrows(TerrainEncodingException.class,
                    () -> recovery.checkpoint(EditorState.unsaved(badDocument)));

            assertEquals(before, Files.readString(path));
            assertEquals(RecoveryHealth.FAILED, recovery.status().health());
            assertTrue(RecoveryService.list(directory).stream()
                    .anyMatch(draft -> draft.path().equals(path)));
        }
    }

    @Test void failedClearIsObservableAndKeepsTheLatestDraftEligibleForRetry()
            throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        EditorState state = EditorState.unsaved(
                DocumentFactories.blankStructure("clear.failure", GridMode.ADVANCED));
        AtomicInteger writes = new AtomicInteger();
        RecoveryStorage storage = new RecoveryStorage() {
            @Override public void writeUtf8(Path path, String content) {
                writes.incrementAndGet();
            }

            @Override public void deleteIfExists(Path path) throws IOException {
                throw new IOException("read-only recovery directory");
            }
        };
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        RecoveryService recovery = new RecoveryService(codec, directory,
                Duration.ofHours(1), "clear-failure-tab", storage, executor,
                Runnable::run, ignored -> { });
        try {
            recovery.checkpoint(state);

            IOException failure = assertThrows(IOException.class,
                    () -> recovery.clear(state));

            assertEquals("read-only recovery directory", failure.getMessage());
            assertEquals(RecoveryHealth.FAILED, recovery.status().health());
            assertEquals(1, writes.get());
        } finally {
            // A failed delete must not null the latest state. Closing retries the durable write.
            recovery.close();
        }
        assertEquals(2, writes.get());
        assertTrue(executor.isShutdown());
    }

    private static void awaitHealth(RecoveryService recovery, RecoveryHealth expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while (recovery.status().health() != expected && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertEquals(expected, recovery.status().health());
    }

    private static void awaitWrites(AtomicInteger writes, int expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
        while (writes.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertEquals(expected, writes.get());
    }
}
