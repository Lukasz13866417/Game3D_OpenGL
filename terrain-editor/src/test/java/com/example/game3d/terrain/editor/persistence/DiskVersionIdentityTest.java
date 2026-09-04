package com.example.game3d.terrain.editor.persistence;

import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.GridMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskVersionIdentityTest {
    @TempDir Path directory;

    @Test void hardLinkAliasHasTheSameObservedFilesystemIdentity() throws Exception {
        EditorPersistence persistence = new EditorPersistence(new TerrainJsonCodec());
        Path source = directory.resolve("source.json");
        persistence.save(EditorState.unsaved(DocumentFactories.blankStructure(
                "identity", GridMode.ADVANCED)), source);
        DiskVersion observed = persistence.diskVersion(source);
        Path alias = directory.resolve("alias.json");
        Files.createLink(alias, source);

        assertFalse(persistence.externallyChanged(alias, observed));
    }

    @Test void sameBytesInAReplacementFileAreStillANewDiskVersion() throws Exception {
        EditorPersistence persistence = new EditorPersistence(new TerrainJsonCodec());
        Path source = directory.resolve("source.json");
        persistence.save(EditorState.unsaved(DocumentFactories.blankStructure(
                "identity", GridMode.ADVANCED)), source);
        DiskVersion observed = persistence.diskVersion(source);
        byte[] bytes = Files.readAllBytes(source);
        Path replacement = directory.resolve("replacement.tmp");
        Files.write(replacement, bytes);
        Files.move(replacement, source, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);

        assertTrue(persistence.externallyChanged(source, observed));
    }
}
