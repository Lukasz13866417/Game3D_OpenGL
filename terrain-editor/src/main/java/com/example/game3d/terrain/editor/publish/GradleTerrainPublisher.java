package com.example.game3d.terrain.editor.publish;

import com.example.game3d.terrain.editor.persistence.ProjectSettings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Real Build hook: invokes the project's shared atomic publishTerrainContent pipeline. */
public final class GradleTerrainPublisher {
    public int publish(Path projectRoot, Consumer<String> output) throws IOException, InterruptedException {
        if (!ProjectSettings.isProjectRoot(projectRoot))
            throw new IllegalArgumentException("Choose a project root containing gradlew and settings.gradle.kts");
        Process process = new ProcessBuilder(projectRoot.resolve("gradlew").toString(),
                "--console=plain", "publishTerrainContent")
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) output.accept(line);
        }
        return process.waitFor();
    }
}
