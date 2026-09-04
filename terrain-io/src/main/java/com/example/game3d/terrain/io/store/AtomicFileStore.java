package com.example.game3d.terrain.io.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Writes a sibling temporary file, flushes it, and replaces the target as one operation. */
public final class AtomicFileStore {
    /** Runs after the temporary file is durable and immediately before target replacement. */
    public interface BeforeReplace {
        void verify(Path target) throws IOException;
    }

    private static final BeforeReplace ALWAYS = new BeforeReplace() {
        @Override public void verify(Path target) {
            // Unconditional replacement for callers that have no external-version contract.
        }
    };

    public void writeUtf8(Path target, String content) throws IOException {
        writeUtf8(target, content, ALWAYS);
    }

    /**
     * Atomically writes UTF-8 only after the supplied last-moment replacement guard succeeds.
     * A rejected guard leaves the original target untouched and removes the temporary file.
     */
    public void writeUtf8(Path target, String content, BeforeReplace beforeReplace)
            throws IOException {
        if (beforeReplace == null) {
            throw new IllegalArgumentException("beforeReplace == null");
        }
        Path absolute = target.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("Target has no parent: " + target);
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, absolute.getFileName().toString() + ".", ".tmp");
        boolean moved = false;
        try {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            beforeReplace.verify(absolute);
            // Publishing and editor saves promise atomic replacement. If the target filesystem
            // cannot provide that guarantee, fail and leave the previous good file untouched.
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            moved = true;
            forceDirectoryBestEffort(parent);
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    /** Persists the rename itself on filesystems that permit opening directories as channels. */
    private static void forceDirectoryBestEffort(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException unsupported) {
            // Windows and some virtual filesystems reject directory channels. The file payload
            // remains flushed and atomically replaced; directory fsync is an optional hardening.
        }
    }
}
