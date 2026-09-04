package com.example.game3d.terrain.editor.ui;

import com.example.game3d.core.terrain.TerrainSnapshot;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Collections;
import java.util.Optional;

/** Responsive full-provider preview whose action bar is never part of the 3D viewport. */
public final class ProviderImportDialog {
    private static final double SCREEN_FRACTION = .90;
    private static final double PREFERRED_WIDTH = 1100;
    private static final double PREFERRED_HEIGHT = 820;
    private static final double MINIMUM_WIDTH = 680;
    private static final double MINIMUM_HEIGHT = 480;

    public enum Action {
        IMPORT_STRUCTURE,
        IMPORT_INLINE_LEVEL
    }

    private ProviderImportDialog() {
    }

    public static Optional<Action> show(
            Window owner, String header, TerrainSnapshot snapshot) {
        return createDialog(owner, header, snapshot,
                screenFor(owner).getVisualBounds()).showAndWait();
    }

    /** Package-visible factory so responsive dialog behavior can be exercised by TestFX. */
    static Dialog<Action> createDialog(
            Window owner,
            String header,
            TerrainSnapshot snapshot,
            Rectangle2D visualBounds) {
        if (header == null || snapshot == null) {
            throw new IllegalArgumentException("Provider preview arguments are required");
        }
        if (visualBounds == null || !Double.isFinite(visualBounds.getWidth())
                || !Double.isFinite(visualBounds.getHeight())
                || visualBounds.getWidth() <= 0 || visualBounds.getHeight() <= 0) {
            throw new IllegalArgumentException("A positive finite visual screen is required");
        }
        double maximumWidth = visualBounds.getWidth() * SCREEN_FRACTION;
        double maximumHeight = visualBounds.getHeight() * SCREEN_FRACTION;
        double preferredWidth = Math.min(PREFERRED_WIDTH, maximumWidth);
        double preferredHeight = Math.min(PREFERRED_HEIGHT, maximumHeight);

        TerrainPreviewPane preview = new TerrainPreviewPane();
        preview.setMinSize(0, 0);
        preview.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        preview.show(snapshot, Collections.emptyMap(), Collections.emptyMap(), ignored -> { });
        StackPane viewport = new StackPane(preview);
        viewport.setMinSize(0, 0);
        viewport.setPrefSize(Math.max(0, preferredWidth - 30),
                Math.max(0, preferredHeight - 145));

        ButtonType importStructure = new ButtonType(
                "Import as JSON Structure", ButtonBar.ButtonData.APPLY);
        ButtonType importLevel = new ButtonType(
                "Import as Inline JSON Level", ButtonBar.ButtonData.YES);
        Dialog<Action> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Java Provider Preview");
        dialog.setHeaderText(header);
        dialog.setResizable(true);
        DialogPane pane = dialog.getDialogPane();
        // These are whole-window upper bounds until the stage is shown and its exact decoration
        // inset is known. configureShownStage() then subtracts that inset from every pane bound.
        pane.setMinSize(Math.min(MINIMUM_WIDTH, maximumWidth),
                Math.min(MINIMUM_HEIGHT, maximumHeight));
        pane.setPrefSize(preferredWidth, preferredHeight);
        pane.setMaxSize(maximumWidth, maximumHeight);
        pane.setContent(viewport);
        pane.getButtonTypes().addAll(
                importStructure, importLevel, ButtonType.CLOSE);
        dialog.setResultConverter(button -> button == importStructure
                ? Action.IMPORT_STRUCTURE : button == importLevel
                ? Action.IMPORT_INLINE_LEVEL : null);
        dialog.setOnShowing(event -> {
            Window window = pane.getScene().getWindow();
            if (window instanceof Stage stage) {
                // Set the stage bounds before its first platform sizing pass. In particular, a
                // tiny screen must never briefly create a window larger than its visual area.
                stage.setMaxWidth(maximumWidth);
                stage.setMaxHeight(maximumHeight);
                stage.setMinWidth(Math.min(MINIMUM_WIDTH, maximumWidth));
                stage.setMinHeight(Math.min(MINIMUM_HEIGHT, maximumHeight));
            }
        });
        dialog.setOnShown(event -> {
            Window window = pane.getScene().getWindow();
            if (window instanceof Stage stage) {
                configureShownStage(pane, stage, visualBounds,
                        maximumWidth, maximumHeight);
            }
        });
        dialog.setOnHidden(event -> preview.dispose());
        return dialog;
    }

    private static void configureShownStage(
            DialogPane pane,
            Stage stage,
            Rectangle2D visualBounds,
            double maximumWidth,
            double maximumHeight) {
        double decorationWidth = Math.max(0, stage.getWidth() - pane.getWidth());
        double decorationHeight = Math.max(0, stage.getHeight() - pane.getHeight());
        double paneMaximumWidth = Math.max(0, maximumWidth - decorationWidth);
        double paneMaximumHeight = Math.max(0, maximumHeight - decorationHeight);
        double paneMinimumWidth = Math.min(MINIMUM_WIDTH, paneMaximumWidth);
        double paneMinimumHeight = Math.min(MINIMUM_HEIGHT, paneMaximumHeight);

        pane.setMinSize(paneMinimumWidth, paneMinimumHeight);
        pane.setPrefSize(Math.min(PREFERRED_WIDTH, paneMaximumWidth),
                Math.min(PREFERRED_HEIGHT, paneMaximumHeight));
        pane.setMaxSize(paneMaximumWidth, paneMaximumHeight);
        stage.setMinWidth(Math.min(maximumWidth, paneMinimumWidth + decorationWidth));
        stage.setMinHeight(Math.min(maximumHeight, paneMinimumHeight + decorationHeight));
        stage.setMaxWidth(maximumWidth);
        stage.setMaxHeight(maximumHeight);
        stage.setWidth(clamp(stage.getWidth(), stage.getMinWidth(), maximumWidth));
        stage.setHeight(clamp(stage.getHeight(), stage.getMinHeight(), maximumHeight));

        // Owned dialogs can otherwise be centered across a screen edge. Keep all decoration inside
        // the same visual screen whose dimensions were used for the 90% size constraint.
        stage.setX(clamp(stage.getX(), visualBounds.getMinX(),
                visualBounds.getMaxX() - stage.getWidth()));
        stage.setY(clamp(stage.getY(), visualBounds.getMinY(),
                visualBounds.getMaxY() - stage.getHeight()));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static Screen screenFor(Window owner) {
        if (owner != null) {
            return Screen.getScreensForRectangle(owner.getX(), owner.getY(),
                    Math.max(1, owner.getWidth()), Math.max(1, owner.getHeight()))
                    .stream().findFirst().orElse(Screen.getPrimary());
        }
        return Screen.getPrimary();
    }
}
