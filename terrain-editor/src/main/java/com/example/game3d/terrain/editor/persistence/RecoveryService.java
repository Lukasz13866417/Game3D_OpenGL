package com.example.game3d.terrain.editor.persistence;

import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.io.CodecException;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;
import com.example.game3d.terrain.io.store.AtomicFileStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Atomic, collision-safe recovery snapshots after an idle delay or focus loss. */
public final class RecoveryService implements AutoCloseable {
    private static final int FORMAT_VERSION = 1;
    private static final Gson JSON = new GsonBuilder()
            .disableHtmlEscaping().setPrettyPrinting().create();

    public record RecoveryDraft(
            Path path,
            long modifiedMillis,
            String documentId,
            Path originalSourcePath) {
        @Override public String toString() {
            String source = originalSourcePath == null
                    ? "untitled" : originalSourcePath.toString();
            return documentId + " — " + source + " — "
                    + java.time.Instant.ofEpochMilli(modifiedMillis);
        }
    }

    public record RestoreResult(
            EditorState state,
            boolean reboundToOriginal,
            Path originalSourcePath) {
        public boolean requiresSaveAs() {
            return state.sourcePath() == null;
        }
    }

    private final TerrainJsonCodec codec;
    private final AtomicFileStore files;
    private final Path directory;
    private final String instanceId;
    private final long idleMillis;
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> pending;
    private EditorState latest;

    public RecoveryService(TerrainJsonCodec codec, Path directory, Duration idle) {
        this(codec, directory, idle, UUID.randomUUID().toString());
    }

    RecoveryService(
            TerrainJsonCodec codec, Path directory, Duration idle,
            String instanceId) {
        if (codec == null || directory == null || idle == null
                || instanceId == null || instanceId.isEmpty()) {
            throw new IllegalArgumentException("Recovery arguments are required");
        }
        this.codec = codec;
        this.files = new AtomicFileStore();
        this.directory = directory;
        this.instanceId = instanceId;
        this.idleMillis = idle.toMillis();
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "terrain-editor-recovery");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void edited(EditorState state) {
        latest = state;
        if (pending != null) pending.cancel(false);
        pending = executor.schedule(this::saveLatestUnchecked,
                idleMillis, TimeUnit.MILLISECONDS);
    }

    public synchronized void focusLost() {
        saveLatestUnchecked();
    }

    /** Synchronously writes a replacement draft, reporting failure to the caller. */
    public synchronized Path checkpoint(EditorState state) throws IOException {
        if (state == null) throw new IllegalArgumentException("state == null");
        latest = state;
        if (pending != null) pending.cancel(false);
        pending = null;
        Path path = pathFor(state);
        files.writeUtf8(path, encodeEnvelope(state, codec));
        return path;
    }

    public Path pathFor(EditorState state) {
        String safe = state.document().id().replaceAll("[^A-Za-z0-9._-]", "_");
        return directory.resolve(safe + "-" + instanceId + ".recovery.json");
    }

    public synchronized void clear(EditorState state) throws IOException {
        Files.deleteIfExists(pathFor(state));
        latest = null;
        if (pending != null) pending.cancel(false);
        pending = null;
    }

