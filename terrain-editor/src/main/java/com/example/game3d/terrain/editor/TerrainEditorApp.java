package com.example.game3d.terrain.editor;

import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.terrain.editor.persistence.EditorPersistence;
import com.example.game3d.terrain.editor.persistence.ProjectSettings;
import com.example.game3d.terrain.editor.persistence.ProjectTerrainDocumentRepository;
import com.example.game3d.terrain.editor.persistence.RecoveryService;
import com.example.game3d.terrain.editor.persistence.UserStateDirectory;
import com.example.game3d.terrain.editor.publish.GradleTerrainPublisher;
import com.example.game3d.terrain.editor.importing.BuiltinProviderImporter;
import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.editor.ui.EditorWorkspace;
import com.example.game3d.terrain.editor.ui.TerrainPreviewPane;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Collections;

public final class TerrainEditorApp extends Application implements TerrainDocumentRepository {
    private final List<EditorWorkspace> workspaces = new ArrayList<>();
    private final List<Path> recent = new ArrayList<>();
    private final TabPane tabs = new TabPane();
    private final TerrainJsonCodec codec = new TerrainJsonCodec();
    private final EditorPersistence persistence = new EditorPersistence(codec);
    private final ProjectSettings projectSettings = new ProjectSettings();
    private final ProjectTerrainDocumentRepository projectDocuments =
            new ProjectTerrainDocumentRepository(codec);
    private final BuiltinProviderImporter builtInImporter = new BuiltinProviderImporter();
    private Stage stage;

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage stage) {
        this.stage = stage;
        projectSettings.projectRoot().ifPresent(root -> reloadProjectContent(root, false));
        BorderPane root = new BorderPane(tabs);
        root.setTop(menuBar());
        tabs.getTabs().add(welcomeTab());
        stage.setScene(new Scene(root, 1420, 860, true));
        stage.setTitle("Game3D Terrain Editor");
        stage.setOnCloseRequest(event -> {
            for (EditorWorkspace workspace : new ArrayList<>(workspaces)) {
                if (!workspace.confirmClose(this::chooseSaveTarget)) { event.consume(); return; }
            }
            workspaces.forEach(EditorWorkspace::close);
        });
        stage.focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (focused) active().ifPresent(EditorWorkspace::checkExternalChange);
            else workspaces.forEach(EditorWorkspace::focusLost);
        });
        tabs.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldTab, newTab) -> active().ifPresent(EditorWorkspace::checkExternalChange));
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
        MenuItem save = new MenuItem("Save");
        save.setOnAction(event -> save(false));
        MenuItem saveAs = new MenuItem("Save As…");
        saveAs.setOnAction(event -> save(true));
        MenuItem javaProvider = new MenuItem("Preview / Import Java Provider…");
        javaProvider.setOnAction(event -> previewJavaProvider());
        file.getItems().addAll(structure, level, open, javaProvider, save, saveAs);
        Menu edit = new Menu("Edit");
        MenuItem undo = new MenuItem("Undo"); undo.setOnAction(event -> active().ifPresent(EditorWorkspace::undo));
        MenuItem redo = new MenuItem("Redo"); redo.setOnAction(event -> active().ifPresent(EditorWorkspace::redo));
        edit.getItems().addAll(undo, redo);
        Menu build = new Menu("Build");
        MenuItem projectRoot = new MenuItem("Choose Project Root…");
        projectRoot.setOnAction(event -> chooseProjectRoot());
        MenuItem reloadProject = new MenuItem("Reload Project Content");
        reloadProject.setOnAction(event -> reloadProjectContent());
        MenuItem publish = new MenuItem("Publish Terrain Content");
        publish.setOnAction(event -> publish());
        build.getItems().addAll(projectRoot, reloadProject, publish);
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
        TerrainPreviewPane preview = new TerrainPreviewPane();
        preview.setPrefSize(980, 640);
        preview.show(imported.originalSnapshot(), Collections.emptyMap(),
                Collections.emptyMap(), ignored -> {});
        ButtonType importStructure = new ButtonType(
                "Import as JSON Structure", ButtonBar.ButtonData.APPLY);
        ButtonType importLevel = new ButtonType(
                "Import as Inline JSON Level", ButtonBar.ButtonData.YES);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Java Provider Preview");
        dialog.setHeaderText(provider.get() + " · ordinal " + ordinal
                + " · " + imported.originalSnapshot().segments.size() + " tiles");
        dialog.getDialogPane().setContent(preview);
        dialog.getDialogPane().getButtonTypes().addAll(
                importStructure, importLevel, ButtonType.CLOSE);
        Optional<ButtonType> action = dialog.showAndWait();
        if (action.orElse(ButtonType.CLOSE) == importStructure) {
            askId(imported.structure().id()).ifPresent(id -> addWorkspace(
                    EditorState.unsaved(imported.structureWithId(id))));
        } else if (action.orElse(ButtonType.CLOSE) == importLevel) {
            askId("imported.level." + provider.get()).ifPresent(id -> addWorkspace(
                    EditorState.unsaved(builtInImporter.importInlineLevel(
                            provider.get(), ordinal, id))));
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
        Path directory = UserStateDirectory.terrainEditor().resolve("recovery");
        try {
            List<RecoveryService.RecoveryDraft> drafts = RecoveryService.list(directory);
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
                    EditorWorkspace workspace = addWorkspace(restored.state());
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
        if (recent.isEmpty()) { new Alert(Alert.AlertType.INFORMATION, "No recent documents yet.").showAndWait(); return; }
        ChoiceDialog<Path> dialog = new ChoiceDialog<>(recent.get(0), recent);
        dialog.setTitle("Recent Documents"); dialog.showAndWait().ifPresent(this::open);
    }

    private void open(Path path) {
        try {
            addWorkspace(persistence.load(path).state());
            recent.remove(path); recent.add(0, path);
        } catch (Exception error) {
            showError("Could not open " + path, error);
        }
    }

    private void save(boolean forceChoose) {
        active().ifPresent(workspace -> {
            Path target = forceChoose ? null : workspace.state().sourcePath();
            if (target == null) {
                File file = jsonChooser("Save Terrain Document").showSaveDialog(stage);
                if (file == null) return;
                target = file.toPath();
            }
            try {
                if (workspace.save(target, this::chooseSaveTarget)) {
                    Path saved = workspace.state().sourcePath();
                    recent.remove(saved); recent.add(0, saved);
                }
            }
            catch (Exception error) { showError("Could not save " + target, error); }
        });
    }

    private EditorWorkspace addWorkspace(EditorState state) {
        EditorWorkspace workspace = new EditorWorkspace(state, this);
        workspaces.add(workspace);
        Tab tab = new Tab();
        tab.setContent(workspace);
        Runnable title = () -> tab.setText((workspace.dirty() ? "*" : "") + workspace.state().document().id());
        workspace.setTitleChanged(title); title.run();
        tab.setOnCloseRequest(event -> { if (!workspace.confirmClose(this::chooseSaveTarget)) event.consume(); });
        tab.setOnClosed(event -> { workspaces.remove(workspace); workspace.close(); });
        tabs.getTabs().add(tab); tabs.getSelectionModel().select(tab);
        return workspace;
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

    private Path chooseSaveTarget() {
        File file = jsonChooser("Save Terrain Document").showSaveDialog(stage);
        return file == null ? null : file.toPath();
    }

    private void chooseProjectRoot() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose Game3D Project Root");
        projectSettings.projectRoot().filter(java.nio.file.Files::isDirectory)
                .ifPresent(path -> chooser.setInitialDirectory(path.toFile()));
        File selected = chooser.showDialog(stage);
        if (selected == null) return;
        try {
            projectSettings.setProjectRoot(selected.toPath());
            reloadProjectContent(selected.toPath(), true);
        }
        catch (IllegalArgumentException invalid) {
            new Alert(Alert.AlertType.ERROR, invalid.getMessage()).showAndWait();
        }
    }

    private void reloadProjectContent() {
        Optional<Path> root = projectSettings.projectRoot();
        if (root.isEmpty()) {
            chooseProjectRoot();
            return;
        }
        reloadProjectContent(root.get(), true);
    }

    private void reloadProjectContent(Path root, boolean reportFailure) {
        try {
            projectDocuments.reload(root);
            for (EditorWorkspace workspace : workspaces) {
                workspace.recompile();
            }
        } catch (Exception error) {
            if (reportFailure) {
                showError("Could not reload saved project terrain content", error);
            }
        }
    }

    private void publish() {
        List<String> unsaved = workspaces.stream().filter(EditorWorkspace::dirty)
                .map(value -> value.state().document().id()).toList();
        if (!unsaved.isEmpty()) {
            new Alert(Alert.AlertType.WARNING,
                    "Publish requires every open draft to be saved first:\n\n"
                            + String.join("\n", unsaved)).showAndWait();
            return;
        }
        Optional<Path> root = projectSettings.projectRoot();
        if (root.isEmpty()) {
            chooseProjectRoot();
            root = projectSettings.projectRoot();
            if (root.isEmpty()) return;
        }
        Path projectRoot = root.get();
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Publish Terrain Content");
        dialog.setHeaderText("Validating and atomically publishing from " + projectRoot);
        TextArea output = new TextArea();
        output.setEditable(false);
        output.setPrefColumnCount(95);
        output.setPrefRowCount(28);
        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(28, 28);
        dialog.getDialogPane().setContent(new VBox(8, new HBox(8, progress,
                new Label("Running publishTerrainContent…")), output));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        javafx.scene.Node close = dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        close.setDisable(true);
        dialog.show();
        Thread worker = new Thread(() -> {
            int exit;
            try {
                exit = new GradleTerrainPublisher().publish(projectRoot,
                        line -> Platform.runLater(() -> output.appendText(line + "\n")));
            } catch (Exception error) {
                exit = -1;
                Platform.runLater(() -> output.appendText("\nERROR: " + error.getMessage() + "\n"));
            }
            int completedExit = exit;
            Platform.runLater(() -> {
                progress.setProgress(1);
                dialog.setHeaderText(completedExit == 0
                        ? "Terrain content published successfully"
                        : "Publish failed (exit " + completedExit + "); previous runtime asset was preserved");
                close.setDisable(false);
            });
        }, "terrain-content-publisher");
        worker.setDaemon(true);
        worker.start();
    }

    private void showError(String message, Exception error) {
        new Alert(Alert.AlertType.ERROR, message + "\n\n" + error.getMessage(), ButtonType.OK).showAndWait();
    }

    @Override public StructureDocument findStructure(String id) {
        for (EditorWorkspace workspace : workspaces)
            if (workspace.state().document() instanceof StructureDocument value && value.id().equals(id)) return value;
        return projectDocuments.findStructure(id);
    }

    @Override public LevelDocument findLevel(String id) {
        for (EditorWorkspace workspace : workspaces)
            if (workspace.state().document() instanceof LevelDocument value && value.id().equals(id)) return value;
        return projectDocuments.findLevel(id);
    }
}
