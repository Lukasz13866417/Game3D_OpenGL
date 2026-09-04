package com.example.game3d.terrain.editor;

import com.example.game3d.terrain.editor.persistence.EditorPersistence;
import com.example.game3d.terrain.editor.persistence.ProjectSettings;
import com.example.game3d.terrain.editor.persistence.RecoveryService;
import com.example.game3d.terrain.editor.persistence.RecoveryStatusListener;
import com.example.game3d.terrain.editor.persistence.UserStateDirectory;
import com.example.game3d.terrain.editor.ui.EditorWorkspaceDependencies;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import javafx.application.Platform;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/** Application-level side-effect boundaries; tests can bind every state path to a temp tree. */
record TerrainEditorAppDependencies(
        TerrainJsonCodec codec,
        EditorPersistence persistence,
        ProjectSettings projectSettings,
        Path workspaceRecoveryDirectory,
        Path catalogRecoveryDirectory,
        EditorWorkspaceDependencies workspaceDependencies,
        CatalogRecoveryFactory catalogRecoveryFactory) {

    TerrainEditorAppDependencies {
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(projectSettings, "projectSettings");
        Objects.requireNonNull(workspaceRecoveryDirectory, "workspaceRecoveryDirectory");
        Objects.requireNonNull(catalogRecoveryDirectory, "catalogRecoveryDirectory");
        Objects.requireNonNull(workspaceDependencies, "workspaceDependencies");
        Objects.requireNonNull(catalogRecoveryFactory, "catalogRecoveryFactory");
    }

    static TerrainEditorAppDependencies production() {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        EditorPersistence persistence = new EditorPersistence(codec);
        ProjectSettings settings = new ProjectSettings();
        Path state = UserStateDirectory.terrainEditor();
        Path workspaceRecovery = state.resolve("recovery");
        Path catalogRecovery = state.resolve("catalog-recovery");
        EditorWorkspaceDependencies workspaces =
                EditorWorkspaceDependencies.production(
                        codec, persistence, settings, workspaceRecovery);
        return new TerrainEditorAppDependencies(
                codec, persistence, settings, workspaceRecovery, catalogRecovery,
                workspaces,
                listener -> new RecoveryService(codec, catalogRecovery,
                        Duration.ofSeconds(2), Platform::runLater, listener));
    }

    @FunctionalInterface
    interface CatalogRecoveryFactory {
        RecoveryService create(RecoveryStatusListener listener);
    }
}
