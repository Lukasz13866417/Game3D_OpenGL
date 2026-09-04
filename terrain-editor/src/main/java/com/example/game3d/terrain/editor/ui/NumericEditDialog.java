package com.example.game3d.terrain.editor.ui;

import com.example.game3d.terrain.editor.edit.NumericEditRequest;
import com.example.game3d.terrain.editor.edit.TileEdits;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.Optional;

/** Explicit Set/Add/Linear Sequence editor for every numeric tile field. */
public final class NumericEditDialog {
    public Optional<NumericEditRequest> show(int selectedCount) {
        ComboBox<TileEdits.Field> field = new ComboBox<>();
        field.getItems().setAll(TileEdits.Field.values());
        field.getSelectionModel().select(TileEdits.Field.TURN);
        ComboBox<TileEdits.Mode> mode = new ComboBox<>();
        mode.getItems().setAll(TileEdits.Mode.values());
        mode.getSelectionModel().select(TileEdits.Mode.SET);
        TextField start = new TextField("0");
        TextField increment = new TextField("1");
        increment.disableProperty().bind(mode.valueProperty().isNotEqualTo(TileEdits.Mode.LINEAR_SEQUENCE));

        GridPane form = new GridPane();
        form.setHgap(8); form.setVgap(8);
        form.addRow(0, new Label("Selected tiles"), new Label(Integer.toString(selectedCount)));
        form.addRow(1, new Label("Field"), field);
        form.addRow(2, new Label("Operation"), mode);
        form.addRow(3, new Label("Value / start"), start);
        form.addRow(4, new Label("Increment"), increment);

        ButtonType apply = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION, "", apply, ButtonType.CANCEL);
        dialog.setTitle("Edit Numeric Fields");
        dialog.setHeaderText("Apply one compound edit in sequence order");
        dialog.getDialogPane().setContent(form);
        while (dialog.showAndWait().orElse(ButtonType.CANCEL) == apply) {
            try {
                TileEdits.Mode selectedMode = mode.getValue();
                NumericEditRequest request = new NumericEditRequest(field.getValue(), selectedMode,
                        Double.parseDouble(start.getText().trim()),
                        selectedMode == TileEdits.Mode.LINEAR_SEQUENCE
                                ? Double.parseDouble(increment.getText().trim()) : 0.0);
                if (selectedMode == TileEdits.Mode.LINEAR_SEQUENCE && selectedCount > 0) {
                    double last = request.start()
                            + (selectedCount - 1.0) * request.increment();
                    if (!Double.isFinite(last)) {
                        throw new IllegalArgumentException(
                                "Numeric sequence produces a non-finite value");
                    }
                }
                return Optional.of(request);
            } catch (IllegalArgumentException invalid) {
                new Alert(Alert.AlertType.ERROR, "Values must be finite numbers.").showAndWait();
            }
        }
        return Optional.empty();
    }
}
