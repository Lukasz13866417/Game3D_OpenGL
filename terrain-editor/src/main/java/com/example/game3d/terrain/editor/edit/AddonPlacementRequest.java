package com.example.game3d.terrain.editor.edit;

import com.example.game3d.authoring.TerrainMaterializer;
import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.terrain.io.authoring.DataBackedStructureFactory;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;

import java.util.Collections;

/** Validated, UI-independent input for either supported addon placement mode. */
public record AddonPlacementRequest(
        Placement.Mode mode,
        int rowStart,
        int rowEnd,
        int columnStart,
        int columnEnd,
        String segmentSourceId,
        double across,
        double along) {

    public static AddonPlacementRequest grid(
            int rowStart, int rowEnd, int columnStart, int columnEnd) {
        return new AddonPlacementRequest(Placement.Mode.GRID,
                rowStart, rowEnd, columnStart, columnEnd, null, 0.0, 0.0);
    }

    public static AddonPlacementRequest normalized(
            String segmentSourceId, double across, double along) {
        return new AddonPlacementRequest(Placement.Mode.SEGMENT_NORMALIZED,
                0, 0, 0, 0, segmentSourceId, across, along);
    }

    public Placement toPlacement(StructureDocument structure) {
        if (structure == null || mode == null) {
            throw new IllegalArgumentException("structure and placement mode are required");
        }
        if (mode == Placement.Mode.GRID) {
            if (rowStart < 1 || rowEnd < rowStart
                    || columnStart < 1 || columnEnd < columnStart
                    || columnEnd > TrackProfile.gameplayDefault().gridColumns) {
                throw new IllegalArgumentException("Grid range is outside the structure");
            }
            int rowCount = physicalGridRowCount(structure);
            if (rowEnd > rowCount) {
                throw new IllegalArgumentException("Grid row range [" + rowStart + ", "
                        + rowEnd + "] exceeds derived physical row count " + rowCount);
            }
            return Placement.grid(rowStart, rowEnd, columnStart, columnEnd);
        }
        TileRecord owner = null;
        for (TileRecord tile : structure.tiles()) {
            if (tile.sourceId().equals(segmentSourceId)) {
                owner = tile;
                break;
            }
        }
        if (owner == null) {
            throw new IllegalArgumentException("Selected segment does not exist");
        }
        if (!owner.solid()) {
            throw new IllegalArgumentException("An addon cannot be placed on a gap tile");
        }
        if (!Double.isFinite(across) || !Double.isFinite(along)
                || across < 0.0 || across > 1.0 || along < 0.0 || along > 1.0) {
            throw new IllegalArgumentException("Normalized coordinates must be in [0, 1]");
        }
        return Placement.normalized(segmentSourceId, across, along);
    }

    /** Exact GRID row count used by both the request validator and the editor spinner. */
    public static int physicalGridRowCount(StructureDocument structure) {
        if (structure == null) {
            throw new IllegalArgumentException("structure is required");
        }
        // Existing addon errors must not prevent the editor from deriving geometry bounds for
        // a new placement. GRID rows depend only on the completed tile geometry and profile.
        StructureDocument geometryOnly = structure.withAddons(Collections.emptyList());
        return TerrainMaterializer.derivePhysicalGridRowCount(
                DataBackedStructureFactory.create(geometryOnly),
                TrackProfile.gameplayDefault(), Vec3.ZERO, 0L);
    }
}
