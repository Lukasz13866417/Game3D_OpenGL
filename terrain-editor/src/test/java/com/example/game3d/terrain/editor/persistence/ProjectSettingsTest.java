package com.example.game3d.terrain.editor.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSettingsTest {
    @TempDir Path directory;

    @Test void discoversNearestGradleProjectRootFromNestedDirectory() throws Exception {
        Files.createFile(directory.resolve("gradlew"));
        Files.createFile(directory.resolve("settings.gradle.kts"));
        Path nested = Files.createDirectories(directory.resolve("terrain-content/structures"));

        assertEquals(directory.toAbsolutePath().normalize(),
                ProjectSettings.discover(nested).orElseThrow());
        assertTrue(ProjectSettings.isProjectRoot(directory));
    }
}
