package com.example.game3d.terrain.editor.ui;

import com.example.game3d.terrain.io.catalog.CatalogDocumentEdits;
import com.example.game3d.terrain.io.catalog.GameplayCatalogPolicy;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.CatalogEntry;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import com.example.game3d.terrain.io.resolve.TerrainProjectContentIndex;
import com.example.game3d.terrain.io.validation.ValidationProblem;
import com.example.game3d.terrain.io.validation.ValidationResult;
import com.example.game3d.terrain.io.validation.TerrainValidator;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Explicit editor for the one ordered gameplay source catalog. */
public final class GameplayCatalogPane extends BorderPane {
    private final TableView<Row> table = new TableView<>();
    private final Label status = new Label();
    private final Button retryRecovery = new Button("Retry Recovery");
    private final Button openRecoveryFolder = new Button("Open Recovery Folder");
    private final List<Button> actionButtons = new ArrayList<>();
    private final List<Button> publishButtons = new ArrayList<>();
    private final Consumer<CatalogDocument> changed;
    private final Runnable addCurrentLevel;
    private final Runnable save;
    private final Runnable publish;
    private final Consumer<String> publishAndSimulate;
    private CatalogDocument catalog;
    private TerrainDocumentRepository repository;
    private TerrainProjectContentIndex contentIndex;
    private Set<String> publishedIds = Set.of();
    private String validationStatus = "";
    private String workspaceStatus = "";
    private boolean sourceValid;
    private boolean buildRunning;

    public GameplayCatalogPane(
            CatalogDocument catalog,
            TerrainDocumentRepository repository,
            Consumer<CatalogDocument> changed,
            Runnable addCurrentLevel,
            Runnable save,
            Runnable publish,
            Consumer<String> publishAndSimulate) {
        this.catalog = require(catalog, "catalog");
        this.repository = require(repository, "repository");
        this.contentIndex = repository.contentIndex();
        this.changed = require(changed, "changed");
        this.addCurrentLevel = require(addCurrentLevel, "addCurrentLevel");
        this.save = require(save, "save");
        this.publish = require(publish, "publish");
        this.publishAndSimulate = require(publishAndSimulate, "publishAndSimulate");
        setPadding(new Insets(10));
        setTop(header());
        setCenter(table);
        setBottom(footer());
        configureTable();
        refresh(null);
    }

    public CatalogDocument catalog() { return catalog; }

    public void setCatalog(CatalogDocument value, TerrainDocumentRepository references) {
        String selected = selectedId();
        catalog = require(value, "catalog");
        repository = require(references, "references");
        contentIndex = references.contentIndex();
        refresh(selected);
    }

    public void setPublishedIds(Set<String> ids) {
        publishedIds = ids == null ? Set.of() : Set.copyOf(ids);
        refresh(selectedId());
    }

    public void setWorkspaceStatus(String value) {
        workspaceStatus = value == null ? "" : value;
        updateStatusText();
    }

    public void setBuildRunning(boolean running) {
        buildRunning = running;
        updateButtonStates();
    }

    public void setRecoveryFailure(
            boolean failed, Runnable retry, Runnable openFolder) {
        retryRecovery.setVisible(failed);
        retryRecovery.setManaged(failed);
        openRecoveryFolder.setVisible(failed);
        openRecoveryFolder.setManaged(failed);
        retryRecovery.setOnAction(event -> retry.run());
        openRecoveryFolder.setOnAction(event -> openFolder.run());
    }

    public boolean sourceValid() {
        return sourceValid;
    }

    public String selectedId() {
        Row row = table.getSelectionModel().getSelectedItem();
        return row == null ? null : row.entry().id();
    }

    private VBox header() {
        Label title = new Label("Gameplay Catalog");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        Label explanation = new Label(
                "Built-in Java providers are locked. Custom JSON levels are explicit, "
                        + "ordered gameplay entries.");
        explanation.setWrapText(true);
        VBox box = new VBox(5, title, explanation);
        box.setPadding(new Insets(0, 0, 9, 0));
        return box;
    }

