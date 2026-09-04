package com.example.game3d.terrain.editor.importing;

import com.example.game3d.authoring.GameplayLevelCatalog;
import com.example.game3d.authoring.GameplayLevelProvider;
import com.example.game3d.authoring.CapturedTileCommand;
import com.example.game3d.authoring.CapturedAddonPlacement;
import com.example.game3d.authoring.CapturedStructureCommands;
import com.example.game3d.authoring.MaterializedStructure;
import com.example.game3d.authoring.TerrainMaterializer;
import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.AddonFootprint;
import com.example.game3d.core.terrain.addon.DeathSpike;
import com.example.game3d.core.terrain.addon.Portal;
import com.example.game3d.core.terrain.addon.Potion;
import com.example.game3d.terrain.io.model.AddonKind;
import com.example.game3d.terrain.io.model.AddonParameterNames;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;
import com.example.game3d.terrain.io.model.TileRecord;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Materializes a handwritten built-in and converts it to ordinary explicit JSON records. */
public final class BuiltinProviderImporter {
    private final GameplayLevelCatalog catalog;
    private final TrackProfile profile;

    public BuiltinProviderImporter() {
        this(GameplayLevelCatalog.builtIns(), TrackProfile.gameplayDefault());
    }

    BuiltinProviderImporter(GameplayLevelCatalog catalog, TrackProfile profile) {
        if (catalog == null || profile == null) {
            throw new IllegalArgumentException("catalog and profile are required");
        }
        this.catalog = catalog;
        this.profile = profile;
    }

    public List<String> providerIds() {
        List<String> result = new ArrayList<>();
        for (GameplayLevelProvider provider : catalog.entries()) {
            result.add(provider.stableId());
        }
        return Collections.unmodifiableList(result);
    }

    public ImportedProvider materialize(String providerId, long levelOrdinal) {
        if (levelOrdinal < 0L) {
            throw new IllegalArgumentException("levelOrdinal < 0");
        }
        GameplayLevelProvider provider = requireProvider(providerId);
        CapturedStructureCommands commands =
                TerrainMaterializer.captureResolvedCommands(
                        provider.create(levelOrdinal), profile, 0L);
        MaterializedStructure materialized = TerrainMaterializer.materialize(
                provider.create(levelOrdinal), profile, Vec3.ZERO, 0L);
        String defaultId = "imported." + providerId + "." + levelOrdinal;
        StructureDocument structure = toDocument(
                providerId, levelOrdinal, defaultId,
                commands.tiles, commands.addonPlacements, materialized.segments);
        long committedThrough = materialized.segments.isEmpty()
                ? -1L : materialized.segments.get(materialized.segments.size() - 1).id;
        TerrainSnapshot snapshot = new TerrainSnapshot(
                0L, committedThrough, 0L, materialized.segments);
        return new ImportedProvider(providerId, levelOrdinal, structure, snapshot);
    }

    public StructureDocument importStructure(
            String providerId, long levelOrdinal, String documentId) {
        return materialize(providerId, levelOrdinal).structureWithId(documentId);
    }

    public LevelDocument importInlineLevel(
            String providerId, long levelOrdinal, String levelId) {
        requireDocumentId(levelId);
        StructureDocument structure = importStructure(
                providerId, levelOrdinal, levelId + ".structure");
        return new LevelDocument(TerrainSourceDocument.CURRENT_FORMAT_VERSION,
                levelId, TrackProfile.GAMEPLAY_PROFILE_ID,
                List.of(LevelEntry.inline(stableUuid(providerId, levelOrdinal,
                        "level-entry", 0L), structure)));
    }

