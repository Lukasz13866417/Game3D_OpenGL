package com.example.game3d.terrain.editor;

import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.terrain.editor.persistence.EditorPersistence;
import com.example.game3d.terrain.editor.persistence.DiskVersion;
import com.example.game3d.terrain.editor.persistence.ExpectedDiskVersion;
import com.example.game3d.terrain.editor.persistence.ProjectSettings;
import com.example.game3d.terrain.editor.persistence.ProjectTerrainDocumentRepository;
import com.example.game3d.terrain.editor.persistence.RecoveryService;
import com.example.game3d.terrain.editor.persistence.RecoveryHealth;
import com.example.game3d.terrain.editor.persistence.SaveIntent;
import com.example.game3d.terrain.editor.persistence.SaveResult;
import com.example.game3d.terrain.editor.publish.ProjectCommandRunner;
import com.example.game3d.terrain.editor.importing.BuiltinProviderImporter;
import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.editor.compile.EditorReferenceSnapshot;
import com.example.game3d.terrain.editor.session.EditorSessionIndex;
import com.example.game3d.terrain.editor.ui.EditorWorkspace;
import com.example.game3d.terrain.editor.ui.EditorWorkspaceDependencies;
import com.example.game3d.terrain.editor.ui.GameplayCatalogPane;
import com.example.game3d.terrain.editor.ui.ProviderImportDialog;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.catalog.CatalogDocumentEdits;
import com.example.game3d.terrain.io.catalog.GameplayCatalogPolicy;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.CatalogEntry;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.publish.PublishedGameplayCatalogLoader;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import com.example.game3d.terrain.io.store.AtomicFileStore;
import com.example.game3d.terrain.io.store.ContentDigests;
import com.example.game3d.terrain.io.validation.ValidationProblem;
import com.example.game3d.terrain.io.validation.ValidationResult;
import com.example.game3d.terrain.io.validation.TerrainValidator;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

public final class TerrainEditorApp extends Application implements TerrainDocumentRepository {
    private static final String CATALOG_FILE = "catalog.terrain-catalog.json";
    private static final String RUNTIME_CATALOG =
            "terrain-content/published/terrain/runtime-catalog.json";
    private final List<EditorWorkspace> workspaces = new ArrayList<>();
    private final List<Path> recent = new ArrayList<>();
    private final TabPane tabs = new TabPane();
    private final TerrainJsonCodec codec;
    private final EditorPersistence persistence;
    private final ProjectSettings projectSettings;
    private final ProjectTerrainDocumentRepository projectDocuments;
    private final Path workspaceRecoveryDirectory;
    private final Path catalogRecoveryDirectory;
    private final EditorWorkspaceDependencies workspaceDependencies;
    private final TerrainEditorAppDependencies.CatalogRecoveryFactory catalogRecoveryFactory;
    private final BuiltinProviderImporter builtInImporter = new BuiltinProviderImporter();
    private final Map<UUID, String> indexedSavedDigests = new LinkedHashMap<>();
    private final Map<UUID, Path> indexedSavedPaths = new LinkedHashMap<>();
    private EditorSessionIndex sessionIndex;
    private MenuItem saveItem;
    private MenuItem saveAsItem;
    private MenuItem undoItem;
    private MenuItem redoItem;
    private MenuItem publishItem;
    private MenuItem catalogItem;
    private MenuItem projectRootItem;
    private MenuItem reloadProjectItem;
    private CatalogDocument catalogDocument =
            CatalogDocumentEdits.newGameplayCatalog("gameplay-main");
    private EditorState catalogState = EditorState.unsaved(catalogDocument);
    private DiskVersion catalogDiskVersion;
    private RecoveryService catalogRecovery;
    private GameplayCatalogPane catalogPane;
    private Tab catalogTab;
    private EditorWorkspace lastActiveWorkspace;
    private String publishedCatalogDiagnostic = "No published artifact loaded";
    private final LinkedHashSet<String> publishedCatalogIds = new LinkedHashSet<>();
    private volatile ProjectCommandRunner.CancellationToken activeCommand;
    private volatile Thread activeBuildThread;
    private boolean buildRunning;
    private boolean catalogLoaded;
    private boolean catalogExternalConflictPending;
    private Stage stage;

    public TerrainEditorApp() {
        this(TerrainEditorAppDependencies.production());
    }