    private VBox footer() {
        Button addCurrent = button("Add Current Level", event -> addCurrentLevel.run());
        Button addSaved = button("Add Saved Level…", event -> addSavedLevel());
        Button edit = button("Edit…", event -> editSelected());
        Button toggle = button("Enable / Disable", event -> toggleSelected());
        Button remove = button("Remove", event -> removeSelected());
        Button up = button("Move Up", event -> moveSelected(-1));
        Button down = button("Move Down", event -> moveSelected(1));
        actionButtons.addAll(List.of(
                addCurrent, addSaved, edit, toggle, remove, up, down));
        FlowPane edits = new FlowPane(7, 7,
                addCurrent, addSaved, edit, toggle, remove, up, down);

        Button saveButton = button("Save Catalog", event -> save.run());
        Button publishButton = button("Publish", event -> publish.run());
        Button runButton = button("Publish & Simulate Selected", event -> {
            String selected = selectedId();
            if (selected == null) {
                showError("Select an enabled catalog entry first.");
                return;
            }
            CatalogEntry entry = entry(selected);
            if (entry == null || !entry.enabled()) {
                showError("The selected catalog entry is disabled.");
                return;
            }
            publishAndSimulate.accept(selected);
        });
        actionButtons.addAll(List.of(saveButton, publishButton, runButton));
        publishButtons.addAll(List.of(publishButton, runButton));
        HBox build = new HBox(8, saveButton, publishButton, runButton);
        retryRecovery.setVisible(false);
        retryRecovery.setManaged(false);
        openRecoveryFolder.setVisible(false);
        openRecoveryFolder.setManaged(false);
        FlowPane recoveryActions = new FlowPane(
                7, 7, retryRecovery, openRecoveryFolder);
        VBox box = new VBox(8, edits, build, status, recoveryActions);
        box.setPadding(new Insets(9, 0, 0, 0));
        return box;
    }

    private void configureTable() {
        TableColumn<Row, String> enabled = column("Enabled", 78,
                row -> row.entry().enabled() ? "Yes" : "No");
        TableColumn<Row, String> id = column("Stable gameplay ID", 210,
                row -> row.entry().id());
        TableColumn<Row, String> kind = column("Kind", 120,
                row -> row.entry().kind().name());
        TableColumn<Row, String> level = column("Level document", 210,
                row -> row.entry().location());
        TableColumn<Row, String> source = column("Source", 330,
                row -> row.source() == null ? "—" : row.source().toString());
        TableColumn<Row, String> state = column("Status", 280, Row::status);
        table.getColumns().setAll(enabled, id, kind, level, source, state);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No catalog entries"));
        VBox.setVgrow(table, Priority.ALWAYS);
    }

