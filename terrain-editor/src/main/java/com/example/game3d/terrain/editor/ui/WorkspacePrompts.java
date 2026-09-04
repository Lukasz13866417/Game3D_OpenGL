package com.example.game3d.terrain.editor.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

/** Injectable decisions for persistence/reload flows; production uses JavaFX dialogs. */
public interface WorkspacePrompts {
    enum SaveConflictChoice { SAVE_AS, OVERWRITE, CANCEL }
    enum ExternalChangeChoice { RELOAD, KEEP_EDITOR, CANCEL }
    enum CloseChoice { SAVE, DISCARD, CANCEL }

    SaveConflictChoice saveConflict(boolean targetPresent);
    ExternalChangeChoice externalChange();
    CloseChoice closeDirty(String documentId);

    static WorkspacePrompts javaFx() {
        return new WorkspacePrompts() {
            @Override public SaveConflictChoice saveConflict(boolean targetPresent) {
                ButtonType saveAs = new ButtonType(
                        "Save As…", ButtonBar.ButtonData.OTHER);
                ButtonType overwrite = new ButtonType(targetPresent
                        ? "Overwrite This External Version" : "Create File",
                        ButtonBar.ButtonData.YES);
                Alert alert = new Alert(Alert.AlertType.WARNING,
                        "The target changed or already exists. Choose a new file, explicitly "
                                + "replace the exact version currently on disk, or cancel. "
                                + "If it changes again, the editor will ask again.",
                        saveAs, overwrite, ButtonType.CANCEL);
                alert.setTitle("External File Conflict");
                ButtonType answer = alert.showAndWait().orElse(ButtonType.CANCEL);
                return answer == saveAs ? SaveConflictChoice.SAVE_AS
                        : answer == overwrite ? SaveConflictChoice.OVERWRITE
                        : SaveConflictChoice.CANCEL;
            }

            @Override public ExternalChangeChoice externalChange() {
                ButtonType reload = new ButtonType(
                        "Reload from Disk", ButtonBar.ButtonData.YES);
                ButtonType keep = new ButtonType(
                        "Keep Editor Version", ButtonBar.ButtonData.NO);
                Alert alert = new Alert(Alert.AlertType.WARNING,
                        "This file changed outside the editor. Reload discards editor changes; "
                                + "keeping requires a later explicit Save to replace the disk "
                                + "version.", reload, keep, ButtonType.CANCEL);
                alert.setTitle("External File Change");
                ButtonType answer = alert.showAndWait().orElse(ButtonType.CANCEL);
                return answer == reload ? ExternalChangeChoice.RELOAD
                        : answer == keep ? ExternalChangeChoice.KEEP_EDITOR
                        : ExternalChangeChoice.CANCEL;
            }

            @Override public CloseChoice closeDirty(String documentId) {
                ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.YES);
                ButtonType discard = new ButtonType(
                        "Don't Save", ButtonBar.ButtonData.NO);
                ButtonType cancel = new ButtonType(
                        "Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                        "Save changes to " + documentId + "?", save, discard, cancel);
                ButtonType answer = alert.showAndWait().orElse(cancel);
                return answer == save ? CloseChoice.SAVE
                        : answer == discard ? CloseChoice.DISCARD
                        : CloseChoice.CANCEL;
            }
        };
    }
}