    public static List<RecoveryDraft> list(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return java.util.Collections.emptyList();
        List<RecoveryDraft> result = new ArrayList<>();
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".recovery.json"))
                    .forEach(path -> {
                        try {
                            long modified = Files.getLastModifiedTime(path).toMillis();
                            RecoveryMetadata metadata = metadata(path);
                            result.add(new RecoveryDraft(path, modified,
                                    metadata.documentId, metadata.sourcePath));
                        } catch (IOException | RuntimeException ignored) {
                            // A corrupt/incomplete best-effort record is not restorable.
                        }
                    });
        }
        result.sort(Comparator.comparingLong(RecoveryDraft::modifiedMillis).reversed()
                .thenComparing(value -> value.path().toString()));
        return java.util.Collections.unmodifiableList(result);
    }

    /**
     * Restores the recovered document. Its original path is retained only when that file's
     * current canonical digest still equals the recovery record's saved base digest.
     */
    public static RestoreResult restore(
            RecoveryDraft draft,
            TerrainJsonCodec codec,
            EditorPersistence persistence) throws IOException, CodecException {
        if (draft == null || codec == null || persistence == null) {
            throw new IllegalArgumentException("Recovery restore arguments are required");
        }
        Envelope envelope = readEnvelope(draft.path(), codec);
        Path source = envelope.sourcePath;
        if (source != null && envelope.baseDigest != null) {
            try {
                EditorPersistence.LoadedDocument current = persistence.load(source);
                if (envelope.baseDigest.equals(
                        current.state().savedContentDigest())) {
                    EditorState rebound = new EditorState(
                            envelope.document, source, envelope.baseDigest,
                            Set.of(), 0L, List.of());
                    return new RestoreResult(rebound, true, source);
                }
            } catch (IOException | CodecException changedMissingOrInvalid) {
                // Fall through to an untitled recovery. Never bind uncertain disk state.
            }
        }
        return new RestoreResult(EditorState.unsaved(envelope.document), false, source);
    }

    public static void deleteDraft(RecoveryDraft draft) throws IOException {
        if (draft == null) throw new IllegalArgumentException("draft == null");
        Files.deleteIfExists(draft.path());
    }

    private synchronized void saveLatestUnchecked() {
        if (latest == null) return;
        try {
            files.writeUtf8(pathFor(latest), encodeEnvelope(latest, codec));
        } catch (IOException ignored) {
            // Explicit Save reports storage errors; recovery remains best effort.
        }
    }

    static String encodeEnvelope(EditorState state, TerrainJsonCodec codec) {
        JsonObject root = new JsonObject();
        root.addProperty("recoveryFormatVersion", FORMAT_VERSION);
        root.addProperty("documentId", state.document().id());
        if (state.sourcePath() == null) root.add("sourcePath", JsonNull.INSTANCE);
        else root.addProperty("sourcePath",
                state.sourcePath().toAbsolutePath().normalize().toString());
        if (state.savedContentDigest() == null) root.add("baseDigest", JsonNull.INSTANCE);
        else root.addProperty("baseDigest", state.savedContentDigest());
        root.add("document", JsonParser.parseString(codec.encode(state.document())));
        return JSON.toJson(root) + "\n";
    }

    private static Envelope readEnvelope(Path path, TerrainJsonCodec codec)
            throws IOException, CodecException {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(Files.readString(path));
        } catch (RuntimeException invalid) {
            throw new CodecException("Invalid recovery JSON", invalid);
        }
        JsonObject root = parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        if (root == null || !root.has("recoveryFormatVersion")) {
            // Compatibility for recovery drafts created before envelopes were introduced.
            TerrainSourceDocument legacy = codec.decode(parsed.toString());
            return new Envelope(legacy, null, null);
        }
        requireVersion(root);
        JsonElement document = root.get("document");
        if (document == null || document.isJsonNull()) {
            throw new CodecException("Recovery document is missing");
        }
        TerrainSourceDocument decoded = codec.decode(document.toString());
        String declaredId = requiredString(root, "documentId");
        if (!declaredId.equals(decoded.id())) {
            throw new CodecException("Recovery document ID does not match metadata");
        }
        Path source = optionalString(root, "sourcePath") == null ? null
                : Path.of(optionalString(root, "sourcePath"))
                .toAbsolutePath().normalize();
        String digest = optionalString(root, "baseDigest");
        if (digest != null && !digest.matches("[0-9a-f]{64}")) {
            throw new CodecException("Recovery base digest is malformed");
        }
        return new Envelope(decoded, source, digest);
    }

    private static RecoveryMetadata metadata(Path path) throws IOException {
        JsonElement parsed = JsonParser.parseString(Files.readString(path));
        if (!parsed.isJsonObject()) throw new IOException("Recovery root is not an object");
        JsonObject root = parsed.getAsJsonObject();
        if (!root.has("recoveryFormatVersion")) {
            String legacyId = root.has("id") && root.get("id").isJsonPrimitive()
                    ? root.get("id").getAsString() : path.getFileName().toString();
            return new RecoveryMetadata(legacyId, null);
        }
        requireVersion(root);
        String source = optionalString(root, "sourcePath");
        return new RecoveryMetadata(requiredString(root, "documentId"),
                source == null ? null : Path.of(source).toAbsolutePath().normalize());
    }

    private static void requireVersion(JsonObject root) throws IOException {
        if (!root.has("recoveryFormatVersion")
                || root.get("recoveryFormatVersion").getAsInt() != FORMAT_VERSION) {
            throw new IOException("Unsupported recovery format");
        }
    }

    private static String requiredString(JsonObject root, String name) throws IOException {
        String value = optionalString(root, name);
        if (value == null || value.isEmpty()) {
            throw new IOException("Recovery " + name + " is missing");
        }
        return value;
    }

    private static String optionalString(JsonObject root, String name) throws IOException {
        if (!root.has(name) || root.get(name).isJsonNull()) return null;
        if (!root.get(name).isJsonPrimitive()
                || !root.get(name).getAsJsonPrimitive().isString()) {
            throw new IOException("Recovery " + name + " is not a string");
        }
        return root.get(name).getAsString();
    }

    @Override public synchronized void close() {
        saveLatestUnchecked();
        executor.shutdownNow();
    }

    private record Envelope(
            TerrainSourceDocument document,
            Path sourcePath,
            String baseDigest) {
    }

    private record RecoveryMetadata(String documentId, Path sourcePath) {
    }
}
