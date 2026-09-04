package com.example.game3d.terrain.editor.edit;

import com.example.game3d.terrain.editor.state.DocumentEdit;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public final class AddonEdits {
    private AddonEdits() {}

    public static DocumentEdit add(AddonReservation reservation) {
        requireFinite(reservation);
        return document -> {
            if (!(document instanceof StructureDocument structure))
                throw new IllegalArgumentException("Addon operation requires a structure");
            ArrayList<AddonReservation> addons = new ArrayList<>(structure.addons());
            addons.add(reservation);
            return structure.withAddons(addons);
        };
    }

    public static DocumentEdit delete(Set<String> sourceIds) {
        return document -> {
            if (!(document instanceof StructureDocument structure))
                throw new IllegalArgumentException("Addon operation requires a structure");
            ArrayList<AddonReservation> addons = new ArrayList<>();
            for (AddonReservation addon : structure.addons())
                if (!sourceIds.contains(addon.sourceId())) addons.add(addon);
            return structure.withAddons(addons);
        };
    }

    /** Replaces only placement, retaining kind, parameters, ID, and portal pair metadata. */
    public static DocumentEdit replacePlacement(String sourceId, Placement placement) {
        if (sourceId == null || sourceId.isEmpty() || placement == null) {
            throw new IllegalArgumentException("sourceId and placement are required");
        }
        requireFinite(placement);
        return document -> {
            if (!(document instanceof StructureDocument structure)) {
                throw new IllegalArgumentException(
                        "Addon operation requires a structure");
            }
            ArrayList<AddonReservation> addons = new ArrayList<>();
            boolean found = false;
            for (AddonReservation addon : structure.addons()) {
                if (!sourceId.equals(addon.sourceId())) {
                    addons.add(addon);
                    continue;
                }
                requireFinite(addon);
                addons.add(new AddonReservation(addon.sourceId(), addon.kind(),
                        placement, addon.pairSourceId(), addon.parameters()));
                found = true;
            }
            if (!found) {
                throw new IllegalArgumentException("Unknown addon " + sourceId);
            }
            return structure.withAddons(addons);
        };
    }

    private static void requireFinite(AddonReservation reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("reservation is required");
        }
        requireFinite(reservation.placement());
        for (Map.Entry<String, Double> parameter : reservation.parameters().entrySet()) {
            Double value = parameter.getValue();
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Addon parameter '" + parameter.getKey()
                        + "' must be finite");
            }
        }
    }

    private static void requireFinite(Placement placement) {
        if (placement.mode() == Placement.Mode.SEGMENT_NORMALIZED
                && (!Double.isFinite(placement.across())
                || !Double.isFinite(placement.along()))) {
            throw new IllegalArgumentException(
                    "Normalized placement coordinates must be finite");
        }
    }
}
