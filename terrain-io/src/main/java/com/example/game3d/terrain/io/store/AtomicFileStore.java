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
    public void writeUtf8(Path target, String content) throws IOException {
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
            // Publishing and editor saves promise atomic replacement. If the target filesystem
            // cannot provide that guarantee, fail and leave the previous good file untouched.
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }
}
