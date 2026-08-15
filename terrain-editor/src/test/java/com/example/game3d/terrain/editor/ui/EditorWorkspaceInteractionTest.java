package com.example.game3d.terrain.editor.ui;

import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.resolve.InMemoryTerrainDocumentRepository;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Exercises editor controls through JavaFX events instead of invoking edit helpers directly. */
@ExtendWith(ApplicationExtension.class)
class EditorWorkspaceInteractionTest {
    private EditorWorkspace workspace;

    @Start
    void start(Stage stage) {
        LevelDocument blank = DocumentFactories.blankLevel(
                "click-test.level", "gameplay-default");
        workspace = new EditorWorkspace(EditorState.unsaved(blank),
                new InMemoryTerrainDocumentRepository(List.of(), List.of()));
        stage.setScene(new Scene(workspace, 1200, 800));
        stage.show();
        stage.toFront();
    }

    @AfterEach
    void closeWorkspace() {
        if (workspace != null) workspace.close();
    }

    @Test
    void addTileButtonCreatesAndKeepsEditingAnInlineStructureInANewLevel(FxRobot robot) {
        robot.clickOn("Add Tile");
        WaitForAsyncUtils.waitForFxEvents();

        // Do not select the newly created level entry. Every click must keep targeting the sole
        // implicit inline structure even when a sequence refresh clears the prior selection.
        robot.clickOn("Add Tile");
        WaitForAsyncUtils.waitForFxEvents();
        robot.clickOn("Add Tile");
        WaitForAsyncUtils.waitForFxEvents();

        LevelDocument level = (LevelDocument) workspace.state().document();
        assertEquals(1, level.entries().size(),
                "Add Tile must make blank levels editable without a prior Add Inline step");
        LevelEntry entry = level.entries().get(0);
        assertEquals(LevelEntry.Kind.INLINE_STRUCTURE, entry.kind());
        assertNotNull(entry.inlineStructure());
        assertEquals(3, entry.inlineStructure().tiles().size());
    }
}
