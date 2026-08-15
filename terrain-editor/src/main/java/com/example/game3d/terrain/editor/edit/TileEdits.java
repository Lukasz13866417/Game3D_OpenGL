package com.example.game3d.terrain.editor.edit;

import com.example.game3d.terrain.editor.state.DocumentEdit;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;
import com.example.game3d.terrain.io.model.TileRecord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class TileEdits {
    public enum Mode { SET, ADD, LINEAR_SEQUENCE }
    public enum Field { TURN, SLOPE, LIFT, ALPHA, BRIGHTNESS }

    private TileEdits() {}

    public static DocumentEdit repeat(int insertAt, boolean solid, String surface,
                                      RepeatSpec spec, Supplier<String> sourceIds) {
        return document -> {
            StructureDocument structure = requireStructure(document);
            if (insertAt < 0 || insertAt > structure.tiles().size())
                throw new IllegalArgumentException("insertAt out of range");
            List<TileRecord> tiles = new ArrayList<>(structure.tiles());
            List<TileRecord> generated = new ArrayList<>();
            for (int i = 0; i < spec.count(); i++) {
                generated.add(new TileRecord(sourceIds.get(), solid,
                        spec.startTurn() + i * spec.turnIncrement(),
                        spec.startSlope() + i * spec.slopeIncrement(),
                        spec.startLift() + i * spec.liftIncrement(), surface,
                        spec.startAlpha() + i * spec.alphaIncrement(),
                        spec.startBrightness() + i * spec.brightnessIncrement()));
            }
            tiles.addAll(insertAt, generated);
            return structure.withTiles(tiles);
        };
    }

    public static DocumentEdit repeat(int insertAt, boolean solid, String surface, RepeatSpec spec) {
        return repeat(insertAt, solid, surface, spec, () -> UUID.randomUUID().toString());
    }

    public static DocumentEdit duplicate(Set<String> sourceIds, Supplier<String> newIds) {
        return document -> {
            StructureDocument structure = requireStructure(document);
            List<TileRecord> tiles = new ArrayList<>();
            for (TileRecord tile : structure.tiles()) {
                tiles.add(tile);
                if (sourceIds.contains(tile.sourceId())) tiles.add(tile.duplicate(newIds.get()));
            }
            return structure.withTiles(tiles);
        };
    }

    public static DocumentEdit delete(Set<String> sourceIds) {
        return document -> {
            StructureDocument structure = requireStructure(document);
            List<TileRecord> tiles = new ArrayList<>();
            for (TileRecord tile : structure.tiles()) if (!sourceIds.contains(tile.sourceId())) tiles.add(tile);
            return structure.withTiles(tiles);
        };
    }

    public static DocumentEdit reorder(List<String> orderedIds) {
        return document -> {
            StructureDocument structure = requireStructure(document);
            if (orderedIds.size() != structure.tiles().size()
                    || new HashSet<>(orderedIds).size() != orderedIds.size())
                throw new IllegalArgumentException("reorder must contain every tile exactly once");
            java.util.Map<String, TileRecord> byId = new java.util.HashMap<>();
            for (TileRecord tile : structure.tiles()) byId.put(tile.sourceId(), tile);
            List<TileRecord> tiles = new ArrayList<>();
            for (String id : orderedIds) {
                TileRecord tile = byId.get(id);
                if (tile == null) throw new IllegalArgumentException("unknown tile " + id);
                tiles.add(tile);
            }
            return structure.withTiles(tiles);
        };
    }

    public static DocumentEdit numeric(Set<String> sourceIds, Field field, Mode mode,
                                       double start, double increment) {
        return document -> {
            StructureDocument structure = requireStructure(document);
            List<TileRecord> tiles = new ArrayList<>();
            int selectedIndex = 0;
            for (TileRecord tile : structure.tiles()) {
                if (!sourceIds.contains(tile.sourceId())) {
                    tiles.add(tile);
                    continue;
                }
                double operand = mode == Mode.LINEAR_SEQUENCE ? start + selectedIndex * increment : start;
                selectedIndex++;
                double current = fieldValue(tile, field);
                double value = mode == Mode.ADD ? current + operand : operand;
                tiles.add(withField(tile, field, value));
            }
            return structure.withTiles(tiles);
        };
    }

    /** Sets solid/gap state for every selected existing tile as one compound edit. */
    public static DocumentEdit setSolid(Set<String> sourceIds, boolean solid) {
        return setCategorical(sourceIds, solid, null);
    }

    /** Sets the persisted core surface tag for every selected existing tile. */
    public static DocumentEdit setSurface(Set<String> sourceIds, String surfaceKind) {
        if (surfaceKind == null || surfaceKind.trim().isEmpty()) {
            throw new IllegalArgumentException("surfaceKind is blank");
        }
        return setCategorical(sourceIds, null, surfaceKind);
    }

    /** Null means keep that category; both changes still form one multi-tile undo operation. */
    public static DocumentEdit setCategorical(
            Set<String> sourceIds, Boolean solid, String surfaceKind) {
        if (sourceIds == null) {
            throw new IllegalArgumentException("sourceIds == null");
        }
        if (solid == null && surfaceKind == null) {
            throw new IllegalArgumentException("No categorical tile change requested");
        }
        if (surfaceKind != null && surfaceKind.trim().isEmpty()) {
            throw new IllegalArgumentException("surfaceKind is blank");
        }
        return document -> {
            StructureDocument structure = requireStructure(document);
            List<TileRecord> tiles = new ArrayList<>();
            for (TileRecord tile : structure.tiles()) {
                if (!sourceIds.contains(tile.sourceId())) {
                    tiles.add(tile);
                    continue;
                }
                tiles.add(new TileRecord(tile.sourceId(),
                        solid == null ? tile.solid() : solid,
                        tile.turnDeltaDegrees(), tile.absoluteSlopeDegrees(),
                        tile.liftBefore(),
                        surfaceKind == null ? tile.surfaceKind() : surfaceKind,
                        tile.alpha(), tile.brightness(),
                        tile.resolvedTurnDeltaRadians(),
                        tile.resolvedAbsoluteSlopeRadians()));
            }
            return structure.withTiles(tiles);
        };
    }

    private static double fieldValue(TileRecord tile, Field field) {
        return switch (field) {
            case TURN -> tile.turnDeltaDegrees();
            case SLOPE -> tile.absoluteSlopeDegrees();
            case LIFT -> tile.liftBefore();
            case ALPHA -> tile.alpha();
            case BRIGHTNESS -> tile.brightness();
        };
    }

    private static TileRecord withField(TileRecord tile, Field field, double value) {
        return tile.withValues(
                field == Field.TURN ? value : tile.turnDeltaDegrees(),
                field == Field.SLOPE ? value : tile.absoluteSlopeDegrees(),
                field == Field.LIFT ? value : tile.liftBefore(),
                field == Field.ALPHA ? value : tile.alpha(),
                field == Field.BRIGHTNESS ? value : tile.brightness());
    }

    private static StructureDocument requireStructure(TerrainSourceDocument document) {
        if (!(document instanceof StructureDocument structure))
            throw new IllegalArgumentException("Tile operation requires a structure document");
        return structure;
    }
}
