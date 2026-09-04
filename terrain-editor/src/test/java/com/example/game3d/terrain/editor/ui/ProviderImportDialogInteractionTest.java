package com.example.game3d.terrain.editor.ui;

import com.example.game3d.core.terrain.TerrainSnapshot;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class ProviderImportDialogInteractionTest {
    private static final double EPSILON = 1.0e-6;
    private static final List<String> ACTIONS = List.of(
            "Import as JSON Structure", "Import as Inline JSON Level", "Close");

    private Stage owner;

    @Start
    void start(Stage stage) {
        owner = stage;
        stage.setScene(new Scene(new StackPane(), 900, 600));
        stage.show();
        stage.toFront();
    }

    @Test
    void actionsStayVisibleAndUsableAtOrdinaryAndMinimumScreenSizes(FxRobot robot) {
        List<Rectangle2D> screens = List.of(
                new Rectangle2D(0, 0, 1200, 800),
                new Rectangle2D(0, 0, 900, 600));
        for (Rectangle2D screen : screens) {
            verifyAction(robot, screen, ACTIONS.get(0),
                    ProviderImportDialog.Action.IMPORT_STRUCTURE);
            verifyAction(robot, screen, ACTIONS.get(1),
                    ProviderImportDialog.Action.IMPORT_INLINE_LEVEL);
            verifyAction(robot, screen, ACTIONS.get(2), null);
        }
    }

    private void verifyAction(
            FxRobot robot,
            Rectangle2D screen,
            String actionText,
            ProviderImportDialog.Action expectedResult) {
        AtomicReference<Dialog<ProviderImportDialog.Action>> created = new AtomicReference<>();
        robot.interact(() -> created.set(ProviderImportDialog.createDialog(
                owner, "Provider preview", TerrainSnapshot.empty(), screen)));
        Dialog<ProviderImportDialog.Action> dialog = created.get();
        robot.interact(dialog::show);
        WaitForAsyncUtils.waitForFxEvents();

        Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
        double maximumWidth = screen.getWidth() * .90;
        double maximumHeight = screen.getHeight() * .90;
        assertEquals(maximumWidth, dialogStage.getMaxWidth(), EPSILON);
        assertEquals(maximumHeight, dialogStage.getMaxHeight(), EPSILON);
        assertTrue(dialogStage.getWidth() <= maximumWidth + EPSILON);
        assertTrue(dialogStage.getHeight() <= maximumHeight + EPSILON);
        double decorationWidth = Math.max(0,
                dialogStage.getWidth() - dialog.getDialogPane().getWidth());
        double decorationHeight = Math.max(0,
                dialogStage.getHeight() - dialog.getDialogPane().getHeight());
        assertTrue(dialogStage.getMinWidth() <= dialogStage.getMaxWidth());
        assertTrue(dialogStage.getMinHeight() <= dialogStage.getMaxHeight());
        assertTrue(dialog.getDialogPane().getMinWidth()
                <= dialog.getDialogPane().getPrefWidth());
        assertTrue(dialog.getDialogPane().getPrefWidth()
                <= dialog.getDialogPane().getMaxWidth());
        assertTrue(dialog.getDialogPane().getMinHeight()
                <= dialog.getDialogPane().getPrefHeight());
        assertTrue(dialog.getDialogPane().getPrefHeight()
                <= dialog.getDialogPane().getMaxHeight());
        assertTrue(dialog.getDialogPane().getMaxWidth() + decorationWidth
                <= maximumWidth + EPSILON);
        assertTrue(dialog.getDialogPane().getMaxHeight() + decorationHeight
                <= maximumHeight + EPSILON);
        assertInsideScreen(dialogStage, screen);

        // Exercise both ends of the resize range. The viewport is allowed to shrink, while the
        // DialogPane-owned action bar must remain laid out and clickable.
        resize(robot, dialogStage, maximumWidth, maximumHeight);
        assertAllActionsVisible(dialog);
        resize(robot, dialogStage, dialogStage.getMinWidth(), dialogStage.getMinHeight());
        assertAllActionsVisible(dialog);

        Button action = button(dialog, actionText);
        robot.clickOn(action);
        WaitForAsyncUtils.waitForFxEvents();

        assertFalse(dialog.isShowing());
        if (expectedResult == null) assertNull(dialog.getResult());
        else assertEquals(expectedResult, dialog.getResult());
    }

    private static void resize(FxRobot robot, Stage stage, double width, double height) {
        robot.interact(() -> {
            stage.setWidth(width);
            stage.setHeight(height);
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(stage.getWidth() <= stage.getMaxWidth() + EPSILON);
        assertTrue(stage.getHeight() <= stage.getMaxHeight() + EPSILON);
    }

    private static void assertAllActionsVisible(Dialog<?> dialog) {
        Bounds paneBounds = dialog.getDialogPane().localToScreen(
                dialog.getDialogPane().getBoundsInLocal());
        assertNotNull(paneBounds);
        for (String text : ACTIONS) {
            Button button = button(dialog, text);
            Bounds buttonBounds = button.localToScreen(button.getBoundsInLocal());
            assertTrue(button.isVisible() && button.isManaged() && !button.isDisabled(),
                    text + " must remain an available dialog action");
            assertNotNull(buttonBounds, text + " must be part of the visible scene");
            assertTrue(buttonBounds.getMinX() >= paneBounds.getMinX() - EPSILON
                            && buttonBounds.getMaxX() <= paneBounds.getMaxX() + EPSILON
                            && buttonBounds.getMinY() >= paneBounds.getMinY() - EPSILON
                            && buttonBounds.getMaxY() <= paneBounds.getMaxY() + EPSILON,
                    text + " must not be clipped outside the dialog pane");
        }
    }

    private static void assertInsideScreen(Stage stage, Rectangle2D screen) {
        assertTrue(stage.getX() >= screen.getMinX() - EPSILON);
        assertTrue(stage.getY() >= screen.getMinY() - EPSILON);
        assertTrue(stage.getX() + stage.getWidth() <= screen.getMaxX() + EPSILON);
        assertTrue(stage.getY() + stage.getHeight() <= screen.getMaxY() + EPSILON);
    }

    private static Button button(Dialog<?> dialog, String text) {
        ButtonType type = dialog.getDialogPane().getButtonTypes().stream()
                .filter(candidate -> candidate.getText().equals(text))
                .findFirst().orElseThrow();
        return (Button) dialog.getDialogPane().lookupButton(type);
    }
}
