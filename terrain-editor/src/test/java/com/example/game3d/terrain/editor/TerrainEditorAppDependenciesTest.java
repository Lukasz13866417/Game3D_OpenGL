package com.example.game3d.terrain.editor;

import com.example.game3d.terrain.editor.compile.DebouncedCompiler;
import com.example.game3d.terrain.editor.persistence.EditorPersistence;
import com.example.game3d.terrain.editor.persistence.ProjectSettings;
import com.example.game3d.terrain.editor.persistence.RecoveryHealth;
import com.example.game3d.terrain.editor.persistence.RecoveryService;
import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.editor.ui.EditorWorkspaceDependencies;
import com.example.game3d.terrain.editor.ui.WorkspacePrompts;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the application-wide side-effect boundary used by editor tests. */
class TerrainEditorAppDependenciesTest {
    @TempDir Path temporaryState;

    @Test
    void injectedApplicationStateKeepsPreferencesAndBothRecoveryKindsInTempTree()
            throws Exception {
        Path project = createProject(temporaryState.resolve("project"));
        Path workspaceRecovery = temporaryState.resolve("state/workspaces").toAbsolutePath();
        Path catalogRecovery = temporaryState.resolve("state/catalog").toAbsolutePath();
        InMemoryPreferences preferences = new InMemoryPreferences();
        ProjectSettings settings = new ProjectSettings(preferences);
        settings.setProjectRoot(project);
        settings.setRecentDocuments(List.of(project.resolve("one.terrain-level.json")));
        settings.setPanePosition("editor.main.left", .31);

        TerrainJsonCodec codec = new TerrainJsonCodec();
        EditorPersistence persistence = new EditorPersistence(codec);
        EditorWorkspaceDependencies.LayoutPreferences layoutPreferences =
                new EditorWorkspaceDependencies.LayoutPreferences() {
                    @Override public double read(String key, double fallback) {
                        return settings.panePosition(key, fallback);
                    }

                    @Override public void write(String key, double value) {
                        settings.setPanePosition(key, value);
                    }
                };
        EditorWorkspaceDependencies workspaceDependencies =
                new EditorWorkspaceDependencies(
                        codec,
                        persistence,
                        listener -> new RecoveryService(codec, workspaceRecovery,
                                Duration.ofHours(1), Runnable::run, listener),
                        compiler -> new DebouncedCompiler(
                                compiler, Duration.ZERO, Runnable::run),
                        layoutPreferences,
                        cancelingPrompts(),
                        workspaceRecovery);
        TerrainEditorAppDependencies dependencies = new TerrainEditorAppDependencies(
                codec,
                persistence,
                settings,
                workspaceRecovery,
                catalogRecovery,
                workspaceDependencies,
                listener -> new RecoveryService(codec, catalogRecovery,
                        Duration.ofHours(1), Runnable::run, listener));

        assertSame(codec, dependencies.codec());
        assertSame(persistence, dependencies.persistence());
        assertSame(settings, dependencies.projectSettings());
        assertSame(workspaceDependencies, dependencies.workspaceDependencies());
        assertEquals(workspaceRecovery, dependencies.workspaceRecoveryDirectory());
        assertEquals(catalogRecovery, dependencies.catalogRecoveryDirectory());
        assertEquals(project, settings.projectRoot().orElseThrow());
        assertEquals(.31, workspaceDependencies.layoutPreferences()
                .read("editor.main.left", .5), 1e-9);
        assertFalse(preferences.values.isEmpty(),
                "The injected in-memory preferences backend should receive all writes");

        EditorState workspaceState = EditorState.unsaved(
                DocumentFactories.blankStructure("workspace-draft", GridMode.ADVANCED));
        EditorState catalogState = EditorState.unsaved(
                new CatalogDocument(TerrainSourceDocument.CURRENT_FORMAT_VERSION,
                        "catalog-draft", List.of()));
        CopyOnWriteArrayList<RecoveryHealth> workspaceEvents =
                new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<RecoveryHealth> catalogEvents =
                new CopyOnWriteArrayList<>();

        Path workspaceDraft;
        try (RecoveryService recovery = dependencies.workspaceDependencies()
                .recoveryFactory().create(status -> workspaceEvents.add(status.health()))) {
            workspaceDraft = recovery.checkpoint(workspaceState);
        }
        Path catalogDraft;
        try (RecoveryService recovery = dependencies.catalogRecoveryFactory()
                .create(status -> catalogEvents.add(status.health()))) {
            catalogDraft = recovery.checkpoint(catalogState);
        }

        assertTrue(workspaceDraft.startsWith(workspaceRecovery));
        assertTrue(catalogDraft.startsWith(catalogRecovery));
        assertTrue(Files.isRegularFile(workspaceDraft));
        assertTrue(Files.isRegularFile(catalogDraft));
        assertTrue(workspaceEvents.contains(RecoveryHealth.SAVED));
        assertTrue(catalogEvents.contains(RecoveryHealth.SAVED));
        assertEquals(Set.of(workspaceDraft, catalogDraft),
                Set.copyOf(recoveryFilesUnder(temporaryState)));
    }

