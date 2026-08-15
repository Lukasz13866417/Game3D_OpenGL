package com.example.game3d.terrain.editor.edit;

import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumericTileEditTest {
    @Test void setAddAndLinearSequenceUseStructureOrder() {
        StructureDocument source = new StructureDocument(1, "numeric", GridMode.ADVANCED,
                Arrays.asList(tile("a", 10), tile("b", 20), tile("c", 30)), java.util.List.of());
        Set<String> selected = new LinkedHashSet<>(Arrays.asList("c", "a"));

        StructureDocument set = (StructureDocument) TileEdits.numeric(selected,
                TileEdits.Field.TURN, TileEdits.Mode.SET, 4, 0).apply(source);
        assertEquals(Arrays.asList(4.0, 20.0, 4.0), turns(set));

        StructureDocument add = (StructureDocument) TileEdits.numeric(selected,
                TileEdits.Field.TURN, TileEdits.Mode.ADD, 2, 0).apply(source);
        assertEquals(Arrays.asList(12.0, 20.0, 32.0), turns(add));

        StructureDocument sequence = (StructureDocument) TileEdits.numeric(selected,
                TileEdits.Field.TURN, TileEdits.Mode.LINEAR_SEQUENCE, 1, 3).apply(source);
        assertEquals(Arrays.asList(1.0, 20.0, 4.0), turns(sequence));
    }

    @Test void requestRejectsNonFiniteAndIrrelevantIncrement() {
        assertThrows(IllegalArgumentException.class, () -> new NumericEditRequest(
                TileEdits.Field.SLOPE, TileEdits.Mode.SET, Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new NumericEditRequest(
                TileEdits.Field.SLOPE, TileEdits.Mode.ADD, 1, 2));
    }

    private static TileRecord tile(String id, double turn) {
        return new TileRecord(id, true, turn, 0, 0, "NORMAL", 1, 1);
    }

    private static java.util.List<Double> turns(StructureDocument value) {
        return value.tiles().stream().map(TileRecord::turnDeltaDegrees).toList();
    }
}
