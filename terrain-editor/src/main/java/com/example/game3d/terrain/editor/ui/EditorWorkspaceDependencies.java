package com.example.game3d.terrain.editor.ui;

import com.example.game3d.terrain.editor.compile.DebouncedCompiler;
import com.example.game3d.terrain.editor.compile.DocumentCompiler;
import com.example.game3d.terrain.editor.persistence.EditorPersistence;
import com.example.game3d.terrain.editor.persistence.ProjectSettings;
import com.example.game3d.terrain.editor.persistence.RecoveryService;
import com.example.game3d.terrain.editor.persistence.RecoveryStatusListener;
import com.example.game3d.terrain.editor.persistence.UserStateDirectory;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import javafx.application.Platform;

import java.time.Duration;
import java.nio.file.Path;
import java.util.Objects;

/** Explicit side-effect boundaries used by a workspace; tests provide hermetic implementations. */
public record EditorWorkspaceDependencies(
        TerrainJsonCodec codec,
        EditorPersistence persistence,
        RecoveryFactory recoveryFactory,
        CompilerFactory compilerFactory,
        LayoutPreferences layoutPreferences,
        WorkspacePrompts prompts,
        Path recoveryDirectory) {
    public EditorWorkspaceDependencies {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(recoveryFactory, "recoveryFactory");
        Objects.requireNonNull(compilerFactory, "compilerFactory");
        Objects.requireNonNull(layoutPreferences, "layoutPreferences");
        Objects.requireNonNull(prompts, "prompts");
        Objects.requireNonNull(recoveryDirectory, "recoveryDirectory");
    }

    public EditorWorkspaceDependencies(
            TerrainJsonCodec codec,
            EditorPersistence persistence,
            RecoveryFactory recoveryFactory,
            CompilerFactory compilerFactory) {
        this(codec, persistence, recoveryFactory, compilerFactory,
                LayoutPreferences.NONE, WorkspacePrompts.javaFx(),
                Path.of(System.getProperty("java.io.tmpdir"),
                        "game3d-terrain-editor-tests"));
    }

    public static EditorWorkspaceDependencies production() {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        return production(codec, new EditorPersistence(codec),
                new ProjectSettings(),
                UserStateDirectory.terrainEditor().resolve("recovery"));
    }

    public static EditorWorkspaceDependencies production(
            TerrainJsonCodec codec,
            EditorPersistence persistence,
            ProjectSettings settings,
            Path recoveryDirectory) {
        Objects.requireNonNull(recoveryDirectory, "recoveryDirectory");
        return new EditorWorkspaceDependencies(
                codec,
                persistence,
                listener -> new RecoveryService(codec,
                        recoveryDirectory,
                        Duration.ofSeconds(4), Platform::runLater, listener),
                compiler -> new DebouncedCompiler(
                        compiler, Duration.ofMillis(180), Platform::runLater),
                new SettingsLayoutPreferences(settings),
                WorkspacePrompts.javaFx(), recoveryDirectory);
    }

    @FunctionalInterface
    public interface RecoveryFactory {
        RecoveryService create(RecoveryStatusListener listener);
    }

    @FunctionalInterface
    public interface CompilerFactory {
        DebouncedCompiler create(DocumentCompiler compiler);
    }

    public interface LayoutPreferences {
        LayoutPreferences NONE = new LayoutPreferences() {
            @Override public double read(String key, double fallback) { return fallback; }
            @Override public void write(String key, double value) { }
        };

        double read(String key, double fallback);
        void write(String key, double value);
    }

    private record SettingsLayoutPreferences(ProjectSettings settings)
            implements LayoutPreferences {
        @Override public double read(String key, double fallback) {
            return settings.panePosition(key, fallback);
        }

        @Override public void write(String key, double value) {
            settings.setPanePosition(key, value);
        }
    }
}
