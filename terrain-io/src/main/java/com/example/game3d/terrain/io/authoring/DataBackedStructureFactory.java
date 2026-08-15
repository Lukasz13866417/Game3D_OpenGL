package com.example.game3d.terrain.io.authoring;

import com.example.game3d.authoring.AddonBlueprint;
import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.BaseTerrainStructure;
import com.example.game3d.authoring.BasicTerrainStructure;
import com.example.game3d.authoring.Terrain;
import com.example.game3d.core.terrain.SurfaceProperties;
import com.example.game3d.core.terrain.addon.Portal;
import com.example.game3d.terrain.io.model.AddonKind;
import com.example.game3d.terrain.io.model.AddonParameterNames;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TileRecord;

/** Converts a frozen JSON data model into the same brush commands as handwritten Java. */
public final class DataBackedStructureFactory {
    private DataBackedStructureFactory() {}

    public static BaseTerrainStructure<?> create(StructureDocument document) {
        return document.gridMode() == GridMode.BASIC
                ? new Basic(document) : new Advanced(document);
    }

    private static void tiles(StructureDocument document, Terrain.TileBrush brush) {
        for (TileRecord tile : document.tiles()) {
            if (tile.resolvedTurnDeltaRadians() == null
                    && tile.resolvedAbsoluteSlopeRadians() == null) {
                brush.addTileDegrees(tile.sourceId(), tile.solid(), tile.turnDeltaDegrees(),
                        tile.absoluteSlopeDegrees(), tile.liftBefore(), surface(tile.surfaceKind()),
                        (float) tile.alpha(), (float) tile.brightness());
            } else {
                double turn = tile.resolvedTurnDeltaRadians() == null
                        ? Math.toRadians(tile.turnDeltaDegrees())
                        : tile.resolvedTurnDeltaRadians();
                double slope = tile.resolvedAbsoluteSlopeRadians() == null
                        ? Math.toRadians(tile.absoluteSlopeDegrees())
                        : tile.resolvedAbsoluteSlopeRadians();
                brush.addHorizontalAng(turn);
                brush.setVerticalAng(slope);
                brush.liftUp(tile.liftBefore());
                brush.setUpcomingSurface(surface(tile.surfaceKind()));
                brush.setCornerAlphas((float) tile.alpha(), (float) tile.alpha());
                brush.setUpcomingBrightnessMultiplier((float) tile.brightness());
                if (tile.solid()) brush.addSegment(tile.sourceId());
                else brush.addEmptySegment(tile.sourceId());
            }
        }
    }

    private static void addons(StructureDocument document, Terrain.BaseGridBrush brush) {
        for (AddonReservation addon : document.addons()) {
            AddonBlueprint blueprint = blueprint(addon);
            Placement placement = addon.placement();
            if (placement.mode() == Placement.Mode.GRID) {
                brush.placeGridRegion(
                        placement.rowStart(), placement.rowEnd(),
                        placement.columnStart(), placement.columnEnd(), blueprint);
            } else {
                int row = rowOf(document, placement.segmentSourceId());
                double halfAcross = parameter(addon,
                        AddonParameterNames.FOOTPRINT_HALF_ACROSS, 0.04);
                double halfAlong = parameter(addon,
                        AddonParameterNames.FOOTPRINT_HALF_ALONG, 0.04);
                if (!(halfAcross > 0.0) || !(halfAlong > 0.0)) {
                    throw new IllegalArgumentException(
                            "Normalized footprint half extents must be positive");
                }
                if (parameter(addon,
                        AddonParameterNames.FOOTPRINT_POSE_ALIGNED, 0.0) != 0.0) {
                    double lateral = parameter(addon,
                            AddonParameterNames.POSE_LATERAL_FRACTION,
                            placement.across() - 0.5);
                    double halfAcrossWorld = parameter(addon,
                            AddonParameterNames.POSE_HALF_ACROSS_WORLD,
                            halfAcross * com.example.game3d.authoring.TrackProfile
                                    .gameplayDefault().width);
                    double halfAlongWorld = parameter(addon,
                            AddonParameterNames.POSE_HALF_ALONG_WORLD,
                            halfAlong * com.example.game3d.authoring.TrackProfile
                                    .gameplayDefault().tileLength);
                    brush.placePoseAlignedOnSegment(row, lateral,
                            halfAcrossWorld, halfAlongWorld,
                            blueprint);
                } else {
                    brush.placeNormalized(row, clamp(placement.across() - halfAcross),
                            clampUpper(placement.across() + halfAcross),
                            clamp(placement.along() - halfAlong),
                            clampUpper(placement.along() + halfAlong), blueprint);
                }
            }
        }
    }

