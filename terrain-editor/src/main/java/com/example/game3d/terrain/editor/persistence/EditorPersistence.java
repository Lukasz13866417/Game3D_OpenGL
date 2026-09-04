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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class EditorPersistence {
    private final TerrainJsonCodec codec;
    private final TerrainDocumentStore store;

    public EditorPersistence(TerrainJsonCodec codec) {
        this.codec = codec;
        this.store = new TerrainDocumentStore(codec, new AtomicFileStore());
    }

    public EditorState save(EditorState state, Path target) throws IOException {
        ResolvedSaveTarget resolved = resolveSaveTarget(target);
        String digest = store.save(resolved.replacementTarget(), state.document(), ignored -> {
            if (!symbolicAliasStillTargets(resolved)) {
                throw new IOException("Save target symlink changed before replacement: "
                        + resolved.requestedPath());
            }
        });
        Optional<DiskVersion> written = diskVersionIfPresent(resolved.requestedPath());
        if (!symbolicAliasStillTargets(resolved) || written.isEmpty()
                || !digest.equals(written.get().rawSha256())) {
            throw new IOException("Save target changed during replacement: "
                    + resolved.requestedPath());
        }
        return state.markSaved(resolved.requestedPath(), digest);
    }

    /**
     * Saves only with the caller's explicit target-version authority. Conflict checks run after
     * the temporary file is flushed and immediately before atomic replacement.
     */
    public SaveResult save(
            EditorState state,
            Path target,
            ExpectedDiskVersion expected,
            SaveIntent intent) throws IOException {
        if (state == null || target == null || expected == null || intent == null) {
            throw new IllegalArgumentException("Conditional save arguments are required");
        }
        Path requestedTarget = target.toAbsolutePath().normalize();
        validateIntent(requestedTarget, expected, intent);
        ResolvedSaveTarget resolved = resolveSaveTarget(requestedTarget);
        AtomicReference<Optional<DiskVersion>> rejectedActual = new AtomicReference<>();
        com.example.game3d.terrain.io.store.AtomicFileStore.BeforeReplace guard = ignored -> {
            Optional<DiskVersion> actual = diskVersionIfPresent(requestedTarget);
            boolean accepted = switch (intent) {
                case CREATE_NEW -> actual.isEmpty();
                case SAVE_IF_UNCHANGED, OVERWRITE_CONFIRMED -> actual.isPresent()
                        && expected.exactVersion().sameContent(actual.get());
            };
            // AtomicFileStore must replace the referent, never the directory entry containing a
            // symlink. Revalidate that binding at the last possible moment as part of the same
            // conditional-save decision.
            accepted &= symbolicAliasStillTargets(resolved);
            if (!accepted) {
                rejectedActual.set(actual);
                throw new ConditionalWriteRejected();
            }
        };

        final String digest;
        try {
            digest = store.save(resolved.replacementTarget(), state.document(), guard);
        } catch (ConditionalWriteRejected conflict) {
            return new SaveResult.Conflict(expected,
                    rejectedActual.get() == null ? Optional.empty() : rejectedActual.get());
        }

        Optional<DiskVersion> written = diskVersionIfPresent(requestedTarget);
        if (!symbolicAliasStillTargets(resolved) || written.isEmpty()
                || !digest.equals(written.get().rawSha256())) {
            // Another writer won immediately after our atomic move. Do not mark this editor state
            // saved against bytes that are no longer on disk.
            return new SaveResult.Conflict(expected, written);
        }
        return new SaveResult.Saved(
                state.markSaved(requestedTarget, digest), written.get());
    }

    public LoadedDocument load(Path source) throws IOException, CodecException {
        ReadDisk observed = readDiskVersionIfPresent(source)
                .orElseThrow(() -> new java.nio.file.NoSuchFileException(source.toString()));
        TerrainSourceDocument document = codec.decode(
                new String(observed.bytes(), StandardCharsets.UTF_8));
        String encoded = codec.encode(document);
        EditorState state = EditorState.unsaved(document).markSaved(source.toAbsolutePath(),
                ContentDigests.sha256(encoded));
        DiskVersion diskVersion = observed.version();
        return new LoadedDocument(state, diskVersion.modifiedTime(), diskVersion);
    }

    public DiskVersion diskVersion(Path source) throws IOException {
        return diskVersionIfPresent(source).orElseThrow(
                () -> new java.nio.file.NoSuchFileException(source.toString()));
    }

    /** Exact-byte external-change check; timestamps are not authoritative. */
    public boolean externallyChanged(Path source, DiskVersion knownVersion) throws IOException {
        if (source == null || knownVersion == null) {
            throw new IllegalArgumentException("source and knownVersion are required");
        }
        if (!sameFileIdentity(knownVersion.path(), source)) return true;
        Optional<DiskVersion> actual = diskVersionIfPresent(source);
        return actual.isEmpty() || !knownVersion.sameContent(actual.get());
    }

    /** @deprecated Use the digest-based overload. Retained until the workspace UI migrates. */
    @Deprecated
    public boolean externallyChanged(Path source, FileTime knownWriteTime) throws IOException {
        return !Files.getLastModifiedTime(source).equals(knownWriteTime);
    }

    private Optional<DiskVersion> diskVersionIfPresent(Path source) throws IOException {
        try {
            return readDiskVersionIfPresent(source).map(ReadDisk::version);
        } catch (java.nio.file.NoSuchFileException disappeared) {
            return Optional.empty();
        }
    }

    private Optional<ReadDisk> readDiskVersionIfPresent(Path source) throws IOException {
        if (!Files.exists(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            BasicFileAttributes before = Files.readAttributes(
                    source, BasicFileAttributes.class);
            byte[] bytes = Files.readAllBytes(source);
            BasicFileAttributes after = Files.readAttributes(
                    source, BasicFileAttributes.class);
            String beforeIdentity = filesystemIdentity(source, before);
            String afterIdentity = filesystemIdentity(source, after);
            if (before.size() == bytes.length && after.size() == bytes.length
                    && beforeIdentity.equals(afterIdentity)
                    && before.lastModifiedTime().equals(after.lastModifiedTime())) {
                return Optional.of(new ReadDisk(bytes, new DiskVersion(
                        source, ContentDigests.sha256(bytes), bytes.length,
                        after.lastModifiedTime(), afterIdentity)));
            }
        }
        throw new IOException("File changed repeatedly while it was being read: " + source);
    }

    private static String filesystemIdentity(
            Path source, BasicFileAttributes attributes) throws IOException {
        Object key = attributes.fileKey();
        return key == null ? "real:" + source.toRealPath() : "key:" + key;
    }

    private static void validateIntent(
            Path target, ExpectedDiskVersion expected, SaveIntent intent) throws IOException {
        ExpectedDiskVersion.Kind required = switch (intent) {
            case CREATE_NEW -> ExpectedDiskVersion.Kind.ABSENT;
            case SAVE_IF_UNCHANGED, OVERWRITE_CONFIRMED -> ExpectedDiskVersion.Kind.EXACT;
        };
        if (expected.kind() != required) {
            throw new IllegalArgumentException(intent + " requires " + required);
        }
        if (required == ExpectedDiskVersion.Kind.EXACT
                && !sameFileIdentity(expected.exactVersion().path(), target)) {
            throw new IllegalArgumentException(
                    "Expected disk version belongs to a different target");
        }
        if (required == ExpectedDiskVersion.Kind.EXACT
                && !expected.exactVersion().path().equals(
                target.toAbsolutePath().normalize())) {
            // Atomic replacement rebinds one directory entry. Treating a different hard-link (or
            // symlink) spelling as the same Save As target would silently split aliases: the
            // selected entry would get the new inode while the original name kept the old one.
            throw new IllegalArgumentException(
                    "Save target is an alias of the expected file; use the original path or "
                            + "choose a distinct Save As target");
        }
    }

    /**
     * Resolves a final-component symlink to its referent so an atomic rename never unlinks the
     * symlink itself. The requested spelling remains the document's source path and is used for
     * every observed {@link DiskVersion}.
     */
    private static ResolvedSaveTarget resolveSaveTarget(Path target) throws IOException {
        Path requested = target.toAbsolutePath().normalize();
        if (!Files.isSymbolicLink(requested)) {
            return new ResolvedSaveTarget(requested, requested, false);
        }
        try {
            return new ResolvedSaveTarget(requested, requested.toRealPath(), true);
        } catch (java.nio.file.NoSuchFileException dangling) {
            throw new IOException("Refusing to replace dangling save-target symlink: "
                    + requested, dangling);
        }
    }

    private static boolean symbolicAliasStillTargets(ResolvedSaveTarget resolved)
            throws IOException {
        if (!resolved.symbolicLink()) return true;
        if (!Files.isSymbolicLink(resolved.requestedPath())) return false;
        try {
            return Files.isSameFile(
                    resolved.requestedPath(), resolved.replacementTarget());
        } catch (java.nio.file.NoSuchFileException disappeared) {
            return false;
        }
    }

    private static boolean sameFileIdentity(Path left, Path right) throws IOException {
        Path normalizedLeft = left.toAbsolutePath().normalize();
        Path normalizedRight = right.toAbsolutePath().normalize();
        if (normalizedLeft.equals(normalizedRight)) return true;
        return Files.exists(normalizedLeft) && Files.exists(normalizedRight)
                && Files.isSameFile(normalizedLeft, normalizedRight);
    }

    private static final class ConditionalWriteRejected extends IOException {
    }

    private record ReadDisk(byte[] bytes, DiskVersion version) {}

    /**
     * Hard links intentionally are not resolved here: atomic save replaces only the requested
     * directory entry, so other hard links keep the previous inode and bytes.
     */
    private record ResolvedSaveTarget(
            Path requestedPath, Path replacementTarget, boolean symbolicLink) {}

    public record LoadedDocument(
            EditorState state, FileTime writeTime, DiskVersion diskVersion) {}
}
