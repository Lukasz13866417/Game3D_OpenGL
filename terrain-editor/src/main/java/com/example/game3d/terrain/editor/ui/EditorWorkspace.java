package com.example.game3d.terrain.editor.ui;

import com.example.game3d.terrain.editor.compile.AuthoringDocumentCompiler;
import com.example.game3d.terrain.editor.compile.CompileResult;
import com.example.game3d.terrain.editor.compile.DebouncedCompiler;
import com.example.game3d.terrain.editor.edit.AddonEdits;
import com.example.game3d.terrain.editor.edit.AddonPlacementRequest;
import com.example.game3d.terrain.editor.edit.LevelEdits;
import com.example.game3d.terrain.editor.edit.NumericEditRequest;
import com.example.game3d.terrain.editor.edit.OrderedIds;
import com.example.game3d.terrain.editor.edit.RepeatSpec;
import com.example.game3d.terrain.editor.edit.RandomGridAddonEdits;
import com.example.game3d.terrain.editor.edit.TileEdits;
import com.example.game3d.terrain.editor.persistence.EditorPersistence;
import com.example.game3d.terrain.editor.persistence.ExternalConflictGuard;
import com.example.game3d.terrain.editor.persistence.RecoveryService;
import com.example.game3d.terrain.editor.persistence.UserStateDirectory;
import com.example.game3d.terrain.editor.state.EditorHistory;
import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.AddonKind;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import com.example.game3d.terrain.io.validation.ValidationProblem;
import com.example.game3d.core.terrain.SurfaceProperties;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class EditorWorkspace extends BorderPane implements AutoCloseable {
    private static final String ROOT_STRUCTURE = "<root>";
    private final TerrainJsonCodec codec;
    private final EditorPersistence persistence;
    private final EditorHistory history;
    private final DebouncedCompiler compiler;
    private final RecoveryService recovery;
    private final ExternalConflictGuard externalConflict = new ExternalConflictGuard();
    private final ListView<Object> sequence = new ListView<>();
    private final ListView<String> problems = new ListView<>();
    private final TerrainPreviewPane preview = new TerrainPreviewPane();
    private final VBox inspector = new VBox(8);
    private Runnable titleChanged = () -> {};
    private FileTime knownWriteTime;

    public EditorWorkspace(EditorState initial, TerrainDocumentRepository repository) {
        codec = new TerrainJsonCodec();
        persistence = new EditorPersistence(codec);
        history = new EditorHistory(initial);
        compiler = new DebouncedCompiler(new AuthoringDocumentCompiler(repository),
                Duration.ofMillis(180), Platform::runLater);
        recovery = new RecoveryService(codec, UserStateDirectory.terrainEditor().resolve("recovery"),
                Duration.ofSeconds(4));
        knownWriteTime = readWriteTime(initial.sourcePath());
        setPadding(new Insets(6));
        setLeft(buildSequencePane());
        setCenter(preview);
        setRight(buildInspector());
        setBottom(buildProblems());
        sequence.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        sequence.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<Object>) change -> selectionChanged());
        refresh();
    }

    public EditorState state() { return history.state(); }
    public void setTitleChanged(Runnable callback) { titleChanged = callback; }
    public boolean dirty() { return state().isDirty(codec); }

    public void undo() { history.undo(); changed(true); }
    public void redo() { history.redo(); changed(true); }

    /** Re-resolves saved references after an explicit project-content reload. */
    public void recompile() {
        history.replaceState(state().nextCompileRevision());
        refresh();
    }

    public boolean save(Path target) throws IOException {
        return save(target, () -> null);
    }

    public boolean save(Path target, Supplier<Path> saveAsTarget) throws IOException {
        if (target == null) return false;
        Path actualTarget = target;
        boolean sameAsSource = ExternalConflictGuard.sameFileName(
                state().sourcePath(), actualTarget);
        boolean changedNow = sameAsSource && hasExternalChange();
        if (externalConflict.requiresExplicitSaveDecision(
                state().sourcePath(), actualTarget, changedNow)) {
            ButtonType saveAs = new ButtonType("Save As…", ButtonBar.ButtonData.OTHER);
            ButtonType overwrite = new ButtonType(
                    "Overwrite External File", ButtonBar.ButtonData.YES);
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "The original file changed outside the editor. Choose a new file, "
                            + "explicitly overwrite the external version, or cancel.",
                    saveAs, overwrite, ButtonType.CANCEL);
            alert.setTitle("External File Conflict");
            ButtonType answer = alert.showAndWait().orElse(ButtonType.CANCEL);
            if (answer == ButtonType.CANCEL) return false;
            if (answer == saveAs) {
                actualTarget = saveAsTarget.get();
                if (actualTarget == null) return false;
                if (ExternalConflictGuard.sameFileName(
                        state().sourcePath(), actualTarget)) {
                    new Alert(Alert.AlertType.ERROR,
                            "Save As must choose a different file while an external "
                                    + "conflict is pending.").showAndWait();
                    return false;
                }
            }
        }
        history.replaceState(persistence.save(state(), actualTarget));
        knownWriteTime = Files.getLastModifiedTime(actualTarget);
        externalConflict.resolved();
        recovery.clear(state());
        titleChanged.run();
        return true;
    }

    public void focusLost() { recovery.focusLost(); }

    /** Writes a collision-safe replacement before an older selected recovery is retired. */
    public void checkpointRecovery() throws IOException {
        recovery.checkpoint(state());
    }

    /** Checks disk only on explicit focus/tab events; no silent live reload occurs. */
    public boolean checkExternalChange() {
        if (externalConflict.pending()) return true;
        if (!hasExternalChange()) return false;
        resolveExternalConflict();
        return true;
    }

    private boolean hasExternalChange() {
        try {
            return state().sourcePath() != null && knownWriteTime != null
                    && persistence.externallyChanged(state().sourcePath(), knownWriteTime);
        } catch (IOException missingOrUnreadable) {
            return state().sourcePath() != null;
        }
    }

    private boolean resolveExternalConflict() {
        ButtonType reload = new ButtonType("Reload from Disk", ButtonBar.ButtonData.YES);
        ButtonType keep = new ButtonType("Keep Editor Version", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.WARNING,
                "This file changed outside the editor. Reload discards editor changes; "
                        + "keeping requires a later explicit Save to replace the disk version.",
                reload, keep, ButtonType.CANCEL);
        alert.setTitle("External File Change");
        ButtonType answer = alert.showAndWait().orElse(ButtonType.CANCEL);
        if (answer == reload) {
            try {
                EditorPersistence.LoadedDocument loaded = persistence.load(state().sourcePath());
                // Reload explicitly discards the editor version, including its pending recovery.
                recovery.clear(state());
                history.reset(loaded.state());
                knownWriteTime = loaded.writeTime();
                externalConflict.resolved();
                changed(false);
                return false;
            } catch (Exception error) {
                new Alert(Alert.AlertType.ERROR, "Could not reload:\n" + error.getMessage()).showAndWait();
                return false;
            }
        }
        if (answer == keep) {
            knownWriteTime = readWriteTime(state().sourcePath());
            externalConflict.keepEditorVersion();
            return true;
        }
        return false;
    }

    private static FileTime readWriteTime(Path path) {
        try { return path == null ? null : Files.getLastModifiedTime(path); }
        catch (IOException ignored) { return null; }
    }

    public boolean confirmClose(Supplier<Path> saveAsTarget) {
        if (!dirty()) {
            try {
                recovery.clear(state());
                return true;
            } catch (IOException error) {
                new Alert(Alert.AlertType.ERROR,
                        "Could not clear the recovery draft:\n" + error.getMessage())
                        .showAndWait();
                return false;
            }
        }
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discard = new ButtonType("Don't Save", ButtonBar.ButtonData.NO);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Save changes to " + state().document().id() + "?", save, discard, cancel);
        ButtonType answer = alert.showAndWait().orElse(cancel);
        if (answer == cancel) return false;
        if (answer == save) {
            Path target = state().sourcePath() == null ? saveAsTarget.get() : state().sourcePath();
            if (target == null) return false;
            try { return save(target, saveAsTarget); }
            catch (IOException error) {
                new Alert(Alert.AlertType.ERROR, "Could not save:\n" + error.getMessage()).showAndWait();
                return false;
            }
        }
        try {
            recovery.clear(state());
            return true;
        } catch (IOException error) {
            new Alert(Alert.AlertType.ERROR,
                    "Could not discard the recovery draft:\n" + error.getMessage())
                    .showAndWait();
            return false;
        }
    }

    private VBox buildSequencePane() {
        sequence.setPrefWidth(285);
        sequence.setCellFactory(list -> new SequenceCell());
        Button add = new Button("Add Tile");
        add.setOnAction(event -> addTile());
        Button duplicate = new Button("Duplicate");
        duplicate.setOnAction(event -> duplicate());
        Button delete = new Button("Delete");
        delete.setOnAction(event -> delete());
        Button repeat = new Button("Repeat…");
        repeat.setOnAction(event -> repeat());
        HBox actions = new HBox(5, add, duplicate, delete, repeat);
        Button addon = new Button("Add Addon…");
        addon.setOnAction(event -> addAddon());
        Button randomAddons = new Button("Random GRID…");
        randomAddons.setOnAction(event -> addRandomGridAddons());
        Button reference = new Button("Add Reference…");
        reference.setOnAction(event -> addLevelReference());
        Button inline = new Button("Add Inline…");
        inline.setOnAction(event -> addInlineStructure());
        Button up = new Button("↑"); up.setOnAction(event -> moveSelected(-1));
        Button down = new Button("↓"); down.setOnAction(event -> moveSelected(1));
        HBox contentActions = new HBox(5, addon, randomAddons, reference, inline);
        HBox ordering = new HBox(5, new Label("Reorder (or drag):"), up, down);
        ordering.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(6, new Label("Sequence"), sequence, actions,
                contentActions, ordering);
        VBox.setVgrow(sequence, Priority.ALWAYS);
        return box;
    }

    private VBox buildInspector() {
        inspector.setPadding(new Insets(0, 4, 0, 10));
        inspector.setPrefWidth(270);
        return inspector;
    }

    private VBox buildProblems() {
        problems.setPrefHeight(115);
        VBox box = new VBox(3, new Label("Problems"), problems);
        VBox.setVgrow(problems, Priority.ALWAYS);
        return box;
    }

    private void addTile() {
        StructureTarget target = selectedStructureTarget();
        RepeatSpec one = new RepeatSpec(1, 0,0, 0,0, 0,0, 1,0, 1,0);
        if (target == null) {
            if (!(state().document() instanceof LevelDocument level)) return;
            Object selected = sequence.getSelectionModel().getSelectedItem();
            if (selected != null) {
                new Alert(Alert.AlertType.INFORMATION,
                        "Select an inline structure (or one of its tiles) before adding a tile. "
                                + "Clear the selection to create a new inline structure.")
                        .showAndWait();
                return;
            }
            if (level.entries().stream().filter(entry ->
                    entry.kind() == LevelEntry.Kind.INLINE_STRUCTURE).count() > 1) {
                new Alert(Alert.AlertType.INFORMATION,
                        "This level has multiple inline structures. Select the one to edit.")
                        .showAndWait();
                return;
            }
            String entrySourceId = UUID.randomUUID().toString();
            String tileSourceId = UUID.randomUUID().toString();
            StructureDocument inline =
                    com.example.game3d.terrain.editor.state.DocumentFactories.blankStructure(
                            level.id() + ".inline", GridMode.ADVANCED);
            history.apply(LevelEdits.appendTileToSoleInlineOrCreate(entrySourceId, inline,
                    new TileRecord(tileSourceId, true, 0, 0, 0, "NORMAL", 1, 1)));
            changed(true);
            selectSourceId(tileSourceId);
            return;
        }
        int at = target.structure.tiles().size();
        applyStructureEdit(target, TileEdits.repeat(at, true, "NORMAL", one));
        changed(true);
    }

    private void duplicate() {
        StructureTarget target = selectedStructureTarget();
        Set<String> ids = selectedTileIds(target);
        if (target == null || ids.isEmpty()) return;
        applyStructureEdit(target,
                TileEdits.duplicate(ids, () -> UUID.randomUUID().toString()));
        changed(true);
    }

    private void delete() {
        Set<String> entryIds = selectedLevelEntryIds();
        Map<String, Set<String>> tiles = selectedInlineIds(TileRecord.class);
        Map<String, Set<String>> addons = selectedInlineIds(AddonReservation.class);
        if (state().document() instanceof StructureDocument) {
            StructureTarget target = selectedStructureTarget();
            Set<String> tileIds = selectedTileIds(target);
            Set<String> addonIds = selectedAddonIds(target);
            if (tileIds.isEmpty() && addonIds.isEmpty()) return;
            history.apply(document -> deleteFromStructure(
                    (StructureDocument) document, tileIds, addonIds));
        } else if (state().document() instanceof LevelDocument level) {
            if (entryIds.isEmpty() && tiles.isEmpty() && addons.isEmpty()) return;
            history.apply(document -> {
                LevelDocument current = (LevelDocument) document;
                List<LevelEntry> entries = new ArrayList<>();
                for (LevelEntry entry : current.entries()) {
                    if (entryIds.contains(entry.sourceId())) continue;
                    if (entry.kind() != LevelEntry.Kind.INLINE_STRUCTURE) {
                        entries.add(entry);
                        continue;
                    }
                    StructureDocument edited = deleteFromStructure(entry.inlineStructure(),
                            tiles.getOrDefault(entry.sourceId(), Set.of()),
                            addons.getOrDefault(entry.sourceId(), Set.of()));
                    entries.add(LevelEntry.inline(entry.sourceId(), edited));
                }
                return current.withEntries(entries);
            });
        } else {
            return;
        }
        changed(true);
    }

    private void repeat() {
        StructureTarget target = selectedStructureTarget();
        if (target == null) return;
        StructureDocument structure = target.structure;
        Spinner<Integer> count = new Spinner<>(1, 1000, 10);
        String[] labels = {"Start turn", "Turn increment", "Start slope", "Slope increment",
                "Start lift", "Lift increment", "Start alpha", "Alpha increment",
                "Start brightness", "Brightness increment"};
        String[] defaults = {"0", "2", "0", "0", "0", "0", "1", "0", "1", "0"};
        TextField[] values = new TextField[labels.length];
        GridPane fields = new GridPane();
        fields.setHgap(8); fields.setVgap(8);
        fields.addRow(0, new Label("Count"), count);
        for (int i = 0; i < labels.length; i++) {
            values[i] = new TextField(defaults[i]);
            fields.addRow(i + 1, new Label(labels[i] + (i < 4 ? " (°)" : "")), values[i]);
        }
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Repeat Tiles");
        dialog.getDialogPane().setContent(fields);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try {
            double[] numbers = new double[values.length];
            for (int i = 0; i < values.length; i++) numbers[i] = Double.parseDouble(values[i].getText());
            RepeatSpec spec = new RepeatSpec(count.getValue(), numbers[0], numbers[1],
                    numbers[2], numbers[3], numbers[4], numbers[5], numbers[6], numbers[7],
                    numbers[8], numbers[9]);
            applyStructureEdit(target,
                    TileEdits.repeat(structure.tiles().size(), true, "NORMAL", spec));
            changed(true);
        } catch (NumberFormatException invalid) {
            new Alert(Alert.AlertType.ERROR, "Every repetition value must be a number").showAndWait();
        }
    }

    private void addAddon() {
        StructureTarget target = selectedStructureTarget();
        if (target == null) return;
        StructureDocument structure = target.structure;
        TileRecord tile = selectedTile(target);
        if (tile != null && !tile.solid()) {
            new Alert(Alert.AlertType.WARNING,
                    "The selected tile is a gap. Select a solid tile for addon placement.")
                    .showAndWait();
            return;
        }
        if (tile == null) {
            for (int i = structure.tiles().size() - 1; i >= 0; i--) {
                if (structure.tiles().get(i).solid()) {
                    tile = structure.tiles().get(i);
                    break;
                }
            }
        }
        if (tile == null) {
            new Alert(Alert.AlertType.WARNING, "Add a solid tile first.").showAndWait();
            return;
        }
        ChoiceDialog<AddonKind> dialog = new ChoiceDialog<>(AddonKind.DEATH_SPIKE,
                AddonKind.DEATH_SPIKE, AddonKind.AIR_JUMP_POTION,
                AddonKind.PORTAL_ENTRANCE, AddonKind.PORTAL_EXIT);
        dialog.setTitle("Add Addon");
        dialog.setHeaderText("Choose an addon (choosing a portal adds a complete pair)");
        Optional<AddonKind> selectedKind = dialog.showAndWait();
        if (selectedKind.isEmpty()) return;
        AddonKind kind = selectedKind.get();
        Optional<Placement> first = choosePlacement(structure, tile,
                kind == AddonKind.DEATH_SPIKE || kind == AddonKind.AIR_JUMP_POTION
                        ? "Addon Placement" : "First Portal Endpoint");
        if (first.isEmpty()) return;
        if (kind == AddonKind.DEATH_SPIKE || kind == AddonKind.AIR_JUMP_POTION) {
            AddonReservation addon = new AddonReservation(UUID.randomUUID().toString(), kind,
                    first.get(), null, Collections.emptyMap());
            applyStructureEdit(target, AddonEdits.add(addon));
        } else {
            Optional<Placement> second = choosePlacement(structure, tile,
                    "Second Portal Endpoint");
            if (second.isEmpty()) return;
            String firstId = UUID.randomUUID().toString();
            String secondId = UUID.randomUUID().toString();
            AddonKind secondKind = kind == AddonKind.PORTAL_ENTRANCE
                    ? AddonKind.PORTAL_EXIT : AddonKind.PORTAL_ENTRANCE;
            AddonReservation firstEndpoint = new AddonReservation(firstId, kind,
                    first.get(), secondId, Collections.emptyMap());
            AddonReservation secondEndpoint = new AddonReservation(secondId, secondKind,
                    second.get(), firstId, Collections.emptyMap());
            applyStructureEdit(target, document -> {
                StructureDocument current = (StructureDocument) document;
                List<AddonReservation> updated = new ArrayList<>(current.addons());
                updated.add(firstEndpoint);
                updated.add(secondEndpoint);
                return current.withAddons(updated);
            });
        }
        changed(true);
    }

    private void addRandomGridAddons() {
        StructureTarget target = selectedStructureTarget();
        if (target == null) return;
        if (target.structure.gridMode() != GridMode.ADVANCED) {
            new Alert(Alert.AlertType.WARNING,
                    "Random GRID placement is available for ADVANCED structures only.")
                    .showAndWait();
            return;
        }
        if (target.structure.tiles().isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Add solid terrain first.").showAndWait();
            return;
        }

        ChoiceBox<AddonKind> kind = new ChoiceBox<>(FXCollections.observableArrayList(
                AddonKind.DEATH_SPIKE, AddonKind.AIR_JUMP_POTION));
        kind.setValue(AddonKind.DEATH_SPIKE);
        Spinner<Integer> count = new Spinner<>(1,
                com.example.game3d.terrain.io.validation.TerrainContentLimits
                        .MAX_STRUCTURE_ADDONS, 1);
        TextField seed = new TextField("0");
        GridPane fields = new GridPane();
        fields.setHgap(8);
        fields.setVgap(8);
        fields.addRow(0, new Label("Kind"), kind);
        fields.addRow(1, new Label("Count"), count);
        fields.addRow(2, new Label("Seed (signed 64-bit)"), seed);
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Random GRID Addons");
        dialog.setHeaderText("The shared reservation engine selects free physical grid cells. "
                + "The chosen cells are saved immediately as ordinary explicit placements.");
        dialog.getDialogPane().setContent(fields);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        try {
            long selectedSeed = Long.parseLong(seed.getText().trim());
            applyStructureEdit(target, RandomGridAddonEdits.add(
                    selectedSeed, count.getValue(), kind.getValue()));
            changed(true);
        } catch (NumberFormatException invalidSeed) {
            new Alert(Alert.AlertType.ERROR,
                    "Seed must be a signed 64-bit integer.").showAndWait();
        } catch (IllegalArgumentException | IllegalStateException invalidPlacement) {
            new Alert(Alert.AlertType.ERROR, invalidPlacement.getMessage()).showAndWait();
        }
    }

    private Optional<Placement> choosePlacement(
            StructureDocument structure, TileRecord preferred, String title) {
        int selectedRow = Math.max(1, structure.tiles().indexOf(preferred) + 1);
        final int physicalGridRows;
        try {
            physicalGridRows = AddonPlacementRequest.physicalGridRowCount(structure);
        } catch (IllegalArgumentException invalidGeometry) {
            new Alert(Alert.AlertType.ERROR,
                    "Cannot derive physical grid rows: " + invalidGeometry.getMessage())
                    .showAndWait();
            return Optional.empty();
        }
        int tileRows = structure.tiles().size();
        int initialRowLimit = Math.max(tileRows, Math.max(1, physicalGridRows));
        int columns = com.example.game3d.authoring.TrackProfile
                .gameplayDefault().gridColumns;
        ChoiceBox<Placement.Mode> mode = new ChoiceBox<>(FXCollections.observableArrayList(
                Placement.Mode.SEGMENT_NORMALIZED, Placement.Mode.GRID));
        mode.setValue(Placement.Mode.SEGMENT_NORMALIZED);
        Spinner<Integer> rowStart = new Spinner<>(1, initialRowLimit, selectedRow);
        Spinner<Integer> rowEnd = new Spinner<>(1, initialRowLimit, selectedRow);
        Spinner<Integer> columnStart = new Spinner<>(1, columns, 1);
        Spinner<Integer> columnEnd = new Spinner<>(1, columns, columns);
        TextField across = new TextField("0.5");
        TextField along = new TextField("0.5");
        GridPane fields = new GridPane();
        fields.setHgap(8); fields.setVgap(8);
        fields.addRow(0, new Label("Mode"), mode);
        fields.addRow(1, new Label("Segment / first grid row"), rowStart);
        fields.addRow(2, new Label("Last grid row"), rowEnd);
        fields.addRow(3, new Label("First column"), columnStart);
        fields.addRow(4, new Label("Last column"), columnEnd);
        fields.addRow(5, new Label("Across [0..1]"), across);
        fields.addRow(6, new Label("Along [0..1]"), along);
        Runnable updateMode = () -> {
            boolean grid = mode.getValue() == Placement.Mode.GRID;
            int rowLimit = grid ? Math.max(1, physicalGridRows) : tileRows;
            javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory startValues =
                    (javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory)
                            rowStart.getValueFactory();
            javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory endValues =
                    (javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory)
                            rowEnd.getValueFactory();
            startValues.setMax(rowLimit);
            endValues.setMax(rowLimit);
            if (startValues.getValue() > rowLimit) startValues.setValue(rowLimit);
            if (endValues.getValue() > rowLimit) endValues.setValue(rowLimit);
            rowEnd.setDisable(!grid);
            columnStart.setDisable(!grid);
            columnEnd.setDisable(!grid);
            across.setDisable(grid);
            along.setDisable(grid);
        };
        mode.valueProperty().addListener((observable, oldValue, value) -> updateMode.run());
        updateMode.run();
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle(title);
        dialog.setHeaderText("GRID uses physical rows derived from completed geometry; "
                + physicalGridRows + " rows are available. Normalized placement uses "
                + "the selected segment.");
        dialog.getDialogPane().setContent(fields);
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return Optional.empty();
        }
        try {
            AddonPlacementRequest request;
            if (mode.getValue() == Placement.Mode.GRID) {
                request = AddonPlacementRequest.grid(rowStart.getValue(), rowEnd.getValue(),
                        columnStart.getValue(), columnEnd.getValue());
            } else {
                request = AddonPlacementRequest.normalized(
                        structure.tiles().get(rowStart.getValue() - 1).sourceId(),
                        Double.parseDouble(across.getText()),
                        Double.parseDouble(along.getText()));
            }
            return Optional.of(request.toPlacement(structure));
        } catch (IllegalArgumentException invalid) {
            new Alert(Alert.AlertType.ERROR, invalid.getMessage()).showAndWait();
            return Optional.empty();
        }
    }

    private void addLevelReference() {
        if (!(state().document() instanceof LevelDocument level)) return;
        javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog("structure.id");
        dialog.setTitle("Add Structure Reference");
        dialog.setHeaderText("Saved structure document ID");
        dialog.showAndWait().map(String::trim).filter(value -> !value.isEmpty()).ifPresent(id -> {
            history.apply(LevelEdits.add(
                    LevelEntry.reference(UUID.randomUUID().toString(), id)));
            changed(true);
        });
    }

    private void addInlineStructure() {
        if (!(state().document() instanceof LevelDocument)) return;
        javafx.scene.control.TextInputDialog idDialog =
                new javafx.scene.control.TextInputDialog("inline.structure");
        idDialog.setTitle("Add Inline Structure");
        idDialog.setHeaderText("Stable ID for the embedded structure");
        Optional<String> id = idDialog.showAndWait().map(String::trim)
                .filter(value -> !value.isEmpty());
        if (id.isEmpty()) return;
        ChoiceDialog<GridMode> mode = new ChoiceDialog<>(GridMode.ADVANCED,
                GridMode.ADVANCED, GridMode.BASIC);
        mode.setTitle("Inline Structure Grid");
        mode.setHeaderText("Choose how addon reservations share grid cells");
        mode.showAndWait().ifPresent(selected -> {
            StructureDocument structure =
                    com.example.game3d.terrain.editor.state.DocumentFactories
                            .blankStructure(id.get(), selected);
            String entrySourceId = UUID.randomUUID().toString();
            history.apply(LevelEdits.add(LevelEntry.inline(entrySourceId, structure)));
            changed(true);
            selectSourceId(entrySourceId);
        });
    }

    private void moveSelected(int delta) {
        Object selectedItem = sequence.getSelectionModel().getSelectedItem();
        Object selectedValue = unwrap(selectedItem);
        if (selectedValue instanceof LevelEntry entry
                && state().document() instanceof LevelDocument level) {
            List<String> ids = level.entries().stream().map(LevelEntry::sourceId).toList();
            List<String> reordered = OrderedIds.moveBy(ids, entry.sourceId(), delta);
            if (reordered.equals(ids)) return;
            history.apply(LevelEdits.reorder(reordered));
        } else if (selectedValue instanceof TileRecord tile) {
            StructureTarget target = targetForItem(selectedItem);
            if (target == null) return;
            List<String> ids = target.structure.tiles().stream()
                    .map(TileRecord::sourceId).toList();
            List<String> reordered = OrderedIds.moveBy(ids, tile.sourceId(), delta);
            if (reordered.equals(ids)) return;
            applyStructureEdit(target, TileEdits.reorder(reordered));
        } else {
            return;
        }
        changed(true);
    }

    private Set<String> selectedTileIds(StructureTarget target) {
        Set<String> ids = new LinkedHashSet<>();
        if (target == null) return ids;
        for (Object item : sequence.getSelectionModel().getSelectedItems()) {
            if (sameTarget(target, item) && unwrap(item) instanceof TileRecord tile) {
                ids.add(tile.sourceId());
            }
        }
        return ids;
    }

    private Set<String> selectedAddonIds(StructureTarget target) {
        Set<String> ids = new LinkedHashSet<>();
        if (target == null) return ids;
        for (Object item : sequence.getSelectionModel().getSelectedItems()) {
            if (sameTarget(target, item) && unwrap(item) instanceof AddonReservation addon) {
                ids.add(addon.sourceId());
            }
        }
        return ids;
    }

    private void selectionChanged() {
        StructureTarget target = selectedStructureTarget();
        Set<String> ids = selectedTileIds(target);
        Set<String> selectedAddons = selectedAddonIds(target);
        Set<String> all = new LinkedHashSet<>(ids);
        all.addAll(selectedAddons);
        all.addAll(selectedLevelEntryIds());
        history.replaceState(state().withSelection(all));
        inspector.getChildren().clear();
        inspector.getChildren().add(new Label(all.isEmpty() ? "Document" : all.size() + " selected"));
        inspector.getChildren().add(new Separator());
        if (all.isEmpty()) {
            inspector.getChildren().addAll(new Label("ID"), new Label(state().document().id()));
            return;
        }
        if (ids.isEmpty()) {
            if (!selectedAddons.isEmpty()) {
                inspector.getChildren().add(new Label(
                        "Addon placement is resolved through the shared grid brush."));
                if (selectedAddons.size() == 1 && target != null) {
                    String sourceId = selectedAddons.iterator().next();
                    AddonReservation selected = addon(target.structure, sourceId);
                    if (selected != null) {
                        Button move = new Button("Change Placement…");
                        move.setMaxWidth(Double.MAX_VALUE);
                        move.setOnAction(event -> {
                            TileRecord preferred = preferredTile(
                                    target.structure, selected.placement());
                            if (preferred == null) {
                                new Alert(Alert.AlertType.WARNING,
                                        "Add a solid tile before moving this addon.")
                                        .showAndWait();
                                return;
                            }
                            choosePlacement(target.structure, preferred,
                                    "Change Addon Placement").ifPresent(placement -> {
                                applyStructureEdit(target,
                                        AddonEdits.replacePlacement(sourceId, placement));
                                changed(true);
                            });
                        });
                        inspector.getChildren().add(move);
                    }
                }
            } else if (target != null && target.entrySourceId != null) {
                inspector.getChildren().addAll(
                        new Label("Inline structure"),
                        new Label(target.structure.id()),
                        new Label("Use Add Tile / Add Addon to edit it in place."));
            }
            return;
        }
        Button numeric = new Button("Set / Add / Sequence…");
        numeric.setMaxWidth(Double.MAX_VALUE);
        numeric.setOnAction(event -> new NumericEditDialog().show(ids.size()).ifPresent(request -> {
            applyStructureEdit(target, TileEdits.numeric(ids, request.field(), request.mode(),
                    request.start(), request.increment()));
            changed(true);
        }));
        ChoiceBox<String> solidity = new ChoiceBox<>(FXCollections.observableArrayList(
                "Keep solid/gap", "Solid", "Gap"));
        solidity.setValue("Keep solid/gap");
        solidity.setMaxWidth(Double.MAX_VALUE);
        ChoiceBox<String> surface = new ChoiceBox<>(FXCollections.observableArrayList(
                "Keep surface",
                SurfaceProperties.Kind.NORMAL.jsonTag,
                SurfaceProperties.Kind.BOOST_RAMP.jsonTag,
                SurfaceProperties.Kind.BOOST_RAMP_LAUNCH.jsonTag,
                SurfaceProperties.Kind.LEGACY_BOOST.jsonTag));
        surface.setValue("Keep surface");
        surface.setMaxWidth(Double.MAX_VALUE);
        Button applyCategories = new Button("Apply Solid / Gap + Surface");
        applyCategories.setMaxWidth(Double.MAX_VALUE);
        applyCategories.setOnAction(event -> {
            Boolean selectedSolid = "Keep solid/gap".equals(solidity.getValue())
                    ? null : "Solid".equals(solidity.getValue());
            String selectedSurface = "Keep surface".equals(surface.getValue())
                    ? null : surface.getValue();
            if (selectedSolid == null && selectedSurface == null) return;
            applyStructureEdit(target, TileEdits.setCategorical(
                    ids, selectedSolid, selectedSurface));
            changed(true);
        });
        inspector.getChildren().addAll(
                new Label("Solid / gap"), solidity,
                new Label("Surface"), surface, applyCategories, numeric);
    }

    private static AddonReservation addon(
            StructureDocument structure, String sourceId) {
        for (AddonReservation addon : structure.addons()) {
            if (addon.sourceId().equals(sourceId)) return addon;
        }
        return null;
    }

    private static TileRecord preferredTile(
            StructureDocument structure, Placement placement) {
        if (placement.mode() == Placement.Mode.SEGMENT_NORMALIZED) {
            for (TileRecord tile : structure.tiles()) {
                if (tile.sourceId().equals(placement.segmentSourceId()) && tile.solid()) {
                    return tile;
                }
            }
        }
        for (TileRecord tile : structure.tiles()) {
            if (tile.solid()) return tile;
        }
        return null;
    }

    private void changed(boolean recover) {
        refresh();
        if (recover) recovery.edited(state());
        titleChanged.run();
    }

    private void refresh() {
        Set<String> selectionKeys = new LinkedHashSet<>();
        for (Object selected : sequence.getSelectionModel().getSelectedItems()) {
            selectionKeys.add(selectionKey(selected));
        }
        List<?> items = state().document() instanceof StructureDocument structure
                ? combinedStructureItems(structure) : state().document() instanceof LevelDocument level
                ? combinedLevelItems(level) : List.of();
        sequence.setItems(FXCollections.observableArrayList(items));
        if (!selectionKeys.isEmpty()) {
            for (int i = 0; i < sequence.getItems().size(); i++) {
                if (selectionKeys.contains(selectionKey(sequence.getItems().get(i)))) {
                    sequence.getSelectionModel().select(i);
                }
            }
        }
        compiler.submit(state().revision(), state().document(), this::compiled);
    }

    /** Stable and occurrence-aware: identical local IDs in different inline entries stay distinct. */
    private static String selectionKey(Object item) {
        if (item instanceof InlineItem inline) {
            Object value = inline.value;
            String kind = value instanceof TileRecord ? "tile" : "addon";
            String id = value instanceof TileRecord tile
                    ? tile.sourceId() : ((AddonReservation) value).sourceId();
            return "inline/" + inline.entrySourceId + "/" + kind + "/" + id;
        }
        if (item instanceof LevelEntry entry) return "entry/" + entry.sourceId();
        if (item instanceof TileRecord tile) return "root/tile/" + tile.sourceId();
        if (item instanceof AddonReservation addon) return "root/addon/" + addon.sourceId();
        return "other/" + String.valueOf(item);
    }

    private static List<Object> combinedStructureItems(StructureDocument structure) {
        List<Object> items = new ArrayList<>();
        items.addAll(structure.tiles());
        items.addAll(structure.addons());
        return items;
    }

    private static List<Object> combinedLevelItems(LevelDocument level) {
        List<Object> items = new ArrayList<>();
        for (LevelEntry entry : level.entries()) {
            items.add(entry);
            if (entry.kind() == LevelEntry.Kind.INLINE_STRUCTURE) {
                for (TileRecord tile : entry.inlineStructure().tiles()) {
                    items.add(new InlineItem(entry.sourceId(), tile));
                }
                for (AddonReservation addon : entry.inlineStructure().addons()) {
                    items.add(new InlineItem(entry.sourceId(), addon));
                }
            }
        }
        return items;
    }

    private void compiled(CompileResult result) {
        if (result.revision() != state().revision()) return;
        history.replaceState(state().withProblems(result.problems()));
        List<String> messages = new ArrayList<>();
        for (ValidationProblem problem : result.problems()) messages.add(problem.toString());
        problems.setItems(FXCollections.observableArrayList(messages));
        if (result.successful()) preview.show(result.snapshot(), result.sourceSegmentIds(),
                result.sourceAddonIds(), this::selectSourceId);
    }

    private void selectSourceId(String sourceId) {
        int firstSeparator = sourceId.indexOf('/');
        String occurrenceEntryId = firstSeparator < 0
                ? null : sourceId.substring(0, firstSeparator);
        String localSourceId = firstSeparator < 0
                ? sourceId : sourceId.substring(sourceId.lastIndexOf('/') + 1);
        int occurrenceEntryIndex = -1;
        for (int i = 0; i < sequence.getItems().size(); i++) {
            Object item = sequence.getItems().get(i);
            Object value = unwrap(item);
            if (occurrenceEntryId != null && value instanceof LevelEntry entry
                    && occurrenceEntryId.equals(entry.sourceId())) {
                occurrenceEntryIndex = i;
                continue;
            }
            String id = value instanceof TileRecord tile ? tile.sourceId()
                    : value instanceof LevelEntry entry ? entry.sourceId() : null;
            if (value instanceof AddonReservation addon) id = addon.sourceId();
            boolean sameOccurrence = occurrenceEntryId == null
                    || item instanceof InlineItem inline
                    && occurrenceEntryId.equals(inline.entrySourceId());
            if (sameOccurrence && localSourceId.equals(id)) {
                sequence.getSelectionModel().clearAndSelect(i);
                sequence.scrollTo(i);
                return;
            }
        }
        // Referenced structures and nested level contents are not editable in place. Selecting
        // their top-level occurrence still gives a deterministic, useful picking result.
        if (occurrenceEntryIndex >= 0) {
            sequence.getSelectionModel().clearAndSelect(occurrenceEntryIndex);
            sequence.scrollTo(occurrenceEntryIndex);
        }
    }

    private StructureTarget selectedStructureTarget() {
        if (state().document() instanceof StructureDocument structure) {
            return new StructureTarget(null, structure);
        }
        Object selected = sequence.getSelectionModel().getSelectedItem();
        return targetForItem(selected);
    }

    private StructureTarget targetForItem(Object item) {
        if (!(state().document() instanceof LevelDocument level) || item == null) {
            return state().document() instanceof StructureDocument structure
                    ? new StructureTarget(null, structure) : null;
        }
        String entryId = item instanceof InlineItem inline
                ? inline.entrySourceId
                : item instanceof LevelEntry entry
                && entry.kind() == LevelEntry.Kind.INLINE_STRUCTURE
                ? entry.sourceId() : null;
        if (entryId == null) return null;
        for (LevelEntry entry : level.entries()) {
            if (entry.sourceId().equals(entryId)
                    && entry.kind() == LevelEntry.Kind.INLINE_STRUCTURE) {
                return new StructureTarget(entryId, entry.inlineStructure());
            }
        }
        return null;
    }

    private void applyStructureEdit(
            StructureTarget target,
            com.example.game3d.terrain.editor.state.DocumentEdit edit) {
        if (target.entrySourceId == null) {
            history.apply(edit);
        } else {
            history.apply(LevelEdits.editInline(target.entrySourceId, edit));
        }
    }

    private TileRecord selectedTile(StructureTarget target) {
        if (target == null) return null;
        for (Object item : sequence.getSelectionModel().getSelectedItems()) {
            if (sameTarget(target, item) && unwrap(item) instanceof TileRecord tile) {
                return tile;
            }
        }
        return null;
    }

    private boolean sameTarget(StructureTarget target, Object item) {
        if (target.entrySourceId == null) {
            return !(item instanceof InlineItem)
                    && state().document() instanceof StructureDocument;
        }
        return item instanceof InlineItem inline
                && target.entrySourceId.equals(inline.entrySourceId);
    }

    private Set<String> selectedLevelEntryIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (Object item : sequence.getSelectionModel().getSelectedItems()) {
            if (item instanceof LevelEntry entry) ids.add(entry.sourceId());
        }
        return ids;
    }

    private <T> Map<String, Set<String>> selectedInlineIds(Class<T> valueType) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (Object item : sequence.getSelectionModel().getSelectedItems()) {
            if (!(item instanceof InlineItem inline)
                    || !valueType.isInstance(inline.value)) continue;
            String sourceId = inline.value instanceof TileRecord tile
                    ? tile.sourceId() : ((AddonReservation) inline.value).sourceId();
            result.computeIfAbsent(inline.entrySourceId,
                    ignored -> new LinkedHashSet<>()).add(sourceId);
        }
        return result;
    }

    private static StructureDocument deleteFromStructure(
            StructureDocument structure,
            Set<String> tileIds,
            Set<String> addonIds) {
        List<TileRecord> tiles = new ArrayList<>();
        for (TileRecord tile : structure.tiles()) {
            if (!tileIds.contains(tile.sourceId())) tiles.add(tile);
        }
        List<AddonReservation> addons = new ArrayList<>();
        for (AddonReservation addon : structure.addons()) {
            if (!addonIds.contains(addon.sourceId())) addons.add(addon);
        }
        return new StructureDocument(structure.formatVersion(), structure.id(),
                structure.gridMode(), tiles, addons);
    }

    private void reorderDragged(ReorderHandle source, ReorderHandle target) {
        if (!source.compatible(target) || source.sourceId.equals(target.sourceId)) return;
        if (source.kind.equals("entry")
                && state().document() instanceof LevelDocument level) {
            List<String> ids = level.entries().stream().map(LevelEntry::sourceId).toList();
            history.apply(LevelEdits.reorder(
                    OrderedIds.moveOnto(ids, source.sourceId, target.sourceId)));
        } else if (source.kind.equals("tile")) {
            StructureTarget structureTarget = ROOT_STRUCTURE.equals(source.parentId)
                    ? state().document() instanceof StructureDocument structure
                    ? new StructureTarget(null, structure) : null
                    : targetForInlineEntry(source.parentId);
            if (structureTarget == null) return;
            List<String> ids = structureTarget.structure.tiles().stream()
                    .map(TileRecord::sourceId).toList();
            applyStructureEdit(structureTarget, TileEdits.reorder(
                    OrderedIds.moveOnto(ids, source.sourceId, target.sourceId)));
        } else {
            return;
        }
        changed(true);
    }

    private StructureTarget targetForInlineEntry(String entryId) {
        if (!(state().document() instanceof LevelDocument level)) return null;
        for (LevelEntry entry : level.entries()) {
            if (entry.sourceId().equals(entryId)
                    && entry.kind() == LevelEntry.Kind.INLINE_STRUCTURE) {
                return new StructureTarget(entryId, entry.inlineStructure());
            }
        }
        return null;
    }

    private ReorderHandle reorderHandle(Object item) {
        if (item instanceof LevelEntry entry) {
            return new ReorderHandle("entry", "", entry.sourceId());
        }
        if (item instanceof InlineItem inline && inline.value instanceof TileRecord tile) {
            return new ReorderHandle("tile", inline.entrySourceId, tile.sourceId());
        }
        if (item instanceof TileRecord tile
                && state().document() instanceof StructureDocument) {
            return new ReorderHandle("tile", ROOT_STRUCTURE, tile.sourceId());
        }
        return null;
    }

    private static Object unwrap(Object item) {
        return item instanceof InlineItem inline ? inline.value : item;
    }

    private final class SequenceCell extends ListCell<Object> {
        SequenceCell() {
            setOnDragDetected(event -> {
                ReorderHandle handle = reorderHandle(getItem());
                if (handle == null) return;
                Dragboard board = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(handle.encode());
                board.setContent(content);
                event.consume();
            });
            setOnDragOver(event -> {
                ReorderHandle target = reorderHandle(getItem());
                ReorderHandle source = ReorderHandle.decode(
                        event.getDragboard().getString());
                if (source != null && target != null && source.compatible(target)) {
                    event.acceptTransferModes(TransferMode.MOVE);
                    event.consume();
                }
            });
            setOnDragDropped(event -> {
                ReorderHandle source = ReorderHandle.decode(
                        event.getDragboard().getString());
                ReorderHandle target = reorderHandle(getItem());
                boolean accepted = source != null && target != null
                        && source.compatible(target);
                if (accepted) reorderDragged(source, target);
                event.setDropCompleted(accepted);
                event.consume();
            });
        }

        @Override protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                return;
            }
            boolean inline = item instanceof InlineItem;
            Object value = unwrap(item);
            String prefix = inline ? "    ↳ " : "";
            if (value instanceof TileRecord tile) {
                setText(prefix + (tile.solid() ? "Tile" : "Gap")
                        + "  turn " + tile.turnDeltaDegrees() + "°  slope "
                        + tile.absoluteSlopeDegrees() + "°");
            } else if (value instanceof LevelEntry entry) {
                setText(entry.kind() + "  " + (entry.isReference()
                        ? entry.referenceId() : entry.inlineStructure().id()));
            } else if (value instanceof AddonReservation addon) {
                setText(prefix + "Addon " + addon.kind() + "  "
                        + addon.sourceId().substring(0,
                        Math.min(8, addon.sourceId().length())));
            } else {
                setText(prefix + value);
            }
        }
    }

    private record StructureTarget(
            String entrySourceId, StructureDocument structure) {
    }

    private record InlineItem(String entrySourceId, Object value) {
    }

    private record ReorderHandle(String kind, String parentId, String sourceId) {
        boolean compatible(ReorderHandle other) {
            return other != null && kind.equals(other.kind)
                    && parentId.equals(other.parentId);
        }

        String encode() {
            return "terrain-editor-order|" + kind + "|" + parentId + "|" + sourceId;
        }

        static ReorderHandle decode(String value) {
            if (value == null) return null;
            String[] fields = value.split("\\|", -1);
            if (fields.length != 4 || !fields[0].equals("terrain-editor-order")) return null;
            return new ReorderHandle(fields[1], fields[2], fields[3]);
        }
    }

    @Override public void close() { compiler.close(); recovery.close(); }
}