    TerrainEditorApp(TerrainEditorAppDependencies dependencies) {
        if (dependencies == null) throw new IllegalArgumentException("dependencies == null");
        codec = dependencies.codec();
        persistence = dependencies.persistence();
        projectSettings = dependencies.projectSettings();
        workspaceRecoveryDirectory = dependencies.workspaceRecoveryDirectory();
        catalogRecoveryDirectory = dependencies.catalogRecoveryDirectory();
        workspaceDependencies = dependencies.workspaceDependencies();
        catalogRecoveryFactory = dependencies.catalogRecoveryFactory();
        projectDocuments = new ProjectTerrainDocumentRepository(codec);
        sessionIndex = new EditorSessionIndex(projectDocuments.snapshotView(), List.of());
    }

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage stage) {
        this.stage = stage;
        recent.clear();
        recent.addAll(projectSettings.recentDocuments());
        projectSettings.projectRoot().ifPresent(root -> reloadProjectContent(root, false));
        BorderPane root = new BorderPane(tabs);
        root.setTop(menuBar());
        tabs.getTabs().add(welcomeTab());
        stage.setScene(new Scene(root, 1420, 860, true));
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.setTitle("Game3D Terrain Editor");
        stage.setOnCloseRequest(event -> {
            if (buildRunning) {
                ProjectCommandRunner.CancellationToken running = activeCommand;
                if (running != null) running.cancel();
                event.consume();
                showMessage(Alert.AlertType.INFORMATION, "Cancelling build",
                        "The project command is being cancelled. Close the editor again after "
                                + "the build dialog reports that cancellation is complete.");
                return;
            }
            List<EditorWorkspace> closing = new ArrayList<>(workspaces);
            for (EditorWorkspace workspace : closing) {
                if (!workspace.prepareClose(() -> chooseSaveTarget(workspace))) {
                    event.consume();
                    return;
                }
            }
            if (!prepareCatalogClose()) {
                event.consume();
                return;
            }
            for (EditorWorkspace workspace : closing) {
                if (!workspace.commitClose()) {
                    restoreCloseCheckpoints(closing);
                    event.consume();
                    return;
                }
            }
            if (!commitCatalogClose()) {
                restoreCloseCheckpoints(closing);
                event.consume();
                return;
            }
            ProjectCommandRunner.CancellationToken running = activeCommand;
            if (running != null) running.cancel();
            workspaces.forEach(EditorWorkspace::close);
            if (catalogRecovery != null) catalogRecovery.close();
        });
        stage.focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (focused) {
                active().ifPresent(EditorWorkspace::checkExternalChange);
                checkCatalogExternalChange();
            }
            else workspaces.forEach(EditorWorkspace::focusLost);
        });
        tabs.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldTab, newTab) -> {
                    active().ifPresent(workspace -> {
                        lastActiveWorkspace = workspace;
                        workspace.checkExternalChange();
                    });
                    updateActionStates();
                });
        updateActionStates();
        stage.show();
    }

    private MenuBar menuBar() {
        Menu file = new Menu("File");
        MenuItem structure = new MenuItem("New Blank Structure…");
        structure.setOnAction(event -> newStructure());
        MenuItem level = new MenuItem("New Blank Level…");
        level.setOnAction(event -> newLevel());
        MenuItem open = new MenuItem("Open…");
        open.setOnAction(event -> open());
        open.setAccelerator(new KeyCodeCombination(KeyCode.O,
                KeyCombination.SHORTCUT_DOWN));
        saveItem = new MenuItem("Save");
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S,
                KeyCombination.SHORTCUT_DOWN));
        saveItem.setOnAction(event -> save(false));
        saveAsItem = new MenuItem("Save As…");
        saveAsItem.setAccelerator(new KeyCodeCombination(KeyCode.S,
                KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        saveAsItem.setOnAction(event -> save(true));
        MenuItem javaProvider = new MenuItem("Preview / Import Java Provider…");
        javaProvider.setOnAction(event -> previewJavaProvider());
        file.getItems().addAll(structure, level, open, javaProvider, saveItem, saveAsItem);
        Menu edit = new Menu("Edit");
        undoItem = new MenuItem("Undo");
        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z,
                KeyCombination.SHORTCUT_DOWN));
        undoItem.setOnAction(event -> active().ifPresent(EditorWorkspace::undo));
        redoItem = new MenuItem("Redo");
        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z,
                KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
        redoItem.setOnAction(event -> active().ifPresent(EditorWorkspace::redo));
        edit.getItems().addAll(undoItem, redoItem);
        Menu build = new Menu("Build");
        projectRootItem = new MenuItem("Choose Project Root…");
        projectRootItem.setOnAction(event -> chooseProjectRoot());
        reloadProjectItem = new MenuItem("Reload Project Content");
        reloadProjectItem.setOnAction(event -> reloadProjectContent());
        catalogItem = new MenuItem("Gameplay Catalog…");
        catalogItem.setOnAction(event -> showCatalog());
        publishItem = new MenuItem("Publish Terrain Content");
        publishItem.setOnAction(event -> publish());
        build.getItems().addAll(projectRootItem, reloadProjectItem, catalogItem, publishItem);
        return new MenuBar(file, edit, build);
    }

    private Tab welcomeTab() {
        VBox content = new VBox(12);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(30));
        Label title = new Label("Terrain & Level Editor");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");
        Button structure = new Button("New Blank Structure"); structure.setOnAction(event -> newStructure());
        Button level = new Button("New Blank Level"); level.setOnAction(event -> newLevel());
        Button open = new Button("Open"); open.setOnAction(event -> open());
        Button recentButton = new Button("Recent"); recentButton.setOnAction(event -> openRecent());
        Button recover = new Button("Recover Draft"); recover.setOnAction(event -> recover());
        content.getChildren().addAll(title, structure, level, open, recentButton, recover);
        Tab tab = new Tab("Welcome", content); tab.setClosable(false); return tab;
    }

    private void newStructure() {
        ChoiceDialog<GridMode> mode = new ChoiceDialog<>(GridMode.ADVANCED, GridMode.ADVANCED, GridMode.BASIC);
        mode.setTitle("Blank Structure"); mode.setHeaderText("Choose the grid reservation mode");
        mode.showAndWait().ifPresent(value -> askId("structure.new").ifPresent(id ->
                addWorkspace(EditorState.unsaved(DocumentFactories.blankStructure(id, value)))));
    }

    private void newLevel() {
        askId("level.new").ifPresent(id -> addWorkspace(EditorState.unsaved(
                DocumentFactories.blankLevel(id, TrackProfile.GAMEPLAY_PROFILE_ID))));
    }

    private void previewJavaProvider() {
        List<String> providers = builtInImporter.providerIds();
        ChoiceDialog<String> providerDialog = new ChoiceDialog<>(providers.get(0), providers);
        providerDialog.setTitle("Java Terrain Providers");
        providerDialog.setHeaderText("Choose a parity-locked handwritten provider");
        Optional<String> provider = providerDialog.showAndWait();
        if (provider.isEmpty()) return;

        TextInputDialog ordinalDialog = new TextInputDialog("0");
        ordinalDialog.setTitle("Provider Level Ordinal");
        ordinalDialog.setHeaderText("The ordinal controls deterministic portal variants");
        Optional<String> ordinalText = ordinalDialog.showAndWait();
        if (ordinalText.isEmpty()) return;
        final long ordinal;
        try {
            ordinal = Long.parseLong(ordinalText.get().trim());
            if (ordinal < 0L) throw new NumberFormatException("negative");
        } catch (NumberFormatException invalid) {
            new Alert(Alert.AlertType.ERROR,
                    "Level ordinal must be a non-negative whole number.").showAndWait();
            return;
        }

        final BuiltinProviderImporter.ImportedProvider imported;
        try {
            imported = builtInImporter.materialize(provider.get(), ordinal);
        } catch (Exception error) {
            showError("Could not materialize Java provider", error);
            return;
        }
        Optional<ProviderImportDialog.Action> action = ProviderImportDialog.show(
                stage, provider.get() + " · ordinal " + ordinal + " · "
                        + imported.originalSnapshot().segments.size() + " tiles",
                imported.originalSnapshot());
        if (action.orElse(null) == ProviderImportDialog.Action.IMPORT_STRUCTURE) {
            askId(imported.structure().id()).ifPresent(id -> addWorkspace(
                    EditorState.unsaved(imported.structureWithId(id))));
        } else if (action.orElse(null)
                == ProviderImportDialog.Action.IMPORT_INLINE_LEVEL) {
            askId("imported.level." + provider.get()).ifPresent(id -> addWorkspace(
                    EditorState.unsaved(imported.inlineLevelWithId(id))));
        }
    }

    private Optional<String> askId(String suggestion) {
        TextInputDialog dialog = new TextInputDialog(suggestion);
        dialog.setTitle("Document ID"); dialog.setHeaderText("Stable document ID");
        return dialog.showAndWait().map(String::trim).filter(value -> !value.isEmpty());
    }

    private void open() {
        FileChooser chooser = jsonChooser("Open Terrain Document");
        File file = chooser.showOpenDialog(stage);
        if (file != null) open(file.toPath());
    }

    private void recover() {
        if (buildRunning) {
            showMessage(Alert.AlertType.INFORMATION, "Build in progress",
                    "Cancel or finish the current build before restoring a draft.");
            return;
        }
        Path directory = workspaceRecoveryDirectory;
        try {
            List<RecoveryService.RecoveryDraft> drafts = new ArrayList<>(
                    RecoveryService.list(directory));
            drafts.addAll(RecoveryService.list(catalogRecoveryDirectory));
            drafts.sort(java.util.Comparator
                    .comparingLong(RecoveryService.RecoveryDraft::modifiedMillis).reversed());
            if (drafts.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION, "No recovery drafts were found.").showAndWait();
                return;
            }
            ChoiceDialog<RecoveryService.RecoveryDraft> dialog = new ChoiceDialog<>(drafts.get(0), drafts);
            dialog.setTitle("Recover Draft");
            dialog.setHeaderText("A draft rebinds to its original file only when that file "
                    + "still matches the saved base version");
            dialog.showAndWait().ifPresent(draft -> {
                try {
                    RecoveryService.RestoreResult restored = RecoveryService.restore(
                            draft, codec, persistence);
                    if (restored.state().document() instanceof CatalogDocument catalog) {
                        Path original = restored.originalSourcePath();
                        Path candidateRoot = catalogRecoveryProjectRoot(original);
                        ProjectTerrainDocumentRepository candidateRepository =
                                new ProjectTerrainDocumentRepository(codec);
                        candidateRepository.reload(candidateRoot);
                        PublishedCatalogState candidatePublished =
                                readPublishedCatalogState(candidateRoot);
                        if (!confirmCatalogReplacement(
                                "Recovering this catalog will replace the current catalog edits.")) {
                            return;
                        }
                        EditorState restoredState = restored.state();
                        if (restoredState.sourcePath() == null) {
                            restoredState = new EditorState(catalog,
                                    catalogPath(candidateRoot), null,
                                    restoredState.selectedSourceIds(),
                                    restoredState.revision(), restoredState.problems());
                        }
                        commitProjectCatalogSwitch(candidateRoot, candidateRepository,
                                new CatalogCandidate(
                                        restoredState, restored.diskVersion(), catalog),
                                candidatePublished);
                        catalogRecovery.checkpoint(catalogState);
                        showCatalog();
                        RecoveryService.deleteDraft(draft);
                        return;
                    }
                    EditorState workspaceState = restored.state();
                    DiskVersion workspaceDiskVersion = restored.diskVersion();
                    if (workspaceState.sourcePath() != null
                            && workspaceForPath(workspaceState.sourcePath()) != null) {
                        workspaceState = EditorState.unsaved(workspaceState.document());
                        workspaceDiskVersion = null;
                        showMessage(Alert.AlertType.WARNING, "Recovered as an untitled copy",
                                "The original file is already open. The recovered draft was "
                                        + "opened as an untitled copy to avoid two tabs owning "
                                        + "the same file.");
                    }
                    EditorWorkspace workspace = addWorkspace(
                            workspaceState, workspaceDiskVersion);
                    workspace.checkpointRecovery();
                    RecoveryService.deleteDraft(draft);
                    if (restored.originalSourcePath() != null
                            && restored.requiresSaveAs()) {
                        new Alert(Alert.AlertType.WARNING,
                                "The original file is missing, invalid, or changed since this "
                                        + "draft was based on it. The recovered document is "
                                        + "untitled and requires Save As; the external file "
                                        + "will not be overwritten.").showAndWait();
                    }
                } catch (Exception error) { showError("Could not recover " + draft.path(), error); }
            });
        } catch (Exception error) {
            showError("Could not list recovery drafts", error);
        }
    }

    private void openRecent() {
        if (buildRunning) return;
        if (recent.isEmpty()) { new Alert(Alert.AlertType.INFORMATION, "No recent documents yet.").showAndWait(); return; }
        ChoiceDialog<Path> dialog = new ChoiceDialog<>(recent.get(0), recent);
        dialog.setTitle("Recent Documents"); dialog.showAndWait().ifPresent(this::open);
    }

    private void rememberRecent(Path path) {
        if (path == null) return;
        Path normalized = path.toAbsolutePath().normalize();
        recent.remove(normalized);
        recent.add(0, normalized);
        while (recent.size() > 12) recent.remove(recent.size() - 1);
        projectSettings.setRecentDocuments(recent);
    }

    private void open(Path path) {
        EditorWorkspace alreadyOpen = workspaceForPath(path);
        if (alreadyOpen != null) {
            selectWorkspace(alreadyOpen);
            return;
        }
        try {
            EditorPersistence.LoadedDocument loaded = persistence.load(path);
            if (loaded.state().document() instanceof CatalogDocument catalog) {
                openCatalog(path, loaded, catalog);
            } else {
                addWorkspace(loaded.state(), loaded.diskVersion());
            }
            rememberRecent(path);
        } catch (Exception error) {
            showError("Could not open " + path, error);
        }
    }

    private void save(boolean forceChoose) {
        if (buildRunning) return;
        if (isCatalogSelected()) {
            if (forceChoose) {
                showMessage(Alert.AlertType.INFORMATION, "Project catalog",
                        "The gameplay catalog has one canonical project path: "
                                + projectSettings.projectRoot().map(this::catalogPath)
                                .map(Path::toString).orElse("choose a project first"));
            } else {
                saveCatalog();
            }
            return;
        }
        active().ifPresent(workspace -> {
            Path target = forceChoose ? null : workspace.state().sourcePath();
            if (target == null) {
                File file = saveChooser(workspace).showSaveDialog(stage);
                if (file == null) return;
                target = file.toPath();
            }
            try {
                if (workspace.save(target, () -> chooseSaveTarget(workspace))) {
                    Path saved = workspace.state().sourcePath();
                    rememberRecent(saved);
                    warnIfStandalone(saved);
                }
            }
            catch (Exception error) { showError("Could not save " + target, error); }
        });
    }

    private EditorWorkspace addWorkspace(EditorState state) {
        return addWorkspace(state, null);
    }

    private EditorWorkspace addWorkspace(EditorState state, DiskVersion loadedDiskVersion) {
        EditorWorkspace workspace = new EditorWorkspace(
                state, projectDocuments.snapshotView(),
                workspaceDependencies, loadedDiskVersion);
        workspaces.add(workspace);
        Tab tab = new Tab();
        tab.setContent(workspace);
        Runnable title = () -> {
            tab.setText((workspace.dirty() ? "*" : "")
                    + workspace.state().document().id());
            updateActionStates();
        };
        workspace.setTitleChanged(title); title.run();
        workspace.setSessionChanged(() -> workspaceChanged(workspace));
        workspace.setProjectContentRoot(projectDocuments.contentRoot());
        tab.setOnCloseRequest(event -> {
            if (buildRunning) {
                event.consume();
                showMessage(Alert.AlertType.INFORMATION, "Build in progress",
                        "Finish or cancel the build before closing document tabs.");
                return;
            }
            if (!workspace.confirmClose(() -> chooseSaveTarget(workspace))) event.consume();
        });
        tab.setOnClosed(event -> workspaceClosed(workspace));
        tabs.getTabs().add(tab); tabs.getSelectionModel().select(tab);
        lastActiveWorkspace = workspace;
        indexedSavedDigests.put(workspace.workspaceId(), workspace.state().savedContentDigest());
        indexedSavedPaths.put(workspace.workspaceId(), workspace.state().sourcePath());
        rebuildSession(null, true);
        updateWorkspaceCatalogMembership();
        return workspace;
    }

    private void workspaceChanged(EditorWorkspace workspace) {
        boolean projectSnapshotReloaded = refreshSavedProjectSnapshotIfNeeded(workspace);
        updateWorkspaceCatalogMembership();
        // A project reload scans the whole content tree and can ingest unrelated external edits.
        // Conservatively invalidate every open workspace instead of attributing it only to the
        // document whose save triggered the scan.
        rebuildSession(projectSnapshotReloaded ? null : workspace,
                projectSnapshotReloaded);
    }

    private void workspaceClosed(EditorWorkspace workspace) {
        String closedId = workspace.state().document().id();
        EditorSessionIndex previous = sessionIndex;
        workspaces.remove(workspace);
        if (lastActiveWorkspace == workspace) {
            lastActiveWorkspace = workspaces.isEmpty()
                    ? null : workspaces.get(workspaces.size() - 1);
        }
        indexedSavedDigests.remove(workspace.workspaceId());
        indexedSavedPaths.remove(workspace.workspaceId());
        workspace.close();
        rebuildSessionAfterRemoval(previous, closedId);
    }

    private void rebuildSessionAfterRemoval(
            EditorSessionIndex previous, String changedId) {
        sessionIndex = createSessionIndex();
        LinkedHashSet<UUID> affected = new LinkedHashSet<>(
                previous.affectedBy(List.of(changedId)));
        affected.addAll(sessionIndex.affectedBy(List.of(changedId)));
        requestCompiles(affected);
    }

    private void rebuildSession(EditorWorkspace changed, boolean all) {
        EditorSessionIndex previous = sessionIndex;
        sessionIndex = createSessionIndex();
        if (all || changed == null) {
            requestCompiles(sessionIndex.allWorkspaces());
            return;
        }
        String changedId = changed.state().document().id();
        String previousId = previous.documentId(changed.workspaceId());
        LinkedHashSet<String> changedIds = new LinkedHashSet<>();
        if (previousId != null) changedIds.add(previousId);
        changedIds.add(changedId);
        LinkedHashSet<UUID> affected = new LinkedHashSet<>(
                previous.affectedBy(changedIds));
        affected.addAll(sessionIndex.affectedBy(changedIds));
        affected.add(changed.workspaceId());
        requestCompiles(affected);
    }

    private EditorSessionIndex createSessionIndex() {
        List<EditorReferenceSnapshot.OpenDocument> open = new ArrayList<>();
        for (EditorWorkspace workspace : workspaces) {
            open.add(new EditorReferenceSnapshot.OpenDocument(
                    workspace.workspaceId(), workspace.state().sourcePath(),
                    workspace.state().document()));
        }
        return new EditorSessionIndex(projectDocuments.snapshotView(), open);
    }

    private void requestCompiles(Set<UUID> workspaceIds) {
        for (EditorWorkspace workspace : workspaces) {
            if (workspaceIds.contains(workspace.workspaceId())) {
                workspace.requestCompile(sessionIndex.references());
            }
        }
    }

    private boolean refreshSavedProjectSnapshotIfNeeded(EditorWorkspace workspace) {
        UUID id = workspace.workspaceId();
        String current = workspace.state().savedContentDigest();
        Path currentPath = workspace.state().sourcePath();
        boolean known = indexedSavedDigests.containsKey(id);
        String previous = indexedSavedDigests.put(id, current);
        Path previousPath = indexedSavedPaths.put(id, currentPath);
        boolean changed = !java.util.Objects.equals(previous, current)
                || !java.util.Objects.equals(previousPath, currentPath);
        // Save As may cross the terrain-content boundary without needing a repository reload.
        // Refresh this workspace before either fast path returns.
        workspace.setProjectContentRoot(projectDocuments.contentRoot());
        if (!known || !changed || !isProjectContentPath(currentPath)) return false;
        Optional<Path> projectRoot = projectSettings.projectRoot();
        if (projectRoot.isEmpty()) return false;
        try {
            projectDocuments.reload(projectRoot.get());
            for (EditorWorkspace open : workspaces) {
                open.setProjectContentRoot(projectDocuments.contentRoot());
            }
            updateCatalogPresentation();
            return true;
        }
        catch (Exception error) {
            showError("Saved terrain, but could not refresh project references", error);
            return false;
        }
    }

    private boolean isProjectContentPath(Path path) {
        Path contentRoot = projectDocuments.contentRoot();
        return path != null && contentRoot != null
                && path.toAbsolutePath().normalize().startsWith(contentRoot);
    }

    private EditorWorkspace workspaceForPath(Path path) {
        if (path == null) return null;
        for (EditorWorkspace workspace : workspaces) {
            Path open = workspace.state().sourcePath();
            if (open == null) continue;
            try {
                if (java.nio.file.Files.isSameFile(open, path)) return workspace;
            } catch (java.io.IOException unavailable) {
                if (open.toAbsolutePath().normalize()
                        .equals(path.toAbsolutePath().normalize())) return workspace;
            }
        }
        return null;
    }

    private void selectWorkspace(EditorWorkspace workspace) {
        for (Tab tab : tabs.getTabs()) {
            if (tab.getContent() == workspace) {
                tabs.getSelectionModel().select(tab);
                return;
            }
        }
    }

    private Optional<EditorWorkspace> active() {
        if (tabs.getSelectionModel().getSelectedItem() == null) return Optional.empty();
        return workspaces.stream().filter(value -> tabs.getSelectionModel().getSelectedItem().getContent() == value).findFirst();
    }

    private FileChooser jsonChooser(String title) {
        FileChooser chooser = new FileChooser(); chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Terrain JSON", "*.json"));
        return chooser;
    }

    private FileChooser saveChooser(EditorWorkspace workspace) {
        FileChooser chooser = jsonChooser("Save Terrain Document");
        projectSettings.projectRoot().ifPresent(root -> {
            Path directory;
            String suffix;
            if (workspace.state().document() instanceof StructureDocument) {
                directory = root.resolve("terrain-content/structures");
                suffix = ".terrain-structure.json";
            } else if (workspace.state().document() instanceof LevelDocument) {
                directory = root.resolve("terrain-content/levels");
                suffix = ".terrain-level.json";
            } else {
                directory = root.resolve("terrain-content");
                suffix = ".json";
            }
            if (java.nio.file.Files.isDirectory(directory)) {
                chooser.setInitialDirectory(directory.toFile());
            }
            chooser.setInitialFileName(safeFilePart(
                    workspace.state().document().id()) + suffix);
        });
        return chooser;
    }

    private Path chooseSaveTarget(EditorWorkspace workspace) {
        File file = saveChooser(workspace).showSaveDialog(stage);
        return file == null ? null : file.toPath();
    }

    private void warnIfStandalone(Path saved) {
        Path content = projectDocuments.contentRoot();
        if (saved == null || content == null
                || saved.toAbsolutePath().normalize().startsWith(content)) return;
        new Alert(Alert.AlertType.INFORMATION,
                "Saved as a standalone draft. Publish will not include files outside "
                        + content + ".").showAndWait();
    }

    private static String safeFilePart(String id) {
        String safe = id == null ? "terrain" : id.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isEmpty() ? "terrain" : safe;
    }

    private static String safeRunPart(String id) {
        String value = id == null ? "terrain" : id;
        return safeFilePart(value) + "-"
                + ContentDigests.sha256(value).substring(0, 10);
    }

    private static Path editorRunDirectory(Path projectRoot, String stableId) {
        return projectRoot.resolve("build/editor-runs")
                .resolve(safeRunPart(stableId)).toAbsolutePath().normalize();
    }

    private void updateActionStates() {
        Optional<EditorWorkspace> active = active();
        boolean catalogSelected = isCatalogSelected();
        if (saveItem != null) saveItem.setDisable(
                buildRunning || (active.isEmpty() && !catalogSelected));
        if (saveAsItem != null) saveAsItem.setDisable(
                buildRunning || active.isEmpty());
        if (undoItem != null) undoItem.setDisable(active.isEmpty() || !active.get().canUndo());
        if (redoItem != null) redoItem.setDisable(active.isEmpty() || !active.get().canRedo());
        if (publishItem != null) publishItem.setDisable(buildRunning);
        if (catalogItem != null) catalogItem.setDisable(buildRunning);
        if (projectRootItem != null) projectRootItem.setDisable(buildRunning);
        if (reloadProjectItem != null) reloadProjectItem.setDisable(buildRunning);
    }

    private boolean isCatalogSelected() {
        return catalogTab != null
                && tabs.getSelectionModel().getSelectedItem() == catalogTab;
    }

    private void chooseProjectRoot() {
        if (buildRunning) return;
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Game3D Project Root");
        projectSettings.projectRoot().filter(java.nio.file.Files::isDirectory)
                .ifPresent(path -> chooser.setInitialDirectory(path.toFile()));
        File selected = chooser.showDialog(stage);
        if (selected == null) return;
        try {
            if (reloadProjectContent(selected.toPath(), true)) {
                projectSettings.setProjectRoot(selected.toPath());
            }
        }
        catch (IllegalArgumentException invalid) {
            new Alert(Alert.AlertType.ERROR, invalid.getMessage()).showAndWait();
        }
    }

    private void reloadProjectContent() {
        if (buildRunning) return;
        Optional<Path> root = projectSettings.projectRoot();
        if (root.isEmpty()) {
            chooseProjectRoot();
            return;
        }
        reloadProjectContent(root.get(), true);
    }

    private boolean reloadProjectContent(Path root, boolean reportFailure) {
        try {
            ProjectTerrainDocumentRepository candidateRepository =
                    new ProjectTerrainDocumentRepository(codec);
            candidateRepository.reload(root);
            CatalogCandidate candidateCatalog = readProjectCatalog(root);
            PublishedCatalogState candidatePublished = readPublishedCatalogState(root);
            if (reportFailure && !confirmCatalogReplacement(
                    "Reloading the project will replace the current catalog edits.")) {
                return false;
            }
            commitProjectCatalogSwitch(
                    root, candidateRepository, candidateCatalog, candidatePublished);
            return true;
        } catch (Exception error) {
            if (reportFailure) {
                showError("Could not reload saved project terrain content", error);
            }
            return false;
        }
    }

    private void loadProjectCatalog(Path projectRoot) throws Exception {
        CatalogCandidate candidate = readProjectCatalog(projectRoot);
        installCatalogState(candidate.state(), candidate.diskVersion(), candidate.document());
        applyPublishedCatalogState(readPublishedCatalogState(projectRoot));
    }

    private CatalogCandidate readProjectCatalog(Path projectRoot) throws Exception {
        Path path = catalogPath(projectRoot);
        if (Files.isRegularFile(path)) {
            EditorPersistence.LoadedDocument loaded = persistence.load(path);
            if (!(loaded.state().document() instanceof CatalogDocument catalog)) {
                throw new IllegalArgumentException(path + " is not a catalog document");
            }
            return new CatalogCandidate(loaded.state(), loaded.diskVersion(), catalog);
        } else {
            CatalogDocument catalog = CatalogDocumentEdits.newGameplayCatalog("gameplay-main");
            return new CatalogCandidate(new EditorState(
                    catalog, path, null, Set.of(), 0L, List.of()), null, catalog);
        }
    }

    private void installCatalogState(
            EditorState state, DiskVersion diskVersion, CatalogDocument document)
            throws IOException {
        if (catalogRecovery != null) {
            // A replacement is installed only after its load succeeded. Retire the prior draft
            // before closing so RecoveryService.close() cannot recreate discarded catalog edits.
            catalogRecovery.clear(catalogState);
            catalogRecovery.close();
        }
        catalogState = state;
        catalogDiskVersion = diskVersion;
        catalogDocument = document;
        catalogLoaded = true;
        catalogExternalConflictPending = false;
        catalogRecovery = catalogRecoveryFactory.create(
                ignored -> updateCatalogPresentation());
        if (catalogState.isDirty(codec)) catalogRecovery.edited(catalogState);
        if (catalogPane != null) {
            catalogPane.setCatalog(catalogDocument, projectDocuments.snapshotView());
        }
        updateCatalogPresentation();
    }

    private void openCatalog(
            Path openedPath,
            EditorPersistence.LoadedDocument loaded,
            CatalogDocument catalog) throws Exception {
        if (buildRunning) {
            showMessage(Alert.AlertType.INFORMATION, "Build in progress",
                    "Cancel or finish the current build before switching project catalogs.");
            return;
        }
        if (catalogLoaded && catalogState.sourcePath() != null
                && sameFile(catalogState.sourcePath(), openedPath)) {
            showCatalog();
            return;
        }
        Optional<Path> root = ProjectSettings.discover(
                openedPath.toAbsolutePath().normalize().getParent());
        if (root.isEmpty() || !sameFile(catalogPath(root.get()), openedPath)) {
            throw new IllegalArgumentException(
                    "Gameplay catalogs are project-level documents. Open the canonical file at "
                            + root.map(this::catalogPath)
                            .map(Path::toString).orElse("<project>/terrain-content/"
                                    + CATALOG_FILE));
        }
        ProjectTerrainDocumentRepository candidateRepository =
                new ProjectTerrainDocumentRepository(codec);
        candidateRepository.reload(root.get());
        PublishedCatalogState candidatePublished = readPublishedCatalogState(root.get());
        if (!confirmCatalogReplacement(
                "Opening another project catalog will replace the current catalog edits.")) {
            return;
        }
        commitProjectCatalogSwitch(root.get(), candidateRepository,
                new CatalogCandidate(loaded.state(), loaded.diskVersion(), catalog),
                candidatePublished);
        showCatalog();
    }

    private boolean confirmCatalogReplacement(String message) {
        if (!catalogLoaded || !catalogDirty()) return true;
        ButtonType save = new ButtonType("Save Current", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Discard Current", ButtonBar.ButtonData.NO);
        Alert prompt = new Alert(Alert.AlertType.CONFIRMATION,
                message, save, discard, ButtonType.CANCEL);
        prompt.setHeaderText("Current gameplay catalog has unsaved changes");
        ButtonType answer = prompt.showAndWait().orElse(ButtonType.CANCEL);
        if (answer == save) return saveCatalog();
        return answer == discard;
    }

    private Path catalogRecoveryProjectRoot(Path originalSource) throws IOException {
        if (originalSource != null && originalSource.getParent() != null
                && originalSource.getParent().getParent() != null) {
            Path candidate = originalSource.toAbsolutePath().normalize()
                    .getParent().getParent();
            if (ProjectSettings.isProjectRoot(candidate)
                    && catalogPath(candidate).equals(
                    originalSource.toAbsolutePath().normalize())) {
                return candidate;
            }
        }
        Optional<Path> current = projectSettings.projectRoot();
        if (originalSource == null && current.isPresent()) {
            Alert prompt = new Alert(Alert.AlertType.CONFIRMATION,
                    "This older catalog recovery has no project affiliation. Recover it into "
                            + current.get() + "?", ButtonType.OK, ButtonType.CANCEL);
            prompt.setHeaderText("Choose the catalog's project explicitly");
            if (prompt.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                return current.get();
            }
        }
        throw new IOException("The catalog recovery is not tied to a valid project root");
    }

    private void commitProjectCatalogSwitch(
            Path projectRoot,
            ProjectTerrainDocumentRepository candidateRepository,
            CatalogCandidate candidateCatalog,
            PublishedCatalogState candidatePublished) throws IOException {
        installCatalogState(candidateCatalog.state(), candidateCatalog.diskVersion(),
                candidateCatalog.document());
        projectDocuments.replaceWith(candidateRepository);
        projectSettings.setProjectRoot(projectRoot);
        for (EditorWorkspace workspace : workspaces) {
            workspace.setProjectContentRoot(projectDocuments.contentRoot());
        }
        rebuildSession(null, true);
        applyPublishedCatalogState(candidatePublished);
        updateCatalogPresentation();
    }

    private void showCatalog() {
        Optional<Path> root = projectSettings.projectRoot();
        if (root.isEmpty()) {
            chooseProjectRoot();
            root = projectSettings.projectRoot();
            if (root.isEmpty()) return;
        }
        if (catalogRecovery == null) {
            try { loadProjectCatalog(root.get()); }
            catch (Exception error) {
                showError("Could not open the gameplay catalog", error);
                return;
            }
        }
        if (catalogTab == null) {
            catalogPane = new GameplayCatalogPane(
                    catalogDocument,
                    projectDocuments.snapshotView(),
                    this::catalogChanged,
                    this::addCurrentLevelToCatalog,
                    this::saveCatalog,
                    this::publish,
                    this::publishAndSimulate);
            catalogTab = new Tab();
            catalogTab.setContent(catalogPane);
            catalogTab.setOnClosed(event -> {
                catalogTab = null;
                catalogPane = null;
                updateActionStates();
            });
            tabs.getTabs().add(catalogTab);
        }
        updateCatalogPresentation();
        tabs.getSelectionModel().select(catalogTab);
    }

    private void catalogChanged(CatalogDocument changed) {
        catalogDocument = changed;
        catalogState = catalogState.withDocument(changed);
        catalogRecovery.edited(catalogState);
        updateCatalogPresentation();
    }

    private void addCurrentLevelToCatalog() {
        EditorWorkspace workspace = lastActiveWorkspace;
        if (workspace == null || !workspaces.contains(workspace)
                || !(workspace.state().document() instanceof LevelDocument level)) {
            showMessage(Alert.AlertType.WARNING, "No current level",
                    "Select a level tab before using Add Current Level.");
            return;
        }
        if (workspace.dirty() || workspace.state().sourcePath() == null) {
            ButtonType saveNow = new ButtonType("Save Level", ButtonBar.ButtonData.OK_DONE);
            Alert prompt = new Alert(Alert.AlertType.CONFIRMATION,
                    "The current level must be saved inside terrain-content/levels first.",
                    saveNow, ButtonType.CANCEL);
            if (prompt.showAndWait().orElse(ButtonType.CANCEL) != saveNow
                    || !saveWorkspace(workspace)) return;
        }
        Path source = workspace.state().sourcePath();
        if (!isProjectContentPath(source)) {
            showMessage(Alert.AlertType.WARNING, "Standalone level",
                    "Save this level under terrain-content/levels before adding it to gameplay.");
            return;
        }
        try {
            Path root = projectSettings.projectRoot().orElseThrow();
            projectDocuments.reload(root);
            rebuildSession(null, true);
            var indexed = projectDocuments.snapshotView().contentIndex()
                    .levelsById().get(level.id());
            if (indexed == null || !sameFile(indexed.sourcePath(), source)) {
                throw new IllegalArgumentException(
                        "The saved level is not the unique project level named '"
                                + level.id() + "'.");
            }
            catalogPane.setCatalog(catalogDocument, projectDocuments.snapshotView());
            catalogPane.addLevelId(level.id());
        } catch (Exception error) {
            showError("Could not add the current level", error);
        }
    }

    private boolean saveWorkspace(EditorWorkspace workspace) {
        Path target = workspace.state().sourcePath();
        if (target == null) target = chooseSaveTarget(workspace);
        if (target == null) return false;
        try {
            if (!workspace.save(target, () -> chooseSaveTarget(workspace))) return false;
            rememberRecent(workspace.state().sourcePath());
            warnIfStandalone(workspace.state().sourcePath());
            return true;
        } catch (Exception error) {
            showError("Could not save " + target, error);
            return false;
        }
    }

    private boolean saveCatalog() {
        Optional<Path> root = projectSettings.projectRoot();
        if (root.isEmpty()) {
            chooseProjectRoot();
            root = projectSettings.projectRoot();
            if (root.isEmpty()) return false;
        }
        Path target = catalogPath(root.get());
        ExpectedDiskVersion expected = catalogDiskVersion == null
                ? ExpectedDiskVersion.absent()
                : ExpectedDiskVersion.exact(catalogDiskVersion);
        SaveIntent intent = catalogDiskVersion == null
                ? SaveIntent.CREATE_NEW : SaveIntent.SAVE_IF_UNCHANGED;
        while (true) {
            try {
                SaveResult result = persistence.save(catalogState, target, expected, intent);
                if (result instanceof SaveResult.Saved saved) {
                    catalogState = saved.state();
                    catalogDiskVersion = saved.diskVersion();
                    catalogExternalConflictPending = false;
                    catalogDocument = (CatalogDocument) catalogState.document();
                    try { catalogRecovery.clear(catalogState); }
                    catch (IOException recoveryFailure) {
                        showError("Catalog saved, but its recovery draft could not be cleared",
                                recoveryFailure);
                    }
                    try {
                        projectDocuments.reload(root.get());
                        rebuildSession(null, true);
                        if (catalogPane != null) {
                            catalogPane.setCatalog(
                                    catalogDocument, projectDocuments.snapshotView());
                        }
                    } catch (Exception refreshFailure) {
                        showError("Catalog saved, but project references could not be refreshed",
                                refreshFailure);
                    }
                    updateCatalogPresentation();
                    return true;
                }
                SaveResult.Conflict conflict = (SaveResult.Conflict) result;
                ButtonType overwrite = new ButtonType(
                        conflict.actual().isPresent() ? "Overwrite This Version" : "Create File",
                        ButtonBar.ButtonData.OK_DONE);
                String detail = conflict.actual().map(this::describeDiskVersion)
                        .orElse("The catalog file is currently missing.");
                Alert prompt = new Alert(Alert.AlertType.WARNING,
                        "The catalog changed on disk since the version shown to the editor.\n\n"
                                + detail + "\n\nConfirming applies only to this exact disk state.",
                        overwrite, ButtonType.CANCEL);
                prompt.setHeaderText("Gameplay catalog save conflict");
                if (prompt.showAndWait().orElse(ButtonType.CANCEL) != overwrite) return false;
                if (conflict.actual().isPresent()) {
                    expected = ExpectedDiskVersion.exact(conflict.actual().get());
                    intent = SaveIntent.OVERWRITE_CONFIRMED;
                } else {
                    expected = ExpectedDiskVersion.absent();
                    intent = SaveIntent.CREATE_NEW;
                }
            } catch (Exception error) {
                showError("Could not save the gameplay catalog", error);
                return false;
            }
        }
    }

    private boolean prepareCatalogClose() {
        if (!catalogLoaded || !catalogDirty()) return true;
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        Alert prompt = new Alert(Alert.AlertType.CONFIRMATION,
                "The gameplay catalog has unsaved changes.",
                save, discard, ButtonType.CANCEL);
        prompt.setHeaderText("Save gameplay catalog before closing?");
        ButtonType answer = prompt.showAndWait().orElse(ButtonType.CANCEL);
        if (answer == save) return saveCatalog();
        return answer == discard;
    }

    private boolean commitCatalogClose() {
        if (!catalogLoaded || catalogRecovery == null) return true;
        try { if (catalogRecovery != null) catalogRecovery.clear(catalogState); }
        catch (IOException failure) {
            showError("Could not clear the catalog recovery draft", failure);
            return false;
        }
        return true;
    }

    private void restoreCloseCheckpoints(List<EditorWorkspace> closing) {
        for (EditorWorkspace workspace : closing) {
            if (!workspace.dirty()) continue;
            try { workspace.checkpointRecovery(); }
            catch (IOException failure) {
                showError("Could not restore recovery for "
                        + workspace.state().document().id(), failure);
            }
        }
        if (catalogLoaded && catalogDirty() && catalogRecovery != null) {
            try { catalogRecovery.checkpoint(catalogState); }
            catch (IOException failure) {
                showError("Could not restore gameplay catalog recovery", failure);
            }
        }
    }

    private boolean catalogDirty() {
        return catalogState != null && catalogState.isDirty(codec);
    }

    private void checkCatalogExternalChange() {
        if (!catalogLoaded || catalogExternalConflictPending
                || catalogState.sourcePath() == null || catalogDiskVersion == null) return;
        final boolean changed;
        try {
            changed = persistence.externallyChanged(
                    catalogState.sourcePath(), catalogDiskVersion);
        } catch (IOException unavailable) {
            resolveCatalogExternalConflict();
            return;
        }
        if (changed) resolveCatalogExternalConflict();
    }

    private void resolveCatalogExternalConflict() {
        ButtonType reload = new ButtonType("Reload Catalog", ButtonBar.ButtonData.YES);
        ButtonType keep = new ButtonType("Keep Editor Version", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.WARNING,
                "The gameplay catalog changed outside the editor. Reload discards editor "
                        + "changes; keeping it requires an exact-version confirmation on Save.",
                reload, keep, ButtonType.CANCEL);
        alert.setHeaderText("External gameplay catalog change");
        ButtonType answer = alert.showAndWait().orElse(ButtonType.CANCEL);
        if (answer == reload) {
            try {
                EditorPersistence.LoadedDocument loaded = persistence.load(
                        catalogState.sourcePath());
                if (!(loaded.state().document() instanceof CatalogDocument catalog)) {
                    throw new IllegalArgumentException("Disk file is no longer a catalog");
                }
                installCatalogState(loaded.state(), loaded.diskVersion(), catalog);
            } catch (Exception error) {
                catalogExternalConflictPending = true;
                showError("Could not reload the external gameplay catalog", error);
                updateCatalogPresentation();
            }
        } else if (answer == keep) {
            catalogExternalConflictPending = true;
            updateCatalogPresentation();
        }
    }

    private void updateCatalogPresentation() {
        if (catalogTab != null) {
            catalogTab.setText((catalogDirty() ? "*" : "") + "Gameplay Catalog");
        }
        if (catalogPane != null) {
            String recovery = catalogRecovery == null ? "NOT_NEEDED"
                    : catalogRecovery.status().health().name();
            catalogPane.setCatalog(catalogDocument, projectDocuments.snapshotView());
            catalogPane.setPublishedIds(publishedCatalogIds);
            catalogPane.setWorkspaceStatus((catalogDirty() ? "Dirty" : "Saved")
                    + (catalogExternalConflictPending ? " · External conflict" : "")
                    + "  ·  Recovery: " + recovery + "  ·  "
                    + publishedCatalogDiagnostic);
            boolean recoveryFailed = catalogRecovery != null
                    && catalogRecovery.status().health() == RecoveryHealth.FAILED;
            catalogPane.setRecoveryFailure(recoveryFailed,
                    this::retryCatalogRecovery, this::openCatalogRecoveryFolder);
            catalogPane.setBuildRunning(buildRunning);
        }
        updateWorkspaceCatalogMembership();
        updateActionStates();
    }

    private void updateWorkspaceCatalogMembership() {
        TerrainDocumentRepository savedProject = projectDocuments.snapshotView();
        for (EditorWorkspace workspace : workspaces) {
            workspace.setCatalogMembership(representsRegisteredProjectLevel(
                    workspace.state(), catalogDocument, savedProject));
        }
    }

    /**
     * Catalog locations identify saved project levels, not arbitrary open documents sharing an ID.
     * Requiring filesystem identity prevents a standalone draft from claiming membership merely
     * because it shadows the registered level's document ID in the editor session.
     */
    static boolean representsRegisteredProjectLevel(
            EditorState state,
            CatalogDocument catalog,
            TerrainDocumentRepository savedProject) {
        if (state == null || catalog == null || savedProject == null
                || !(state.document() instanceof LevelDocument level)
                || state.sourcePath() == null) {
            return false;
        }
        boolean registered = catalog.entries().stream()
                .anyMatch(entry -> entry.kind() == CatalogEntry.Kind.JSON_LEVEL
                        && entry.location().equals(level.id()));
        if (!registered) return false;
        var saved = savedProject.contentIndex().levelsById().get(level.id());
        if (saved == null || !Files.isRegularFile(state.sourcePath())
                || !Files.isRegularFile(saved.sourcePath())) {
            return false;
        }
        try {
            return Files.isSameFile(state.sourcePath(), saved.sourcePath());
        } catch (IOException unavailable) {
            return false;
        }
    }

    private void retryCatalogRecovery() {
        if (catalogRecovery == null) return;
        try { catalogRecovery.checkpoint(catalogState); }
        catch (IOException failure) {
            showError("Could not write the gameplay catalog recovery", failure);
        }
        updateCatalogPresentation();
    }

    private void openCatalogRecoveryFolder() {
        Path directory = catalogRecoveryDirectory;
        try {
            Files.createDirectories(directory);
            if (!java.awt.Desktop.isDesktopSupported()) {
                throw new IOException("Desktop folder opening is unavailable");
            }
            java.awt.Desktop.getDesktop().open(directory.toFile());
        } catch (Exception failure) {
            showError("Could not open the catalog recovery folder", failure);
        }
    }

    private void refreshPublishedCatalogState(Path projectRoot) {
        applyPublishedCatalogState(readPublishedCatalogState(projectRoot));
    }

    private PublishedCatalogState readPublishedCatalogState(Path projectRoot) {
        Path runtime = projectRoot.resolve(RUNTIME_CATALOG);
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String diagnostic;
        if (!Files.isRegularFile(runtime)) {
            diagnostic = "No published runtime artifact";
        } else {
            try (Reader reader = Files.newBufferedReader(runtime, StandardCharsets.UTF_8)) {
                new PublishedGameplayCatalogLoader().load(reader).entries()
                        .forEach(provider -> ids.add(provider.stableId()));
                diagnostic = "Last published artifact is valid";
            } catch (Exception invalid) {
                diagnostic = "Published artifact is invalid: "
                        + invalid.getMessage();
            }
        }
        return new PublishedCatalogState(Set.copyOf(ids), diagnostic);
    }

    private void applyPublishedCatalogState(PublishedCatalogState state) {
        publishedCatalogIds.clear();
        publishedCatalogIds.addAll(state.ids());
        publishedCatalogDiagnostic = state.diagnostic();
        if (catalogPane != null) catalogPane.setPublishedIds(publishedCatalogIds);
        updateCatalogPresentation();
    }

    private Path catalogPath(Path projectRoot) {
        return projectRoot.toAbsolutePath().normalize()
                .resolve("terrain-content").resolve(CATALOG_FILE);
    }

    private static boolean sameFile(Path left, Path right) throws IOException {
        Path a = left.toAbsolutePath().normalize();
        Path b = right.toAbsolutePath().normalize();
        if (a.equals(b)) return true;
        return Files.exists(a) && Files.exists(b) && Files.isSameFile(a, b);
    }

    private String describeDiskVersion(DiskVersion version) {
        return "Current file: " + version.byteLength() + " bytes, SHA-256 "
                + version.rawSha256().substring(0, 12) + "…, modified "
                + version.modifiedTime();
    }

    private void publish() {
        startProjectWorkflow(null);
    }

    private void publishAndSimulate(String stableId) {
        startProjectWorkflow(stableId);
    }

    private void startProjectWorkflow(String selectedId) {
        if (buildRunning) {
            showMessage(Alert.AlertType.INFORMATION, "Build already running",
                    "Wait for or cancel the current project command.");
            return;
        }
        Path projectRoot = prepareProjectForBuild();
        if (projectRoot == null) return;
        String selected = selectedId == null ? null : selectedId.trim();
        if (selected != null && selected.isEmpty()) return;

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(selected == null
                ? "Publish Terrain Content" : "Publish & Simulate " + selected);
        dialog.setHeaderText("Validating and atomically publishing from " + projectRoot);
        TextArea output = new TextArea();
        output.setEditable(false);
        output.setPrefColumnCount(95);
        output.setPrefRowCount(28);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(28, 28);
        Label phase = new Label("Running publishTerrainContent…");
        dialog.getDialogPane().setContent(new VBox(8,
                new HBox(8, progress, phase), output));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.CLOSE);
        javafx.scene.Node cancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        javafx.scene.Node close = dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        close.setDisable(true);
        ProjectCommandRunner.CancellationToken cancellation =
                new ProjectCommandRunner.CancellationToken();
        cancel.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            cancellation.cancel();
            phase.setText("Cancelling…");
            cancel.setDisable(true);
        });
        dialog.setOnCloseRequest(event -> {
            if (buildRunning) {
                event.consume();
                cancellation.cancel();
                phase.setText("Cancelling…");
                cancel.setDisable(true);
            }
        });
        buildRunning = true;
        activeCommand = cancellation;
        updateCatalogPresentation();
        dialog.show();

        StringBuilder log = new StringBuilder();
        java.util.function.Consumer<String> sink = line -> {
            synchronized (log) { log.append(line).append('\n'); }
            Platform.runLater(() -> output.appendText(line + "\n"));
        };
        Thread worker = new Thread(() -> {
            WorkflowOutcome outcome;
            try {
                outcome = runProjectWorkflow(
                        projectRoot, selected, cancellation, sink, phase, log);
            } catch (Exception error) {
                sink.accept("ERROR: " + error.getMessage());
                boolean cancelled = cancellation.isCancelled();
                outcome = new WorkflowOutcome(false, cancelled, cancelled
                        ? "Workflow was cancelled"
                        : "Workflow failed: " + error.getMessage());
            }
            if (selected != null) {
                try {
                    Path runDirectory = editorRunDirectory(projectRoot, selected);
                    Files.createDirectories(runDirectory);
                    writeRunLog(runDirectory.resolve("run.log"), log);
                } catch (IOException logFailure) {
                    sink.accept("Could not write run.log: " + logFailure.getMessage());
                }
            }
            WorkflowOutcome completed = outcome;
            Platform.runLater(() -> {
                progress.setProgress(1);
                dialog.setHeaderText(completed.summary());
                phase.setText(completed.cancelled() ? "Cancelled" : "Finished");
                cancel.setDisable(true);
                close.setDisable(false);
                if (activeCommand == cancellation) {
                    activeCommand = null;
                    activeBuildThread = null;
                    buildRunning = false;
                    refreshPublishedCatalogState(projectRoot);
                    updateCatalogPresentation();
                }
            });
        }, "terrain-editor-project-command");
        worker.setDaemon(true);
        activeBuildThread = worker;
        worker.start();
    }

    private Path prepareProjectForBuild() {
        Optional<Path> root = projectSettings.projectRoot();
        if (root.isEmpty()) {
            chooseProjectRoot();
            root = projectSettings.projectRoot();
            if (root.isEmpty()) return null;
        }
        for (EditorWorkspace workspace : new ArrayList<>(workspaces)) {
            if (!workspace.resolveExternalChangeBeforeBuild()) {
                showMessage(Alert.AlertType.WARNING, "Build cancelled",
                        "Resolve the external file change for '"
                                + workspace.state().document().id()
                                + "' before publishing.");
                return null;
            }
        }
        checkCatalogExternalChange();
        List<String> dirty = new ArrayList<>();
        workspaces.stream().filter(value -> value.dirty()
                        || value.hasExternalConflictPending())
                .map(value -> value.state().document().id()).forEach(dirty::add);
        if (catalogDirty() || catalogExternalConflictPending) {
            dirty.add("Gameplay Catalog");
        }
        if (!dirty.isEmpty()) {
            ButtonType saveAll = new ButtonType(
                    "Save All and Continue", ButtonBar.ButtonData.OK_DONE);
            Alert prompt = new Alert(Alert.AlertType.CONFIRMATION,
                    "The build consumes saved project files only:\n\n"
                            + String.join("\n", dirty), saveAll, ButtonType.CANCEL);
            prompt.setHeaderText("Save all changes before publishing?");
            if (prompt.showAndWait().orElse(ButtonType.CANCEL) != saveAll) return null;
        }
        for (EditorWorkspace workspace : new ArrayList<>(workspaces)) {
            if ((workspace.dirty() || workspace.hasExternalConflictPending())
                    && !saveWorkspace(workspace)) return null;
        }
        try {
            projectDocuments.reload(root.get());
            rebuildSession(null, true);
            if (catalogPane != null) {
                catalogPane.setCatalog(catalogDocument, projectDocuments.snapshotView());
            }
        } catch (Exception error) {
            showError("Could not reload saved terrain before publishing", error);
            return null;
        }
        ValidationResult validation = validateCatalogForBuild();
        if (!validation.isValid()) {
            StringBuilder message = new StringBuilder();
            validation.problems().stream()
                    .filter(problem -> problem.severity()
                            == ValidationProblem.Severity.ERROR)
                    .limit(12)
                    .forEach(problem -> message.append(problem).append('\n'));
            showMessage(Alert.AlertType.ERROR, "Gameplay catalog is invalid",
                    message.toString());
            showCatalog();
            return null;
        }
        if (!saveCatalog()) return null;
        return root.get();
    }

    private ValidationResult validateCatalogForBuild() {
        ArrayList<ValidationProblem> problems = new ArrayList<>();
        problems.addAll(new TerrainValidator().validate(catalogDocument).problems());
        problems.addAll(GameplayCatalogPolicy.validate(
                catalogDocument, projectDocuments.snapshotView()).problems());
        return new ValidationResult(problems);
    }

    private WorkflowOutcome runProjectWorkflow(
            Path projectRoot,
            String selectedId,
            ProjectCommandRunner.CancellationToken cancellation,
            java.util.function.Consumer<String> output,
            Label phase,
            StringBuilder log) throws Exception {
        ProjectCommandRunner runner = new ProjectCommandRunner();
        ProjectCommandRunner.CommandResult publish = runner.run(projectRoot,
                List.of("publishTerrainContent"), output, cancellation);
        if (!publish.successful()) {
            return new WorkflowOutcome(false, publish.cancelled(), publish.cancelled()
                    ? "Publishing was cancelled; no simulation was launched"
                    : "Publishing failed; the previous runtime artifact was preserved");
        }

        Path runtime = projectRoot.resolve(RUNTIME_CATALOG).toAbsolutePath().normalize();
        LinkedHashSet<String> publishedIds = strictPublishedIds(runtime);
        if (selectedId != null && !publishedIds.contains(selectedId)) {
            throw new IllegalArgumentException(
                    "Published runtime artifact does not contain enabled ID '"
                            + selectedId + "'");
        }
        if (selectedId == null) {
            return new WorkflowOutcome(true, false,
                    "Terrain content published and strictly reloaded successfully");
        }
        if (cancellation.isCancelled()) {
            return new WorkflowOutcome(false, true,
                    "Publishing completed, but simulation was cancelled");
        }

        Path runDirectory = editorRunDirectory(projectRoot, selectedId);
        Files.createDirectories(runDirectory);
        Path trace = runDirectory.resolve("trace.ndjson");
        Path visual = runDirectory.resolve("visual.svg");
        Path runLog = runDirectory.resolve("run.log");
        Path traceStaged = runDirectory.resolve(
                ".trace-" + UUID.randomUUID() + ".ndjson.tmp");
        Path visualStaged = runDirectory.resolve(
                ".visual-" + UUID.randomUUID() + ".svg.tmp");
        // Once a new run starts, prior artifacts must not masquerade as its current result.
        Files.deleteIfExists(trace);
        Files.deleteIfExists(visual);
        try {
            Platform.runLater(() -> phase.setText("Preparing the desktop simulator…"));
            ProjectCommandRunner.CommandResult install = runner.run(projectRoot,
                List.of(":simulator:installDist"), output, cancellation);
            if (!install.successful()) {
                writeRunLog(runLog, log);
                return new WorkflowOutcome(false, install.cancelled(), install.cancelled()
                    ? "Published successfully; simulator preparation was cancelled"
                    : "Published successfully; simulator preparation failed");
            }

            Platform.runLater(() -> phase.setText(
                "Running exact catalog entry '" + selectedId + "' at ordinal 0…"));
            Path simulator = projectRoot.resolve(
                "simulator/build/install/simulator/bin/simulator").toAbsolutePath();
            ProjectCommandRunner.CommandResult simulation = runner.runCommand(projectRoot,
                List.of(simulator.toString(), "run", "published_catalog_level",
                        "--catalog", runtime.toString(),
                        "--catalog-entry", selectedId,
                        "--ticks", "120", "--trace", "summary",
                        "--out", traceStaged.toString()), output, cancellation);
            if (!simulation.successful()) {
                writeRunLog(runLog, log);
                return new WorkflowOutcome(false, simulation.cancelled(), simulation.cancelled()
                    ? "Published successfully; physics simulation was cancelled"
                    : "Published successfully; physics simulation failed");
            }
            promoteGeneratedArtifact(traceStaged, trace);

            Platform.runLater(() -> phase.setText("Generating visual.svg…"));
            ProjectCommandRunner.CommandResult svg = runner.runCommand(projectRoot,
                List.of("python3", projectRoot.resolve("tools/visualize_simulation.py").toString(),
                        trace.toString(), "--output", visualStaged.toString(),
                        "--focus-traveled", "--samples"), output, cancellation);
            writeRunLog(runLog, log);
            if (!svg.successful()) {
                return new WorkflowOutcome(true, svg.cancelled(),
                    "Physics simulation succeeded; SVG generation "
                            + (svg.cancelled() ? "was cancelled" : "failed")
                            + " (trace.ndjson and run.log were kept)");
            }
            promoteGeneratedArtifact(visualStaged, visual);
            return new WorkflowOutcome(true, false,
                    "Published, simulated, and generated " + visual);
        } finally {
            Files.deleteIfExists(traceStaged);
            Files.deleteIfExists(visualStaged);
        }
    }

    private LinkedHashSet<String> strictPublishedIds(Path runtime) throws Exception {
        try (Reader reader = Files.newBufferedReader(runtime, StandardCharsets.UTF_8)) {
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            new PublishedGameplayCatalogLoader().load(reader).entries()
                    .forEach(provider -> ids.add(provider.stableId()));
            return ids;
        }
    }

    private static void writeRunLog(Path path, StringBuilder log) throws IOException {
        String content;
        synchronized (log) { content = log.toString(); }
        new AtomicFileStore().writeUtf8(path, content);
    }

    private static void promoteGeneratedArtifact(Path staged, Path target)
            throws IOException {
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            throw new IOException("Cannot atomically publish generated artifact " + target,
                    unsupported);
        }
    }

    @Override public void stop() {
        ProjectCommandRunner.CancellationToken running = activeCommand;
        if (running != null) running.cancel();
        Thread worker = activeBuildThread;
        if (worker != null && worker != Thread.currentThread()) worker.interrupt();
        for (EditorWorkspace workspace : new ArrayList<>(workspaces)) {
            workspace.close();
        }
        if (catalogRecovery != null) catalogRecovery.close();
    }

    private void showMessage(Alert.AlertType type, String header, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    private record WorkflowOutcome(boolean published, boolean cancelled, String summary) {}

    private record CatalogCandidate(
            EditorState state, DiskVersion diskVersion, CatalogDocument document) { }

    private record PublishedCatalogState(Set<String> ids, String diagnostic) {
        private PublishedCatalogState {
            ids = Set.copyOf(ids);
        }
    }

    private void showError(String message, Exception error) {
        new Alert(Alert.AlertType.ERROR, message + "\n\n" + error.getMessage(), ButtonType.OK).showAndWait();
    }

    @Override public StructureDocument findStructure(String id) {
        return sessionIndex.references().findStructure(id);
    }

    @Override public LevelDocument findLevel(String id) {
        return sessionIndex.references().findLevel(id);
    }
}
