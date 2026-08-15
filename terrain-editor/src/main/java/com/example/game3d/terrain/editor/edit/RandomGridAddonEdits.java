package com.example.game3d.terrain.editor.edit;

import com.example.game3d.authoring.AddonBlueprint;
import com.example.game3d.authoring.AdvancedTerrainStructure;
import com.example.game3d.authoring.BaseTerrainStructure;
import com.example.game3d.authoring.CapturedAddonPlacement;
import com.example.game3d.authoring.CapturedStructureCommands;
import com.example.game3d.authoring.Terrain;
import com.example.game3d.authoring.TerrainMaterializer;
import com.example.game3d.authoring.TrackProfile;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.terrain.editor.state.DocumentEdit;
import com.example.game3d.terrain.io.authoring.DataBackedStructureFactory;
import com.example.game3d.terrain.io.model.AddonKind;
import com.example.game3d.terrain.io.model.AddonReservation;
import com.example.game3d.terrain.io.model.GridMode;
import com.example.game3d.terrain.io.model.Placement;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.validation.TerrainContentLimits;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Expands one seeded advanced-grid selection into ordinary, independently editable reservations.
 * No random command or seed is retained in the document.
 */
public final class RandomGridAddonEdits {
    private static final TrackProfile PROFILE = TrackProfile.gameplayDefault();

    private RandomGridAddonEdits() {}

    /** One returned edit is one compound undo operation, even when it creates many addons. */
    public static DocumentEdit add(long seed, int count, AddonKind kind) {
        validateRequest(count, kind);
        return document -> {
            if (!(document instanceof StructureDocument structure)) {
                throw new IllegalArgumentException(
                        "Random GRID placement requires a structure");
            }
            return expand(structure, seed, count, kind);
        };
    }

    public static StructureDocument expand(
            StructureDocument structure, long seed, int count, AddonKind kind) {
        if (structure == null) {
            throw new IllegalArgumentException("structure is required");
        }
        validateRequest(count, kind);
        if (structure.gridMode() != GridMode.ADVANCED) {
            throw new IllegalArgumentException(
                    "Random GRID placement requires an ADVANCED structure");
        }
        if (structure.addons().size() + count
                > TerrainContentLimits.MAX_STRUCTURE_ADDONS) {
            throw new IllegalArgumentException(
                    "Random placement would exceed the structure limit of "
                            + TerrainContentLimits.MAX_STRUCTURE_ADDONS + " addons");
        }

        CapturedStructureCommands captured = TerrainMaterializer.captureResolvedCommands(
                selectionStructure(structure, count, kind), PROFILE, seed);
        if (captured.addonPlacements.size() < count) {
            throw new IllegalStateException("Shared grid engine returned too few placements");
        }

        int firstRandom = captured.addonPlacements.size() - count;
        List<AddonReservation> addons = new ArrayList<AddonReservation>(
                structure.addons().size() + count);
        addons.addAll(structure.addons());
        Set<String> sourceIds = new HashSet<String>();
        for (AddonReservation addon : structure.addons()) {
            sourceIds.add(addon.sourceId());
        }

        for (int i = 0; i < count; i++) {
            CapturedAddonPlacement selected =
                    captured.addonPlacements.get(firstRandom + i);
            if (!selected.gridPlacement
                    || selected.gridRowStart != selected.gridRowEnd) {
                throw new IllegalStateException(
                        "Shared grid engine did not resolve a single GRID cell");
            }
            int row = selected.gridRowStart + 1;
            int column = columnFromAcross(selected.acrossStart);
            String sourceId = deterministicSourceId(
                    structure.id(), seed, kind, row, column, i, sourceIds);
            sourceIds.add(sourceId);
            addons.add(new AddonReservation(sourceId, kind,
                    Placement.grid(row, row, column, column), null,
                    Collections.<String, Double>emptyMap()));
        }

        StructureDocument expanded = structure.withAddons(addons);
        // Force full canonical placement validation now: gaps, bounds, overlaps, source IDs,
        // and addon payloads cannot be deferred until publish.
        TerrainMaterializer.materialize(DataBackedStructureFactory.create(expanded),
                PROFILE, Vec3.ZERO, 0L);
        return expanded;
    }

    private static BaseTerrainStructure<?> selectionStructure(
            final StructureDocument structure, final int count, final AddonKind kind) {
        return new AdvancedTerrainStructure(0) {
            @Override
            protected void generateTiles(Terrain.TileBrush brush) {
                // The child emits exact document geometry and propagates all existing occupancy.
                brush.addChild(DataBackedStructureFactory.create(structure));
            }

            @Override
            protected void generateAddons(
                    Terrain.AdvancedGridBrush brush, int rows, int columns) {
                AddonBlueprint[] candidates = new AddonBlueprint[count];
                for (int i = 0; i < count; i++) {
                    String id = "editor-random-grid-preview:" + i;
                    candidates[i] = kind == AddonKind.DEATH_SPIKE
                            ? AddonBlueprint.deathSpike(id)
                            : AddonBlueprint.airJumpPotion(id);
                }
                // This is the authoritative reservation algorithm, including occupancy and seed.
                brush.reserveKRandomFields(candidates);
            }
        };
    }

    private static int columnFromAcross(double acrossStart) {
        double scaled = acrossStart * PROFILE.gridColumns;
        int zeroBased = (int) Math.round(scaled);
        if (zeroBased < 0 || zeroBased >= PROFILE.gridColumns
                || Math.abs(scaled - zeroBased) > 1.0e-9) {
            throw new IllegalStateException(
                    "Shared grid engine returned a non-cell-aligned placement");
        }
        return zeroBased + 1;
    }

    private static String deterministicSourceId(
            String structureId, long seed, AddonKind kind,
            int row, int column, int ordinal, Set<String> occupied) {
        String identity = structureId + '\u0000' + seed + '\u0000' + kind.name()
                + '\u0000' + row + '\u0000' + column + '\u0000' + ordinal;
        int collision = 0;
        while (true) {
            String input = collision == 0 ? identity : identity + '\u0000' + collision;
            String candidate = UUID.nameUUIDFromBytes(
                    input.getBytes(StandardCharsets.UTF_8)).toString();
            if (!occupied.contains(candidate)) {
                return candidate;
            }
            collision++;
        }
    }

    private static void validateRequest(int count, AddonKind kind) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be positive");
        }
        if (kind != AddonKind.DEATH_SPIKE
                && kind != AddonKind.AIR_JUMP_POTION) {
            throw new IllegalArgumentException(
                    "Random GRID placement supports spikes and potions only");
        }
    }
}