    private void addSavedLevel() {
        List<String> levels = new ArrayList<>(contentIndex.levelsById().keySet());
        if (levels.isEmpty()) {
            showError("Save a project level before adding it to gameplay.");
            return;
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(levels.get(0), levels);
        dialog.setTitle("Add Saved Level");
        dialog.setHeaderText("Choose a saved level document");
        dialog.showAndWait().ifPresent(this::addLevelId);
    }

    public void addLevelId(String levelId) {
        try {
            replace(CatalogDocumentEdits.addJsonLevel(
                    catalog, levelId, levelId, true), levelId);
        } catch (IllegalArgumentException invalid) {
            showError(invalid.getMessage());
        }
    }

    private void editSelected() {
        CatalogEntry selected = selectedEntry();
        if (!editable(selected)) return;
        TextInputDialog idDialog = new TextInputDialog(selected.id());
        idDialog.setTitle("Edit Gameplay Entry");
        idDialog.setHeaderText("Stable gameplay ID");
        Optional<String> newId = idDialog.showAndWait().map(String::trim)
                .filter(value -> !value.isEmpty());
        if (newId.isEmpty()) return;
        List<String> levels = new ArrayList<>(contentIndex.levelsById().keySet());
        if (!levels.contains(selected.location())) levels.add(0, selected.location());
        ChoiceDialog<String> levelDialog = new ChoiceDialog<>(selected.location(), levels);
        levelDialog.setTitle("Edit Gameplay Entry");
        levelDialog.setHeaderText("Saved level document");
        Optional<String> location = levelDialog.showAndWait();
        if (location.isEmpty()) return;
        if (!selected.id().equals(newId.get()) && publishedIds.contains(selected.id())) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                    "This ID exists in the current published artifact. Changing it changes "
                            + "the stable gameplay identity. Continue?",
                    ButtonType.YES, ButtonType.CANCEL);
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.YES) return;
        }
        try {
            replace(CatalogDocumentEdits.replaceJsonLevel(catalog, selected.id(),
                    newId.get(), location.get(), selected.enabled()), newId.get());
        } catch (IllegalArgumentException invalid) {
            showError(invalid.getMessage());
        }
    }

    private void toggleSelected() {
        CatalogEntry selected = selectedEntry();
        if (!editable(selected)) return;
        replace(CatalogDocumentEdits.setEnabled(
                catalog, selected.id(), !selected.enabled()), selected.id());
    }

    private void removeSelected() {
        CatalogEntry selected = selectedEntry();
        if (!editable(selected)) return;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove gameplay entry '" + selected.id() + "'?",
                ButtonType.YES, ButtonType.CANCEL);
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.YES) return;
        replace(CatalogDocumentEdits.remove(catalog, selected.id()), null);
    }

    private void moveSelected(int delta) {
        CatalogEntry selected = selectedEntry();
        if (!editable(selected)) return;
        List<CatalogEntry> custom = CatalogDocumentEdits.customEntries(catalog);
        int index = -1;
        for (int i = 0; i < custom.size(); i++) {
            if (custom.get(i).id().equals(selected.id())) index = i;
        }
        int target = index + delta;
        if (index < 0 || target < 0 || target >= custom.size()) return;
        ArrayList<String> order = new ArrayList<>();
        for (CatalogEntry entry : custom) order.add(entry.id());
        java.util.Collections.swap(order, index, target);
        replace(CatalogDocumentEdits.reorderCustom(catalog, order), selected.id());
    }

    private boolean editable(CatalogEntry entry) {
        if (entry == null) {
            showError("Select a custom catalog entry first.");
            return false;
        }
        if (GameplayCatalogPolicy.isRequiredBuiltinId(entry.id())) {
            showError("Built-in gameplay entries are locked.");
            return false;
        }
        return true;
    }

    private void replace(CatalogDocument value, String selectedId) {
        catalog = value;
        changed.accept(value);
        refresh(selectedId);
    }

    private void refresh(String selectedId) {
        ArrayList<ValidationProblem> combined = new ArrayList<>();
        combined.addAll(new TerrainValidator().validate(catalog).problems());
        combined.addAll(GameplayCatalogPolicy.validate(catalog, repository).problems());
        ValidationResult validation = new ValidationResult(combined);
        Map<Integer, List<ValidationProblem>> byIndex = new LinkedHashMap<>();
        for (ValidationProblem problem : validation.problems()) {
            int index = entryIndex(problem.path());
            if (index >= 0) byIndex.computeIfAbsent(index, ignored -> new ArrayList<>())
                    .add(problem);
        }
        ArrayList<Row> rows = new ArrayList<>();
        for (int i = 0; i < catalog.entries().size(); i++) {
            CatalogEntry entry = catalog.entries().get(i);
            TerrainProjectContentIndex.Entry<?> sourceEntry =
                    contentIndex.levelsById().get(entry.location());
            String rowStatus = byIndex.containsKey(i)
                    ? byIndex.get(i).get(0).severity() + ": "
                    + byIndex.get(i).get(0).message()
                    : publishedIds.contains(entry.id())
                    ? "Present in last published artifact" : "Valid source";
            rows.add(new Row(entry,
                    sourceEntry == null ? null : sourceEntry.sourcePath(), rowStatus));
        }
        table.setItems(FXCollections.observableArrayList(rows));
        if (selectedId != null) {
            for (Row row : rows) if (row.entry().id().equals(selectedId)) {
                table.getSelectionModel().select(row);
                break;
            }
        }
        long errors = validation.problems().stream()
                .filter(problem -> problem.severity() == ValidationProblem.Severity.ERROR)
                .count();
        sourceValid = errors == 0;
        validationStatus = errors == 0 ? "Catalog source is valid"
                : errors + " catalog error(s); publishing is blocked";
        updateStatusText();
        updateButtonStates();
    }

    private void updateStatusText() {
        status.setText(workspaceStatus.isEmpty() ? validationStatus
                : validationStatus + "  ·  " + workspaceStatus);
    }

    private void updateButtonStates() {
        for (Button button : actionButtons) button.setDisable(buildRunning);
        if (!buildRunning && !sourceValid) {
            for (Button button : publishButtons) button.setDisable(true);
        }
    }

    private CatalogEntry selectedEntry() {
        Row row = table.getSelectionModel().getSelectedItem();
        return row == null ? null : row.entry();
    }

    private CatalogEntry entry(String id) {
        for (CatalogEntry entry : catalog.entries()) if (entry.id().equals(id)) return entry;
        return null;
    }

    private static int entryIndex(String path) {
        String prefix = "$.entries[";
        if (path == null || !path.startsWith(prefix)) return -1;
        int end = path.indexOf(']', prefix.length());
        if (end < 0) return -1;
        try { return Integer.parseInt(path.substring(prefix.length(), end)); }
        catch (NumberFormatException invalid) { return -1; }
    }

    private static <T> T require(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " == null");
        return value;
    }

    private static Button button(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button button = new Button(text);
        button.setOnAction(action);
        return button;
    }

    private static TableColumn<Row, String> column(
            String title, double width, java.util.function.Function<Row, String> value) {
        TableColumn<Row, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    private static void showError(String message) {
        new Alert(Alert.AlertType.ERROR, message).showAndWait();
    }

    private record Row(CatalogEntry entry, Path source, String status) {}
}
