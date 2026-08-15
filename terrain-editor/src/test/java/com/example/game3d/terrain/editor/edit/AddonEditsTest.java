package com.example.game3d.terrain.editor.edit;

import com.example.game3d.terrain.io.model.AddonKind;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddonEditsTest {
    @Test void replacePlacementPreservesPortalPairAndParameters() {
        Map<String, Double> parameters = new LinkedHashMap<>();
        parameters.put("width", 1.5);
        AddonReservation portal = new AddonReservation("entrance",
                AddonKind.PORTAL_ENTRANCE, Placement.grid(1, 1, 1, 2),
                "exit", parameters);
        AddonReservation exit = new AddonReservation("exit",
                AddonKind.PORTAL_EXIT, Placement.grid(2, 2, 4, 5),
                "entrance", Map.of());
        StructureDocument source = new StructureDocument(1, "portals",
                GridMode.ADVANCED, List.of(), List.of(portal, exit));

        Placement moved = Placement.normalized("tile-a", .25, .75);
        StructureDocument result = (StructureDocument) AddonEdits
                .replacePlacement("entrance", moved).apply(source);

        AddonReservation updated = result.addons().get(0);
        assertEquals("entrance", updated.sourceId());
        assertEquals(AddonKind.PORTAL_ENTRANCE, updated.kind());
        assertEquals("exit", updated.pairSourceId());
        assertEquals(parameters, updated.parameters());
        assertEquals(moved, updated.placement());
        assertEquals(exit, result.addons().get(1));
    }

    @Test void replacePlacementRejectsUnknownAddon() {
        StructureDocument source = new StructureDocument(1, "empty",
                GridMode.ADVANCED, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> AddonEdits
                .replacePlacement("missing", Placement.grid(1, 1, 1, 1))
                .apply(source));
    }
}
