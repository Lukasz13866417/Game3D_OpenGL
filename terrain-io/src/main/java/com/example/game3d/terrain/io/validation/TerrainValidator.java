package com.example.game3d.terrain.io.validation;

import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.terrain.io.model.AddonKind;
import com.example.game3d.terrain.io.model.AddonParameterNames;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.CatalogEntry;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;
import com.example.game3d.terrain.io.model.TileRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Semantic validation used by previews and publishing, never by Save. */
public final class TerrainValidator {
    public ValidationResult validate(TerrainSourceDocument document) {
        List<ValidationProblem> out = new ArrayList<>();
        checkVersion(document.formatVersion(), "$", out);
        checkDocumentId(document.id(), "$.id", out);
        if (document instanceof StructureDocument) validateStructure((StructureDocument) document, "$", out);
        else if (document instanceof LevelDocument) validateLevel((LevelDocument) document, "$", out);
        else if (document instanceof CatalogDocument) validateCatalog((CatalogDocument) document, "$", out);
        return new ValidationResult(out);
    }

    private void validateStructure(StructureDocument value, String path, List<ValidationProblem> out) {
        if (value.tiles().size() > TerrainContentLimits.MAX_STRUCTURE_TILES) {
            error(path + ".tiles", "must contain at most "
                    + TerrainContentLimits.MAX_STRUCTURE_TILES + " tiles", out);
        }
        if (value.addons().size() > TerrainContentLimits.MAX_STRUCTURE_ADDONS) {
            error(path + ".addons", "must contain at most "
                    + TerrainContentLimits.MAX_STRUCTURE_ADDONS + " addons", out);
        }
        Set<String> sourceIds = new HashSet<>();
        for (int i = 0; i < value.tiles().size(); i++) {
            TileRecord tile = value.tiles().get(i);
            String item = path + ".tiles[" + i + "]";
            checkSourceId(tile.sourceId(), item + ".sourceId", sourceIds, out);
            finite(tile.turnDeltaDegrees(), item + ".turnDeltaDegrees", out);
            finite(tile.absoluteSlopeDegrees(), item + ".absoluteSlopeDegrees", out);
            if (Math.abs(tile.absoluteSlopeDegrees()) >= 90.0)
                error(item + ".absoluteSlopeDegrees", "must be between -90 and 90 degrees", out);
            finite(tile.liftBefore(), item + ".liftBefore", out);
            finite(tile.alpha(), item + ".alpha", out);
            if (tile.alpha() < 0 || tile.alpha() > 1) error(item + ".alpha", "must be in [0, 1]", out);
            finite(tile.brightness(), item + ".brightness", out);
            if (tile.brightness() < 0) error(item + ".brightness", "must not be negative", out);
            if (tile.surfaceKind().trim().isEmpty()) error(item + ".surfaceKind", "must not be blank", out);
            if (tile.resolvedTurnDeltaRadians() != null) {
                finite(tile.resolvedTurnDeltaRadians(),
                        item + ".resolvedTurnDeltaRadians", out);
                if (Math.abs(Math.toDegrees(tile.resolvedTurnDeltaRadians())
                        - tile.turnDeltaDegrees()) > 1.0e-9) {
                    error(item + ".resolvedTurnDeltaRadians",
                            "must represent turnDeltaDegrees", out);
                }
            }
            if (tile.resolvedAbsoluteSlopeRadians() != null) {
                finite(tile.resolvedAbsoluteSlopeRadians(),
                        item + ".resolvedAbsoluteSlopeRadians", out);
                if (Math.abs(tile.resolvedAbsoluteSlopeRadians()) >= Math.PI * 0.5
                        || Math.abs(Math.toDegrees(tile.resolvedAbsoluteSlopeRadians())
                        - tile.absoluteSlopeDegrees()) > 1.0e-9) {
                    error(item + ".resolvedAbsoluteSlopeRadians",
                            "must represent an absolute slope between -90 and 90 degrees", out);
                }
            }
        }

        Map<String, AddonReservation> addonsById = new HashMap<>();
        for (int i = 0; i < value.addons().size(); i++) {
            AddonReservation addon = value.addons().get(i);
            String item = path + ".addons[" + i + "]";
            checkSourceId(addon.sourceId(), item + ".sourceId", sourceIds, out);
            addonsById.put(addon.sourceId(), addon);
            Placement placement = addon.placement();
            if (placement.mode() == Placement.Mode.GRID) {
                // Physical rows are derived from completed geometry and rowSpacing, so their
                // count is not the tile count. Exact upper bounds are checked by deterministic
                // materialization (preview, Publish, and strict runtime catalog loading).
                if (placement.rowStart() < 1 || placement.rowEnd() < placement.rowStart())
                    error(item + ".placement", "grid rows must be a non-empty 1-based range", out);
                if (placement.columnStart() < 1 || placement.columnEnd() < placement.columnStart())
                    error(item + ".placement", "grid columns must be a non-empty 1-based range", out);
                else if (placement.columnEnd()
                        > TrackProfile.gameplayDefault().gridColumns)
                    error(item + ".placement.columnEnd",
                            "must not exceed the session profile's grid column count", out);
            } else {
                if (!sourceIds.contains(placement.segmentSourceId()))
                    error(item + ".placement.segmentSourceId", "does not name a tile in this structure", out);
                normalized(placement.across(), item + ".placement.across", out);
                normalized(placement.along(), item + ".placement.along", out);
                validateExplicitFootprint(addon, placement, item, out);
            }
            for (Map.Entry<String, Double> parameter : addon.parameters().entrySet())
                finite(parameter.getValue(), item + ".parameters." + parameter.getKey(), out);
        }
        for (int i = 0; i < value.addons().size(); i++) {
            AddonReservation addon = value.addons().get(i);
            String pathToPair = path + ".addons[" + i + "].pairSourceId";
            boolean portal = addon.kind() == AddonKind.PORTAL_ENTRANCE || addon.kind() == AddonKind.PORTAL_EXIT;
            if (portal && addon.pairSourceId() == null) error(pathToPair, "portal must name its counterpart", out);
            if (!portal && addon.pairSourceId() != null) error(pathToPair, "only portals may have a pair", out);
            if (addon.pairSourceId() != null) {
                AddonReservation other = addonsById.get(addon.pairSourceId());
                if (other == null) error(pathToPair, "counterpart does not exist", out);
                else if (!addon.sourceId().equals(other.pairSourceId())) error(pathToPair, "pairing must be reciprocal", out);
                else if (addon.kind() == other.kind()) error(pathToPair, "portal roles must be opposite", out);
            }
        }
    }