    private GameplayLevelProvider requireProvider(String id) {
        for (GameplayLevelProvider provider : catalog.entries()) {
            if (provider.stableId().equals(id)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown built-in provider " + id);
    }

    private StructureDocument toDocument(String providerId, long ordinal,
                                         String documentId,
                                         List<CapturedTileCommand> commands,
                                         List<CapturedAddonPlacement> capturedPlacements,
                                         List<TerrainSegment> segments) {
        if (commands.size() != segments.size()) {
            throw new IllegalArgumentException(
                    "Provider command and segment counts differ");
        }
        List<TileRecord> tiles = new ArrayList<>();
        Map<Long, String> tileSourceIds = new LinkedHashMap<>();
        for (int index = 0; index < segments.size(); index++) {
            TerrainSegment segment = segments.get(index);
            CapturedTileCommand command = commands.get(index);
            String sourceId = stableUuid(providerId, ordinal, "tile", index);
            tileSourceIds.put(segment.id, sourceId);
            if (Float.floatToIntBits(command.alphaLeft)
                    != Float.floatToIntBits(command.alphaRight)) {
                throw new IllegalArgumentException(
                        "Provider uses asymmetric edge alpha at tile " + index
                                + "; the explicit JSON tile format has one alpha");
            }
            tiles.add(new TileRecord(sourceId, command.solid,
                    Math.toDegrees(command.turnDeltaRadians),
                    Math.toDegrees(command.absolutePitchRadians),
                    command.liftBefore, command.surface.kind.name(),
                    command.alphaLeft, command.brightness,
                    command.turnDeltaRadians, command.absolutePitchRadians));
        }

        List<Addon> placed = new ArrayList<>();
        for (TerrainSegment segment : segments) {
            placed.addAll(segment.addons);
        }
        placed.sort(Comparator.comparingLong(Addon::id));
        List<CapturedAddonPlacement> sortedPlacements =
                new ArrayList<>(capturedPlacements);
        sortedPlacements.sort(Comparator
                .comparingInt((CapturedAddonPlacement value) -> value.tileIndex)
                .thenComparingInt(value -> value.declarationIndex));
        if (sortedPlacements.size() != placed.size()) {
            throw new IllegalArgumentException(
                    "Provider placement and addon counts differ");
        }
        Map<Long, String> addonSourceIds = new LinkedHashMap<>();
        for (Addon addon : placed) {
            addonSourceIds.put(addon.id(), stableUuid(
                    providerId, ordinal, "addon", addon.id()));
        }
        Map<Long, List<Addon>> portalsByPair = new LinkedHashMap<>();
        for (Addon addon : placed) {
            if (addon instanceof Portal portal) {
                portalsByPair.computeIfAbsent(portal.pairId,
                        ignored -> new ArrayList<>()).add(addon);
            }
        }

        List<AddonReservation> addons = new ArrayList<>();
        Map<Long, TerrainSegment> segmentsById = new LinkedHashMap<>();
        for (TerrainSegment segment : segments) {
            segmentsById.put(segment.id, segment);
        }
        for (int addonIndex = 0; addonIndex < placed.size(); addonIndex++) {
            Addon addon = placed.get(addonIndex);
            CapturedAddonPlacement captured = sortedPlacements.get(addonIndex);
            TerrainSegment owner = segmentsById.get(addon.ownerSegmentId());
            if (owner == null) {
                throw new IllegalArgumentException("Addon owner is not in provider output");
            }
            if (captured.tileIndex < 0 || captured.tileIndex >= segments.size()
                    || segments.get(captured.tileIndex).id != owner.id) {
                throw new IllegalArgumentException(
                        "Captured addon placement owner differs from materialization");
            }
            PlacementAndParameters authored = placement(addon, captured);
            String pairSourceId = null;
            if (addon instanceof Portal portal) {
                List<Addon> pair = portalsByPair.get(portal.pairId);
                if (pair == null || pair.size() != 2) {
                    throw new IllegalArgumentException(
                            "Built-in portal pair does not contain two endpoints");
                }
                Addon other = pair.get(0).id() == addon.id() ? pair.get(1) : pair.get(0);
                pairSourceId = addonSourceIds.get(other.id());
            }
            addons.add(new AddonReservation(addonSourceIds.get(addon.id()),
                    kind(addon), Placement.normalized(tileSourceIds.get(owner.id),
                    authored.across, authored.along), pairSourceId,
                    authored.parameters));
        }
        return new StructureDocument(TerrainSourceDocument.CURRENT_FORMAT_VERSION,
                documentId, GridMode.ADVANCED, tiles, addons);
    }

    private PlacementAndParameters placement(
            Addon addon, CapturedAddonPlacement captured) {
        AddonFootprint footprint = addon.footprint();
        Vec3 center = footprint.nearLeft.add(footprint.nearRight)
                .add(footprint.farLeft).add(footprint.farRight).multiply(0.25);
        if (!captured.poseAligned || captured.tileIndex != captured.tileEndIndex) {
            throw new IllegalArgumentException(
                    "Built-in provider import requires segment-local pose-aligned addons");
        }
        double across = 0.5 + captured.acrossStart;
        double along = 0.5;
        double halfAcross = captured.acrossEnd / profile.width;
        double halfAlong = captured.alongEnd / profile.tileLength;
        Map<String, Double> parameters = new LinkedHashMap<>();
        parameters.put(AddonParameterNames.FOOTPRINT_HALF_ACROSS, halfAcross);
        parameters.put(AddonParameterNames.FOOTPRINT_HALF_ALONG, halfAlong);
        parameters.put(AddonParameterNames.FOOTPRINT_POSE_ALIGNED, 1.0);
        parameters.put(AddonParameterNames.POSE_LATERAL_FRACTION,
                captured.acrossStart);
        parameters.put(AddonParameterNames.POSE_HALF_ACROSS_WORLD,
                captured.acrossEnd);
        parameters.put(AddonParameterNames.POSE_HALF_ALONG_WORLD,
                captured.alongEnd);
        if (addon instanceof DeathSpike spike) {
            parameters.put("height", spike.collisionHeight);
            parameters.put("baseOffset", spike.baseOffset);
            parameters.put("collisionRadius", spike.collisionRadius);
        } else if (addon instanceof Potion potion) {
            parameters.put("triggerRadius", potion.triggerRadius);
            Vec3 footprintNormal = footprint.nearRight.subtract(footprint.nearLeft)
                    .cross(footprint.farLeft.subtract(footprint.nearLeft)).normalized();
            if (footprintNormal.y < 0.0) {
                footprintNormal = footprintNormal.multiply(-1.0);
            }
            parameters.put("heightAboveSurface",
                    potion.center.subtract(center).dot(footprintNormal));
        }
        return new PlacementAndParameters(across, along,
                Collections.unmodifiableMap(parameters));
    }

    private static AddonKind kind(Addon addon) {
        if (addon instanceof DeathSpike) {
            return AddonKind.DEATH_SPIKE;
        }
        if (addon instanceof Potion) {
            return AddonKind.AIR_JUMP_POTION;
        }
        Portal portal = (Portal) addon;
        return portal.role == Portal.Role.ENTRANCE
                ? AddonKind.PORTAL_ENTRANCE : AddonKind.PORTAL_EXIT;
    }

    private static Vec3 midpoint(Vec3 left, Vec3 right) {
        return left.add(right).multiply(0.5);
    }

    private static String stableUuid(String providerId, long ordinal,
                                     String kind, long index) {
        String key = "terrain-editor-import:" + providerId + ":" + ordinal
                + ":" + kind + ":" + index;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void requireDocumentId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._:-]*")) {
            throw new IllegalArgumentException("Invalid document ID " + id);
        }
    }

    public record ImportedProvider(
            String providerId,
            long levelOrdinal,
            StructureDocument structure,
            TerrainSnapshot originalSnapshot) {
        public StructureDocument structureWithId(String id) {
            requireDocumentId(id);
            return new StructureDocument(structure.formatVersion(), id,
                    structure.gridMode(), structure.tiles(), structure.addons());
        }

        public LevelDocument inlineLevelWithId(String levelId) {
            requireDocumentId(levelId);
            StructureDocument inline = structureWithId(levelId + ".structure");
            return new LevelDocument(TerrainSourceDocument.CURRENT_FORMAT_VERSION,
                    levelId, TrackProfile.GAMEPLAY_PROFILE_ID,
                    List.of(LevelEntry.inline(stableUuid(
                            providerId, levelOrdinal, "level-entry", 0L), inline)));
        }
    }

    private record PlacementAndParameters(
            double across, double along, Map<String, Double> parameters) {
    }
}
