package com.example.game3d.terrain.editor.ui;

import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.editor.compile.DebouncedCompiler;
import com.example.game3d.terrain.editor.persistence.EditorPersistence;
import com.example.game3d.terrain.editor.persistence.RecoveryService;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.resolve.InMemoryTerrainDocumentRepository;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises editor controls through JavaFX events instead of invoking edit helpers directly. */
@ExtendWith(ApplicationExtension.class)
class EditorWorkspaceInteractionTest {
    @TempDir Path temporaryState;
    private EditorWorkspace workspace;

    @Start
    void start(Stage stage) {
        LevelDocument blank = DocumentFactories.blankLevel(
                "click-test.level", "gameplay-default");
        TerrainJsonCodec codec = new TerrainJsonCodec();
        EditorWorkspaceDependencies dependencies = new EditorWorkspaceDependencies(
                codec,
                new EditorPersistence(codec),
                listener -> new RecoveryService(
                        codec, temporaryState.resolve("recovery"), Duration.ofMillis(20)),
                compiler -> new DebouncedCompiler(
                        compiler, Duration.ZERO, javafx.application.Platform::runLater),
                EditorWorkspaceDependencies.LayoutPreferences.NONE,
                cancelingPrompts(),
                temporaryState.resolve("recovery"));
        workspace = new EditorWorkspace(EditorState.unsaved(blank),
                new InMemoryTerrainDocumentRepository(List.of(), List.of()), dependencies);
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

    @Test
    void saveAsRefreshesProjectStatusWhenCrossingTheContentBoundary(FxRobot robot)
            throws Exception {
        Path contentRoot = Files.createDirectories(
                temporaryState.resolve("project/terrain-content"));
        Path levels = Files.createDirectories(contentRoot.resolve("levels"));
        robot.interact(() -> workspace.setProjectContentRoot(contentRoot));
        assertEquals("Standalone", workspace.projectStatusForTesting());

        Path projectLevel = levels.resolve("click-test.terrain-level.json");
        robot.interact(() -> save(workspace, projectLevel));
        assertEquals("Project level", workspace.projectStatusForTesting());

        Path standalone = temporaryState.resolve("standalone.terrain-level.json");
        robot.interact(() -> save(workspace, standalone));
        assertEquals("Standalone", workspace.projectStatusForTesting());
    }

    private static void save(EditorWorkspace workspace, Path target) {
        try {
            assertTrue(workspace.save(target));
        } catch (IOException failure) {
            throw new AssertionError("Could not save test document", failure);
        }
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
}
