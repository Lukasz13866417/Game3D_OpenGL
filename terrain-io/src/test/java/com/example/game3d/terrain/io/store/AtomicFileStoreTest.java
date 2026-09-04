package com.example.game3d.terrain.io.store;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class AtomicFileStoreTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void rejectedLastMomentGuardPreservesOriginalTarget() throws Exception {
        Path target = temporary.newFile("guarded.json").toPath();
        Files.write(target, "original".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        try {
            new AtomicFileStore().writeUtf8(target, "replacement", path -> {
                throw new IOException("version changed");
            });
            fail("Expected guard rejection");
        } catch (IOException expected) {
            assertEquals("version changed", expected.getMessage());
        }

        assertEquals("original", new String(Files.readAllBytes(target),
                java.nio.charset.StandardCharsets.UTF_8));
        try (java.util.stream.Stream<Path> files = Files.list(target.getParent())) {
            assertEquals(1L, files.count());
        }
    }

    @Test public void externalMutationInsideGuardWinsAndIsNeverOverwritten() throws Exception {
        Path target = temporary.newFile("raced.json").toPath();
        Files.write(target, "original".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        try {
            new AtomicFileStore().writeUtf8(target, "editor", path -> {
                Files.write(path, "external".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                throw new IOException("conflict");
            });
            fail("Expected guard rejection");
        } catch (IOException expected) {
            assertEquals("conflict", expected.getMessage());
        }

        assertEquals("external", new String(Files.readAllBytes(target),
                java.nio.charset.StandardCharsets.UTF_8));
    }
}
