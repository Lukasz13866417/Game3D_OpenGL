package com.example.game3d.terrain.editor.edit;

import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.io.model.GridMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepeatSpecSafetyTest {
    @Test void rejectsNonFiniteInputBeforeCreatingAnEdit() {
        assertThrows(NumberFormatException.class, () -> new RepeatSpec(
                1, Double.NaN, 0, 0, 0, 0, 0, 1, 0, 1, 0));
        assertThrows(NumberFormatException.class, () -> new RepeatSpec(
                1, 0, 0, 0, 0, 0, 0, 1, 0, Double.POSITIVE_INFINITY, 0));
    }

    @Test void rejectsSequenceOverflowWithoutChangingSource() {
        var source = DocumentFactories.blankStructure("repeat-overflow", GridMode.ADVANCED);
        RepeatSpec spec = new RepeatSpec(
                2, Double.MAX_VALUE, Double.MAX_VALUE,
                0, 0, 0, 0, 1, 0, 1, 0);

        assertThrows(NumberFormatException.class,
                () -> TileEdits.repeat(0, true, "NORMAL", spec).apply(source));
        assertEquals(0, source.tiles().size());
    }
}
