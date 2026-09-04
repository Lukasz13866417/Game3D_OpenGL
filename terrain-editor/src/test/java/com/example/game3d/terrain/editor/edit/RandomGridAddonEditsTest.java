package com.example.game3d.terrain.editor.edit;

import com.example.game3d.terrain.editor.state.DocumentFactories;
import com.example.game3d.terrain.editor.state.EditorHistory;
import com.example.game3d.terrain.editor.state.EditorState;
import com.example.game3d.terrain.io.TerrainJsonCodec;
import com.example.game3d.terrain.io.model.AddonKind;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import com.example.game3d.terrain.io.validation.TerrainContentLimits;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomGridAddonEditsTest {
    @Test void sameSeedProducesSameConcreteCellsAndStableIds() {
        StructureDocument source = straight(GridMode.ADVANCED, 8);

        StructureDocument first = RandomGridAddonEdits.expand(
                source, 7812L, 7, AddonKind.DEATH_SPIKE);
        StructureDocument same = RandomGridAddonEdits.expand(
                source, 7812L, 7, AddonKind.DEATH_SPIKE);

        assertEquals(signature(first), signature(same));
        assertEquals(7, first.addons().size());
        for (AddonReservation addon : first.addons()) {
            assertEquals(Placement.Mode.GRID, addon.placement().mode());
            assertEquals(addon.placement().rowStart(), addon.placement().rowEnd());
            assertEquals(addon.placement().columnStart(), addon.placement().columnEnd());
            assertTrue(addon.parameters().isEmpty());
            assertEquals(null, addon.pairSourceId());
        }
    }

    @Test void selectionUsesExistingOccupancyAndHasNoDuplicateCells() {
        StructureDocument source = straight(GridMode.ADVANCED, 7);
        AddonReservation occupied = new AddonReservation(
                "00000000-0000-0000-0000-000000000099",
                AddonKind.AIR_JUMP_POTION, Placement.grid(2, 3, 2, 3),
                null, Collections.emptyMap());
        source = source.withAddons(List.of(occupied));

        StructureDocument expanded = RandomGridAddonEdits.expand(
                source, -44L, 12, AddonKind.AIR_JUMP_POTION);
        Set<String> cells = new HashSet<>();
        for (int i = 1; i < expanded.addons().size(); i++) {
            Placement placement = expanded.addons().get(i).placement();
            String cell = placement.rowStart() + ":" + placement.columnStart();
            assertTrue(cells.add(cell), "random placements must be unique");
            assertFalse(placement.rowStart() >= 2 && placement.rowStart() <= 3
                    && placement.columnStart() >= 2 && placement.columnStart() <= 3,
                    "existing region occupancy must be respected");
        }
    }

    @Test void differentSeedsCanChooseDifferentCells() {
        StructureDocument source = straight(GridMode.ADVANCED, 9);
        Set<String> layouts = new HashSet<>();
        for (long seed = 10; seed < 20; seed++) {
            layouts.add(signature(RandomGridAddonEdits.expand(
                    source, seed, 5, AddonKind.DEATH_SPIKE)));
        }
        assertTrue(layouts.size() > 1);
    }

    @Test void resultIsExplicitJsonWithNoPersistentRandomNode() {
        StructureDocument expanded = RandomGridAddonEdits.expand(
                straight(GridMode.ADVANCED, 5), 998877L, 4,
                AddonKind.AIR_JUMP_POTION);
        String json = new TerrainJsonCodec().encode(expanded);

        assertFalse(json.contains("998877"));
        assertFalse(json.contains("randomSeed"));
        assertFalse(json.contains("randomPlacement"));
        assertFalse(json.contains("seed"));
        assertEquals(4, count(json, "\"mode\": \"GRID\""));
        assertEquals(signature(expanded), signature(
                decode(new TerrainJsonCodec(), json)));
    }

    @Test void expansionIsOneCompoundUndoOperation() {
        StructureDocument source = straight(GridMode.ADVANCED, 6);
        EditorHistory history = new EditorHistory(EditorState.unsaved(source));
        history.apply(RandomGridAddonEdits.add(
                7L, 6, AddonKind.DEATH_SPIKE));
        assertEquals(6, ((StructureDocument) history.state().document()).addons().size());

        history.undo();
        assertEquals(0, ((StructureDocument) history.state().document()).addons().size());
        history.redo();
        assertEquals(6, ((StructureDocument) history.state().document()).addons().size());
    }

    @Test void inlineExpansionIsOneCompoundLevelUndoOperation() {
        StructureDocument inline = straight(GridMode.ADVANCED, 6);
        LevelDocument level = new LevelDocument(1, "level",
                "gameplay-default-v1", List.of(
                LevelEntry.inline("inline-entry", inline)));
        EditorHistory history = new EditorHistory(EditorState.unsaved(level));

        history.apply(LevelEdits.editInline("inline-entry",
                RandomGridAddonEdits.add(15L, 4, AddonKind.AIR_JUMP_POTION)));
        LevelDocument expanded = (LevelDocument) history.state().document();
        assertEquals(4, expanded.entries().get(0).inlineStructure().addons().size());

        history.undo();
        assertEquals(0, ((LevelDocument) history.state().document()).entries()
                .get(0).inlineStructure().addons().size());
    }

    @Test void rejectsBasicPortalsOverCapacityAndTerrainWithoutFreeCells() {
        assertThrows(IllegalArgumentException.class, () -> RandomGridAddonEdits.expand(
                straight(GridMode.BASIC, 3), 1L, 1, AddonKind.DEATH_SPIKE));
        assertThrows(IllegalArgumentException.class, () -> RandomGridAddonEdits.expand(
                straight(GridMode.ADVANCED, 3), 1L, 1,
                AddonKind.PORTAL_ENTRANCE));
        assertThrows(IllegalArgumentException.class, () -> RandomGridAddonEdits.expand(
                straight(GridMode.ADVANCED, 3), 1L, 0,
                AddonKind.DEATH_SPIKE));
        assertThrows(IllegalArgumentException.class, () -> RandomGridAddonEdits.expand(
                straight(GridMode.ADVANCED, 3), 1L,
                TerrainContentLimits.MAX_STRUCTURE_ADDONS + 1,
                AddonKind.DEATH_SPIKE));

        StructureDocument gapsOnly = straight(GridMode.ADVANCED, 3).withTiles(List.of(
                tile("00000000-0000-0000-0000-000000000101", false),
                tile("00000000-0000-0000-0000-000000000102", false),
                tile("00000000-0000-0000-0000-000000000103", false)));
        assertThrows(IllegalStateException.class, () -> RandomGridAddonEdits.expand(
                gapsOnly, 1L, 1, AddonKind.DEATH_SPIKE));

        StructureDocument oneRow = straight(GridMode.ADVANCED, 1);
        int rowCount = AddonPlacementRequest.physicalGridRowCount(oneRow);
        List<AddonReservation> full = new ArrayList<>();
        for (int column = 1; column <= 6; column++) {
            full.add(new AddonReservation("occupied-" + column,
                    AddonKind.DEATH_SPIKE,
                    Placement.grid(1, rowCount, column, column), null,
                    Collections.emptyMap()));
        }
        StructureDocument noCapacity = oneRow.withAddons(full);
        assertThrows(IllegalStateException.class, () -> RandomGridAddonEdits.expand(
                noCapacity, 1L, 1, AddonKind.AIR_JUMP_POTION));
    }

    @Test void capacityAdditionCannotOverflowOrMutateHistory() {
        StructureDocument base = straight(GridMode.ADVANCED, 1);
        AddonReservation existing = new AddonReservation(
                "00000000-0000-0000-0000-000000000099",
                AddonKind.DEATH_SPIKE, Placement.grid(1, 1, 1, 1), null, Map.of());
        StructureDocument source = base.withAddons(List.of(existing));
        EditorHistory history = new EditorHistory(EditorState.unsaved(source));

        assertThrows(IllegalArgumentException.class, () -> history.apply(
                RandomGridAddonEdits.add(
                        1L, Integer.MAX_VALUE, AddonKind.DEATH_SPIKE)));

        assertSame(source, history.state().document());
        assertEquals(0L, history.state().revision());
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
    }

    private static StructureDocument straight(GridMode mode, int count) {
        List<TileRecord> tiles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tiles.add(tile(String.format(
                    "00000000-0000-0000-0000-%012d", i + 1), true));
        }
        StructureDocument blank = DocumentFactories.blankStructure("random-grid", mode);
        return blank.withTiles(tiles);
    }

    private static TileRecord tile(String sourceId, boolean solid) {
        return new TileRecord(sourceId, solid, 0, 0, 0, "NORMAL", 1, 1);
    }

    private static StructureDocument decode(TerrainJsonCodec codec, String json) {
        try {
            return codec.decodeStructure(json);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static String signature(StructureDocument structure) {
        StringBuilder result = new StringBuilder();
        for (AddonReservation addon : structure.addons()) {
            Placement placement = addon.placement();
            result.append(addon.sourceId()).append('|').append(addon.kind())
                    .append('|').append(placement.mode())
                    .append('|').append(placement.rowStart())
                    .append('|').append(placement.rowEnd())
                    .append('|').append(placement.columnStart())
                    .append('|').append(placement.columnEnd()).append(';');
        }
        return result.toString();
    }

    private static int count(String text, String needle) {
        int result = 0;
        int at = 0;
        while ((at = text.indexOf(needle, at)) >= 0) {
            result++;
            at += needle.length();
        }
        return result;
    }
}
