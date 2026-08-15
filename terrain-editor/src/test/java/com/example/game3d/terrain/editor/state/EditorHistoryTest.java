package com.example.game3d.terrain.editor.state;

import com.example.game3d.terrain.editor.edit.RepeatSpec;
import com.example.game3d.terrain.editor.edit.TileEdits;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.store.ContentDigests;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorHistoryTest {
    @Test void repeatExpandsToExplicitTilesAsOneUndoOperation() {
        StructureDocument blank = DocumentFactories.blankStructure("test", GridMode.ADVANCED);
        EditorHistory history = new EditorHistory(EditorState.unsaved(blank));
        AtomicInteger ids = new AtomicInteger();
        RepeatSpec repeat = new RepeatSpec(3, 2, 1.5, 4, -1, .2, .1, 1, -.1, .8, .05);

        history.apply(TileEdits.repeat(0, true, "NORMAL", repeat,
                () -> String.format("00000000-0000-0000-0000-%012d", ids.incrementAndGet())));

        StructureDocument result = (StructureDocument) history.state().document();
        assertEquals(3, result.tiles().size());
        assertEquals(5.0, result.tiles().get(2).turnDeltaDegrees());
        assertEquals(2.0, result.tiles().get(2).absoluteSlopeDegrees());
        assertEquals(.4, result.tiles().get(2).liftBefore(), 1e-12);
        history.undo();
        assertEquals(0, ((StructureDocument) history.state().document()).tiles().size());
        history.redo();
        assertEquals(result.tiles(), ((StructureDocument) history.state().document()).tiles());
    }

    @Test void dirtyStateTracksSavedContentDigestAcrossUndo() {
        TerrainJsonCodec codec = new TerrainJsonCodec();
        StructureDocument blank = DocumentFactories.blankStructure("test", GridMode.ADVANCED);
        EditorState saved = EditorState.unsaved(blank).markSaved(Path.of("test.json"),
                ContentDigests.sha256(codec.encode(blank)));
        EditorHistory history = new EditorHistory(saved);
        assertFalse(history.state().isDirty(codec));

        history.apply(TileEdits.repeat(0, true, "NORMAL",
                new RepeatSpec(1,0,0,0,0,0,0,1,0,1,0),
                () -> "00000000-0000-0000-0000-000000000001"));
        assertTrue(history.state().isDirty(codec));
        history.undo();
        assertFalse(history.state().isDirty(codec));
    }

    @Test void explicitReloadClearsUndoAndRedo() {
        StructureDocument first = DocumentFactories.blankStructure("first", GridMode.ADVANCED);
        StructureDocument reloaded = DocumentFactories.blankStructure("reloaded", GridMode.BASIC);
        EditorHistory history = new EditorHistory(EditorState.unsaved(first));
        history.apply(TileEdits.repeat(0, true, "NORMAL",
                new RepeatSpec(1,0,0,0,0,0,0,1,0,1,0),
                () -> "00000000-0000-0000-0000-000000000001"));
        history.undo();
        assertTrue(history.canRedo());

        history.reset(EditorState.unsaved(reloaded));

        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
        assertEquals("reloaded", history.state().document().id());
    }
}
