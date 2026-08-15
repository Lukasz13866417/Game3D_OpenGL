package com.example.game3d.terrain.editor.edit;

import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoricalTileEditTest {
    @Test void multiSelectSolidAndSurfacePreserveEveryNumericAndResolvedField() {
        StructureDocument source = new StructureDocument(1, "categorical",
                GridMode.ADVANCED, Arrays.asList(
                tile("a", true, "NORMAL", 1),
                tile("b", true, "BOOST_RAMP", 2),
                tile("c", true, "NORMAL", 3)), List.of());
        Set<String> selected = new LinkedHashSet<>(Arrays.asList("c", "a"));

        StructureDocument gaps = (StructureDocument) TileEdits
                .setSolid(selected, false).apply(source);
        assertFalse(gaps.tiles().get(0).solid());
        assertTrue(gaps.tiles().get(1).solid());
        assertFalse(gaps.tiles().get(2).solid());
        assertPreserved(source, gaps);

        StructureDocument combined = (StructureDocument) TileEdits
                .setCategorical(selected, false, "LEGACY_BOOST").apply(source);
        assertFalse(combined.tiles().get(0).solid());
        assertTrue(combined.tiles().get(1).solid());
        assertFalse(combined.tiles().get(2).solid());
        assertEquals("LEGACY_BOOST", combined.tiles().get(0).surfaceKind());
        assertEquals("BOOST_RAMP", combined.tiles().get(1).surfaceKind());
        assertEquals("LEGACY_BOOST", combined.tiles().get(2).surfaceKind());
        assertPreserved(source, combined);
    }

    @Test void surfaceRejectsBlankInput() {
        assertThrows(IllegalArgumentException.class,
                () -> TileEdits.setSurface(Set.of("a"), "  "));
    }

    private static TileRecord tile(
            String id, boolean solid, String surface, double base) {
        return new TileRecord(id, solid,
                base + .1, base + .2, base + .3, surface,
                base + .4, base + .5, base + .6, base + .7);
    }

    private static void assertPreserved(
            StructureDocument expected, StructureDocument actual) {
        for (int i = 0; i < expected.tiles().size(); i++) {
            TileRecord before = expected.tiles().get(i);
            TileRecord after = actual.tiles().get(i);
            assertEquals(before.sourceId(), after.sourceId());
            assertEquals(before.turnDeltaDegrees(), after.turnDeltaDegrees());
            assertEquals(before.absoluteSlopeDegrees(), after.absoluteSlopeDegrees());
            assertEquals(before.liftBefore(), after.liftBefore());
            assertEquals(before.alpha(), after.alpha());
            assertEquals(before.brightness(), after.brightness());
            assertEquals(before.resolvedTurnDeltaRadians(),
                    after.resolvedTurnDeltaRadians());
            assertEquals(before.resolvedAbsoluteSlopeRadians(),
                    after.resolvedAbsoluteSlopeRadians());
        }
    }
}
