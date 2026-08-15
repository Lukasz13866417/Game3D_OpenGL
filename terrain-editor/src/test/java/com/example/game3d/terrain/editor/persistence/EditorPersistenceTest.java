package com.example.game3d.terrain.editor.persistence;

import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.GridMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorPersistenceTest {
    @TempDir Path directory;

    @Test void detectsExternalWriteTimeChangesWithoutReloading() throws Exception {
        EditorPersistence persistence = new EditorPersistence(new TerrainJsonCodec());
        Path source = directory.resolve("terrain.json");
        persistence.save(EditorState.unsaved(
                DocumentFactories.blankStructure("external", GridMode.ADVANCED)), source);
        FileTime known = Files.getLastModifiedTime(source);
        assertFalse(persistence.externallyChanged(source, known));

        Files.setLastModifiedTime(source, FileTime.fromMillis(known.toMillis() + 2000));

        assertTrue(persistence.externallyChanged(source, known));
    }
}
