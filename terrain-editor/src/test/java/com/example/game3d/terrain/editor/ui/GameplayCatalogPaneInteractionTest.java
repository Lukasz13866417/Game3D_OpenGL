package com.example.game3d.terrain.editor.ui;

import com.example.game3d.terrain.editor.persistence.ProjectTerrainDocumentRepository;
import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.catalog.CatalogDocumentEdits;
import com.example.game3d.terrain.io.catalog.GameplayCatalogPolicy;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.CatalogEntry;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ApplicationExtension.class)
class GameplayCatalogPaneInteractionTest {
    @TempDir Path projectRoot;

    private GameplayCatalogPane pane;
    private ProjectTerrainDocumentRepository repository;
    private AtomicInteger changes;

    @Start
    void start(Stage stage) throws Exception {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        Path levels = Files.createDirectories(
                projectRoot.resolve("terrain-content").resolve("levels"));
        Files.writeString(levels.resolve("alpha.terrain-level.json"), codec.encode(
                DocumentFactories.blankLevel("level.alpha", "gameplay-default")));
        Files.writeString(levels.resolve("beta.terrain-level.json"), codec.encode(
                DocumentFactories.blankLevel("level.beta", "gameplay-default")));

        repository = new ProjectTerrainDocumentRepository(codec);
        repository.reload(projectRoot);
        changes = new AtomicInteger();
        pane = new GameplayCatalogPane(
                CatalogDocumentEdits.newGameplayCatalog("gameplay"),
                repository.snapshotView(),
                ignored -> changes.incrementAndGet(),
                () -> { },
                () -> { },
                () -> { },
                ignored -> { });
        // Keep the window inside Monocle's fixed headless framebuffer as well as exercising the
        // application's documented minimum desktop size.
        stage.setScene(new Scene(pane, 900, 600));
        stage.show();
        stage.toFront();
    }

    @Test
    void builtInPrefixCannotBeDisabledThroughThePane(FxRobot robot) {
        int builtInCount = GameplayCatalogPolicy.customEntryStartIndex();
        TableView<?> table = robot.lookup(".table-view").queryAs(TableView.class);
        assertEquals(builtInCount, table.getItems().size());
        for (int index = 0; index < builtInCount; index++) {
            CatalogEntry entry = pane.catalog().entries().get(index);
            assertEquals(GameplayCatalogPolicy.requiredBuiltinIds().get(index), entry.id());
            assertEquals(CatalogEntry.Kind.JAVA_PROVIDER, entry.kind());
            assertTrue(entry.enabled());
        }

        robot.interact(() -> table.getSelectionModel().select(0));
        robot.clickOn("Enable / Disable");
        WaitForAsyncUtils.waitForFxEvents();
        assertFalse(robot.lookup("Built-in gameplay entries are locked.")
                .tryQuery().isEmpty());
        robot.clickOn("OK");

        assertEquals(0, changes.get());
        assertTrue(pane.catalog().entries().get(0).enabled());
    }

    @Test
    void customRowsCanBeAddedAndReorderedWithoutMovingTheBuiltIns(FxRobot robot) {
        robot.interact(() -> {
            pane.addLevelId("level.alpha");
            pane.addLevelId("level.beta");
        });
        int firstCustom = GameplayCatalogPolicy.customEntryStartIndex();
        TableView<?> table = robot.lookup(".table-view").queryAs(TableView.class);
        robot.interact(() -> table.getSelectionModel().select(firstCustom + 1));
        robot.clickOn("Move Up");
        WaitForAsyncUtils.waitForFxEvents();

        CatalogDocument result = pane.catalog();
        assertEquals("level.beta", result.entries().get(firstCustom).id());
        assertEquals("level.alpha", result.entries().get(firstCustom + 1).id());
        assertEquals(GameplayCatalogPolicy.requiredBuiltinIds(),
                result.entries().subList(0, firstCustom).stream()
                        .map(CatalogEntry::id).toList());
        assertEquals(3, changes.get());
    }

    @Test
    void invalidCatalogDisablesBothPublishActionsButStillAllowsSaving(FxRobot robot) {
        CatalogDocument invalid = CatalogDocumentEdits.addJsonLevel(
                CatalogDocumentEdits.newGameplayCatalog("gameplay"),
                "missing.entry", "missing.level", true);
        robot.interact(() -> pane.setCatalog(invalid, repository.snapshotView()));

        assertFalse(pane.sourceValid());
        assertTrue(button(robot, "Publish").isDisabled());
        assertTrue(button(robot, "Publish & Simulate Selected").isDisabled());
        assertFalse(button(robot, "Save Catalog").isDisabled());
        assertTrue(robot.lookup("1 catalog error(s); publishing is blocked")
                .tryQuery().isPresent());
    }

    @Test
    void savedLevelDialogEnumeratesOnlyCanonicalProjectLevelIds(FxRobot robot) {
        robot.clickOn("Add Saved Level…");
        WaitForAsyncUtils.waitForFxEvents();

        @SuppressWarnings("unchecked")
        ComboBox<String> choices = (ComboBox<String>) robot.lookup(".combo-box")
                .queryAs(ComboBox.class);
        List<String> ids = new ArrayList<>(choices.getItems());
        assertEquals(List.of("level.alpha", "level.beta"), ids);

        robot.interact(() -> choices.getSelectionModel().select("level.beta"));
        robot.clickOn("OK");
        WaitForAsyncUtils.waitForFxEvents();

        int firstCustom = GameplayCatalogPolicy.customEntryStartIndex();
        CatalogEntry added = pane.catalog().entries().get(firstCustom);
        assertEquals("level.beta", added.id());
        assertEquals("level.beta", added.location());
        assertEquals(1, changes.get());
    }

    private static Button button(FxRobot robot, String text) {
        return robot.lookup(".button").queryAllAs(Button.class).stream()
                .filter(value -> text.equals(value.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing button: " + text));
    }
}