    @Test
    void editorTestsCannotSelectProductionStateOrPreferenceEntryPoints() throws Exception {
        Path testSources = findTestSources();
        Path thisSource = testSources.resolve(
                "com/example/game3d/terrain/editor/TerrainEditorAppDependenciesTest.java")
                .toAbsolutePath().normalize();
        List<String> violations = new ArrayList<>();
        List<String> forbidden = List.of(
                "UserStateDirectory.terrainEditor(",
                "TerrainEditorAppDependencies.production(",
                "EditorWorkspaceDependencies.production(",
                "new TerrainEditorApp()",
                "new ProjectSettings()",
                "Preferences.userNodeForPackage(",
                "Preferences.userRoot(",
                "Preferences.systemRoot(");

        try (Stream<Path> sources = Files.walk(testSources)) {
            sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toAbsolutePath().normalize().equals(thisSource))
                    .forEach(path -> inspectForForbiddenStateAccess(
                            testSources, path, forbidden, violations));
        }

        assertTrue(violations.isEmpty(),
                "Editor tests must inject temp recovery paths and in-memory preferences: "
                        + violations);
    }

    private static void inspectForForbiddenStateAccess(
            Path root,
            Path source,
            List<String> forbidden,
            List<String> violations) {
        try {
            String text = Files.readString(source);
            for (String token : forbidden) {
                if (text.contains(token)) {
                    violations.add(root.relativize(source) + " uses " + token);
                }
            }
        } catch (IOException error) {
            throw new AssertionError("Could not inspect " + source, error);
        }
    }

    private static List<Path> recoveryFilesUnder(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.getFileName().toString()
                            .endsWith(".recovery.json"))
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted()
                    .toList();
        }
    }

    private static Path findTestSources() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            Path fromRoot = cursor.resolve("terrain-editor/src/test/java");
            if (Files.isDirectory(fromRoot)) return fromRoot;
            Path fromModule = cursor.resolve("src/test/java");
            if (Files.isDirectory(fromModule)
                    && cursor.getFileName() != null
                    && cursor.getFileName().toString().equals("terrain-editor")) {
                return fromModule;
            }
            cursor = cursor.getParent();
        }
        throw new AssertionError("Could not locate terrain-editor/src/test/java");
    }

    private static Path createProject(Path directory) throws IOException {
        Files.createDirectories(directory);
        Files.createFile(directory.resolve("gradlew"));
        Files.createFile(directory.resolve("settings.gradle.kts"));
        return directory.toAbsolutePath().normalize();
    }

    private static WorkspacePrompts cancelingPrompts() {
        return new WorkspacePrompts() {
            @Override public SaveConflictChoice saveConflict(boolean targetPresent) {
                return SaveConflictChoice.CANCEL;
            }

            @Override public ExternalChangeChoice externalChange() {
                return ExternalChangeChoice.CANCEL;
            }

            @Override public CloseChoice closeDirty(String documentId) {
                return CloseChoice.CANCEL;
            }
        };
    }

    private static final class InMemoryPreferences extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, InMemoryPreferences> children = new HashMap<>();

        private InMemoryPreferences() {
            super(null, "");
        }

        private InMemoryPreferences(InMemoryPreferences parent, String name) {
            super(parent, name);
        }

        @Override protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override protected String getSpi(String key) {
            return values.get(key);
        }

        @Override protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override protected void removeNodeSpi() {
            values.clear();
            children.clear();
        }

        @Override protected String[] keysSpi() {
            return values.keySet().toArray(String[]::new);
        }

        @Override protected String[] childrenNamesSpi() {
            return children.keySet().toArray(String[]::new);
        }

        @Override protected AbstractPreferences childSpi(String name) {
            return children.computeIfAbsent(name,
                    key -> new InMemoryPreferences(this, key));
        }

        @Override protected void syncSpi() throws BackingStoreException {
        }

        @Override protected void flushSpi() throws BackingStoreException {
        }
    }
}