    private static int rowOf(StructureDocument document, String sourceId) {
        for (int i = 0; i < document.tiles().size(); i++)
            if (document.tiles().get(i).sourceId().equals(sourceId)) return i + 1;
        throw new IllegalArgumentException("Unknown segment source ID " + sourceId);
    }

    private static AddonBlueprint blueprint(AddonReservation addon) {
        if (addon.kind() == AddonKind.DEATH_SPIKE) {
            double height = parameter(addon, "height", Double.NaN);
            double collisionRadius = parameter(addon, "collisionRadius", Double.NaN);
            return Double.isNaN(collisionRadius)
                    ? AddonBlueprint.deathSpike(addon.sourceId(), height,
                    parameter(addon, "baseOffset", .025))
                    : AddonBlueprint.deathSpike(addon.sourceId(), height,
                    parameter(addon, "baseOffset", .025), collisionRadius);
        }
        if (addon.kind() == AddonKind.AIR_JUMP_POTION) {
            return AddonBlueprint.airJumpPotion(addon.sourceId(),
                    parameter(addon, "triggerRadius", .22),
                    parameter(addon, "heightAboveSurface", .56), "POTION_FEATHER");
        }
        Portal.Role role = addon.kind() == AddonKind.PORTAL_ENTRANCE
                ? Portal.Role.ENTRANCE : Portal.Role.EXIT;
        String pairKey = addon.pairSourceId() == null ? addon.sourceId()
                : (addon.sourceId().compareTo(addon.pairSourceId()) < 0
                ? addon.sourceId() + ":" + addon.pairSourceId()
                : addon.pairSourceId() + ":" + addon.sourceId());
        return AddonBlueprint.portal(addon.sourceId(), pairKey, role);
    }

    private static double parameter(AddonReservation addon, String name, double fallback) {
        Double value = addon.parameters().get(name);
        return value == null ? fallback : value;
    }

    private static SurfaceProperties surface(String name) {
        if (SurfaceProperties.Kind.BOOST_RAMP.jsonTag.equals(name)) return SurfaceProperties.BOOST_RAMP;
        if (SurfaceProperties.Kind.BOOST_RAMP_LAUNCH.jsonTag.equals(name)) return SurfaceProperties.BOOST_RAMP_LAUNCH;
        if (SurfaceProperties.Kind.LEGACY_BOOST.jsonTag.equals(name)) return SurfaceProperties.LEGACY_BOOST;
        if (SurfaceProperties.Kind.NORMAL.jsonTag.equals(name) || "DEFAULT".equals(name)) return SurfaceProperties.NORMAL;
        throw new IllegalArgumentException("Unknown surface kind " + name);
    }

    private static double clamp(double value) { return Math.max(0.0, Math.min(.999999, value)); }
    private static double clampUpper(double value) { return Math.max(.000001, Math.min(1.0, value)); }

    private static final class Basic extends BasicTerrainStructure {
        private final StructureDocument document;
        Basic(StructureDocument document) { super(document.tiles().size()); this.document = document; name = document.id(); }
        @Override protected void generateTiles(Terrain.TileBrush brush) { tiles(document, brush); }
        @Override protected void generateAddons(Terrain.BasicGridBrush brush, int rows, int columns) {
            addons(document, brush);
        }
    }

    private static final class Advanced extends AdvancedTerrainStructure {
        private final StructureDocument document;
        Advanced(StructureDocument document) { super(document.tiles().size()); this.document = document; name = document.id(); }
        @Override protected void generateTiles(Terrain.TileBrush brush) { tiles(document, brush); }
        @Override protected void generateAddons(Terrain.AdvancedGridBrush brush, int rows, int columns) {
            addons(document, brush);
        }
    }
}
