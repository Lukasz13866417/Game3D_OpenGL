package com.example.game3d.terrain.editor.edit;

import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AddonPlacementRequestTest {
    private static final String FIRST = "00000000-0000-0000-0000-000000000001";
    private static final String SECOND = "00000000-0000-0000-0000-000000000002";

    @Test void gridRangesAndNormalizedOwnerRowsBecomeCanonicalPlacements() {
        StructureDocument structure = structure(true);
        Placement grid = AddonPlacementRequest.grid(1, 2, 2, 5).toPlacement(structure);
        assertEquals(1, grid.rowStart());
        assertEquals(2, grid.rowEnd());
        assertEquals(5, grid.columnEnd());

        Placement normalized = AddonPlacementRequest.normalized(
                SECOND, .25, .75).toPlacement(structure);
        assertEquals(SECOND, normalized.segmentSourceId());
        assertEquals(.25, normalized.across());
    }

    @Test void normalizedPlacementCannotOwnAGapButPhysicalGridRowsUseGeometry() {
        StructureDocument structure = structure(false);
        assertEquals(1, AddonPlacementRequest.physicalGridRowCount(structure));
        Placement grid = AddonPlacementRequest.grid(1, 1, 1, 1)
                .toPlacement(structure);
        assertEquals(1, grid.rowEnd());
        IllegalArgumentException outOfRange = assertThrows(IllegalArgumentException.class,
                () -> AddonPlacementRequest.grid(2, 2, 1, 1)
                        .toPlacement(structure));
        assertEquals("Grid row range [2, 2] exceeds derived physical row count 1",
                outOfRange.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> AddonPlacementRequest.normalized(SECOND, .5, .5)
                        .toPlacement(structure));
    }

    private static StructureDocument structure(boolean secondSolid) {
        return new StructureDocument(1, "structure", GridMode.ADVANCED,
                List.of(tile(FIRST, true), tile(SECOND, secondSolid)), List.of());
    }

    private static TileRecord tile(String id, boolean solid) {
        return new TileRecord(id, solid, 0, 0, 0, "NORMAL", 1, 1);
    }
}