    private void validateExplicitFootprint(AddonReservation addon, Placement placement,
                                           String path, List<ValidationProblem> out) {
        Double halfAcross = addon.parameters().get(AddonParameterNames.FOOTPRINT_HALF_ACROSS);
        Double halfAlong = addon.parameters().get(AddonParameterNames.FOOTPRINT_HALF_ALONG);
        Double poseAligned = addon.parameters().get(AddonParameterNames.FOOTPRINT_POSE_ALIGNED);
        if (halfAcross != null) {
            finite(halfAcross, path + ".parameters."
                    + AddonParameterNames.FOOTPRINT_HALF_ACROSS, out);
            if (!(halfAcross > 0.0)
                    || placement.across() - halfAcross < 0.0
                    || placement.across() + halfAcross > 1.0) {
                error(path + ".parameters." + AddonParameterNames.FOOTPRINT_HALF_ACROSS,
                        "must be positive and remain inside the segment", out);
            }
        }
        if (halfAlong != null) {
            finite(halfAlong, path + ".parameters."
                    + AddonParameterNames.FOOTPRINT_HALF_ALONG, out);
            if (!(halfAlong > 0.0)
                    || placement.along() - halfAlong < 0.0
                    || placement.along() + halfAlong > 1.0) {
                error(path + ".parameters." + AddonParameterNames.FOOTPRINT_HALF_ALONG,
                        "must be positive and remain inside the segment", out);
            }
        }
        if (poseAligned != null) {
            finite(poseAligned, path + ".parameters."
                    + AddonParameterNames.FOOTPRINT_POSE_ALIGNED, out);
            if (poseAligned != 0.0 && poseAligned != 1.0) {
                error(path + ".parameters." + AddonParameterNames.FOOTPRINT_POSE_ALIGNED,
                        "must be 0 or 1", out);
            }
            if (poseAligned == 1.0 && Math.abs(placement.along() - 0.5) > 1.0e-12) {
                error(path + ".placement.along",
                        "must be 0.5 for pose-aligned footprints", out);
            }
        }
    }

