package com.example.game3d.terrain.editor.edit;

import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.editor.state.EditorHistory;
import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LevelEditsTest {
    @Test void addTileWithoutSelectionCanBeClickedThreeTimesAndUndonePerClick() {
        LevelDocument blank = DocumentFactories.blankLevel("level", "gameplay-default");
        StructureDocument inline = DocumentFactories.blankStructure(
                "level.inline", GridMode.ADVANCED);
        EditorHistory history = new EditorHistory(EditorState.unsaved(blank));
        String entryId = "00000000-0000-0000-0000-000000000001";
        String[] tileIds = {
                "00000000-0000-0000-0000-000000000002",
                "00000000-0000-0000-0000-000000000003",
                "00000000-0000-0000-0000-000000000004"
        };

        for (String tileId : tileIds) {
            history.apply(LevelEdits.appendTileToSoleInlineOrCreate(entryId, inline,
                    defaultTile(tileId)));
        }

        LevelDocument created = (LevelDocument) history.state().document();
        assertEquals(1, created.entries().size());
        assertEquals(LevelEntry.Kind.INLINE_STRUCTURE, created.entries().get(0).kind());
        assertEquals(GridMode.ADVANCED, created.entries().get(0).inlineStructure().gridMode());
        assertEquals(List.of(tileIds), created.entries().get(0).inlineStructure().tiles()
                .stream().map(tile -> tile.sourceId()).toList());

        history.undo();
        assertEquals(2, ((LevelDocument) history.state().document()).entries().get(0)
                .inlineStructure().tiles().size());
        history.undo();
        assertEquals(1, ((LevelDocument) history.state().document()).entries().get(0)
                .inlineStructure().tiles().size());
        history.undo();
        assertEquals(0, ((LevelDocument) history.state().document()).entries().size());
        history.redo();
        assertEquals(1, ((LevelDocument) history.state().document()).entries().get(0)
                .inlineStructure().tiles().size());
    }

    @Test void addTileWithoutSelectionUsesAnExistingSoleInlineStructure() {
        LevelDocument level = DocumentFactories.blankLevel("level", "gameplay-default");
        StructureDocument inline = DocumentFactories.blankStructure("chosen", GridMode.BASIC);
        level = (LevelDocument) LevelEdits.add(LevelEntry.inline("entry", inline)).apply(level);

        level = (LevelDocument) LevelEdits.appendTileToSoleInlineOrCreate(
                "unused", DocumentFactories.blankStructure("unused", GridMode.ADVANCED),
                defaultTile("tile")).apply(level);

        assertEquals(1, level.entries().size());
        assertEquals("entry", level.entries().get(0).sourceId());
        assertEquals("chosen", level.entries().get(0).inlineStructure().id());
        assertEquals(GridMode.BASIC, level.entries().get(0).inlineStructure().gridMode());
        assertEquals("tile", level.entries().get(0).inlineStructure().tiles().get(0).sourceId());
    }

    private static TileRecord defaultTile(String sourceId) {
        return new TileRecord(sourceId, true, 0, 0, 0, "NORMAL", 1, 1);
    }

    @Test void inlineStructureCanBeCreatedEditedAndReorderedAsCompoundDocuments() {
        LevelDocument level = DocumentFactories.blankLevel("level", "gameplay-default");
        String firstEntry = UUID.randomUUID().toString();
        String secondEntry = UUID.randomUUID().toString();
        StructureDocument inline = DocumentFactories.blankStructure("inline", GridMode.ADVANCED);
        level = (LevelDocument) LevelEdits.add(LevelEntry.inline(firstEntry, inline)).apply(level);
        level = (LevelDocument) LevelEdits.add(
                LevelEntry.reference(secondEntry, "saved.structure")).apply(level);

        RepeatSpec one = new RepeatSpec(1, 3, 0, 4, 0,
                0, 0, 1, 0, 1, 0);
        level = (LevelDocument) LevelEdits.editInline(firstEntry,
                TileEdits.repeat(0, true, "NORMAL", one,
                        () -> "00000000-0000-0000-0000-000000000001")).apply(level);
        assertEquals(1, level.entries().get(0).inlineStructure().tiles().size());
        assertEquals(3, level.entries().get(0).inlineStructure()
                .tiles().get(0).turnDeltaDegrees());

        level = (LevelDocument) LevelEdits.reorder(
                List.of(secondEntry, firstEntry)).apply(level);
        assertEquals(secondEntry, level.entries().get(0).sourceId());
    }
}
