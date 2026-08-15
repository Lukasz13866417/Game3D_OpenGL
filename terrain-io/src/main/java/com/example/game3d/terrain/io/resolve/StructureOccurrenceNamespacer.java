package com.example.game3d.terrain.io.resolve;

import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;

import java.util.ArrayList;
import java.util.List;

/** Rewrites local source references for one repeated level-entry occurrence. */
public final class StructureOccurrenceNamespacer {
    private StructureOccurrenceNamespacer() {
    }

    public static StructureDocument namespace(ResolvedStructureOccurrence occurrence) {
        if (occurrence == null) {
            throw new IllegalArgumentException("occurrence == null");
        }
        StructureDocument source = occurrence.structure();
        List<TileRecord> tiles = new ArrayList<TileRecord>(source.tiles().size());
        for (TileRecord tile : source.tiles()) {
            tiles.add(new TileRecord(
                    occurrence.namespacedSourceId(tile.sourceId()), tile.solid(),
                    tile.turnDeltaDegrees(), tile.absoluteSlopeDegrees(), tile.liftBefore(),
                    tile.surfaceKind(), tile.alpha(), tile.brightness(),
                    tile.resolvedTurnDeltaRadians(), tile.resolvedAbsoluteSlopeRadians()));
        }
        List<AddonReservation> addons =
                new ArrayList<AddonReservation>(source.addons().size());
        for (AddonReservation addon : source.addons()) {
            Placement placement = addon.placement();
            Placement rewritten = placement.mode() == Placement.Mode.GRID
                    ? Placement.grid(placement.rowStart(), placement.rowEnd(),
                    placement.columnStart(), placement.columnEnd())
                    : Placement.normalized(
                    occurrence.namespacedSourceId(placement.segmentSourceId()),
                    placement.across(), placement.along());
            addons.add(new AddonReservation(
                    occurrence.namespacedSourceId(addon.sourceId()), addon.kind(), rewritten,
                    addon.pairSourceId() == null ? null
                            : occurrence.namespacedSourceId(addon.pairSourceId()),
                    addon.parameters()));
        }
        return new StructureDocument(source.formatVersion(), source.id(),
                source.gridMode(), tiles, addons);
    }
}