    private void validateLevel(LevelDocument value, String path, List<ValidationProblem> out) {
        if (value.sessionProfileId().trim().isEmpty()) error(path + ".sessionProfileId", "must not be blank", out);
        if (value.entries().size() > TerrainContentLimits.MAX_LEVEL_ENTRIES) {
            error(path + ".entries", "must contain at most "
                    + TerrainContentLimits.MAX_LEVEL_ENTRIES + " entries", out);
        }
        long inlineTiles = 0L;
        long inlineAddons = 0L;
        Set<String> sourceIds = new HashSet<>();
        for (int i = 0; i < value.entries().size(); i++) {
            LevelEntry entry = value.entries().get(i);
            String item = path + ".entries[" + i + "]";
            checkSourceId(entry.sourceId(), item + ".sourceId", sourceIds, out);
            if (entry.isReference() && entry.referenceId().trim().isEmpty())
                error(item, "reference must not be blank", out);
            if (entry.kind() == LevelEntry.Kind.INLINE_STRUCTURE) {
                checkVersion(entry.inlineStructure().formatVersion(), item + ".inlineStructure", out);
                validateStructure(entry.inlineStructure(), item + ".inlineStructure", out);
                inlineTiles += entry.inlineStructure().tiles().size();
                inlineAddons += entry.inlineStructure().addons().size();
            }
        }
        if (inlineTiles > TerrainContentLimits.MAX_RESOLVED_TILES) {
            error(path + ".entries", "inline structures contain more than "
                    + TerrainContentLimits.MAX_RESOLVED_TILES + " tiles", out);
        }
        if (inlineAddons > TerrainContentLimits.MAX_RESOLVED_ADDONS) {
            error(path + ".entries", "inline structures contain more than "
                    + TerrainContentLimits.MAX_RESOLVED_ADDONS + " addons", out);
        }
    }

    private void validateCatalog(CatalogDocument value, String path, List<ValidationProblem> out) {
        if (value.entries().size() > TerrainContentLimits.MAX_CATALOG_ENTRIES) {
            error(path + ".entries", "must contain at most "
                    + TerrainContentLimits.MAX_CATALOG_ENTRIES + " entries", out);
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < value.entries().size(); i++) {
            CatalogEntry entry = value.entries().get(i);
            String item = path + ".entries[" + i + "]";
            checkDocumentId(entry.id(), item + ".id", out);
            if (!ids.add(entry.id())) error(item + ".id", "duplicate catalog ID", out);
            if (entry.location().trim().isEmpty()) error(item + ".location", "must not be blank", out);
        }
    }

    private void checkVersion(int version, String path, List<ValidationProblem> out) {
        if (version != TerrainSourceDocument.CURRENT_FORMAT_VERSION)
            error(path + ".formatVersion", "unsupported format version " + version, out);
    }

    private void checkDocumentId(String id, String path, List<ValidationProblem> out) {
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9._:-]*"))
            error(path, "must be a stable non-empty identifier", out);
    }

    private void checkSourceId(String id, String path, Set<String> seen, List<ValidationProblem> out) {
        try { UUID.fromString(id); }
        catch (IllegalArgumentException error) { error(path, "must be a UUID", out); }
        if (!seen.add(id)) error(path, "duplicate source UUID", out);
    }

    private void finite(double value, String path, List<ValidationProblem> out) {
        if (Double.isNaN(value) || Double.isInfinite(value)) error(path, "must be finite", out);
    }

    private void normalized(double value, String path, List<ValidationProblem> out) {
        finite(value, path, out);
        if (value < 0 || value > 1) error(path, "must be in [0, 1]", out);
    }

    private void error(String path, String message, List<ValidationProblem> out) {
        out.add(new ValidationProblem(ValidationProblem.Severity.ERROR, path, message));
    }
}
