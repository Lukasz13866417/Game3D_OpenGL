package com.example.game3d.terrain.editor.edit;

import com.example.game3d.terrain.editor.state.DocumentEdit;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;
import com.example.game3d.terrain.io.model.TileRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Compound level-entry operations, including in-place editing of inline structures. */
public final class LevelEdits {
    private LevelEdits() {
    }

    public static DocumentEdit add(LevelEntry entry) {
        return document -> {
            LevelDocument level = requireLevel(document);
            List<LevelEntry> entries = new ArrayList<>(level.entries());
            entries.add(entry);
            return level.withEntries(entries);
        };
    }

    /**
     * Adds a new inline structure after applying its initial edit. The creation and edit are one
     * document operation, so callers get one undo step and can never expose a half-created entry.
     */
    public static DocumentEdit addInlineAndEdit(
            String entrySourceId,
            StructureDocument inlineStructure,
            DocumentEdit initialEdit) {
        Objects.requireNonNull(entrySourceId, "entrySourceId");
        Objects.requireNonNull(inlineStructure, "inlineStructure");
        Objects.requireNonNull(initialEdit, "initialEdit");
        return document -> {
            LevelDocument level = requireLevel(document);
            TerrainSourceDocument edited = initialEdit.apply(inlineStructure);
            if (!(edited instanceof StructureDocument structure)) {
                throw new IllegalArgumentException("Inline edit did not return a structure");
            }
            List<LevelEntry> entries = new ArrayList<>(level.entries());
            entries.add(LevelEntry.inline(entrySourceId, structure));
            return level.withEntries(entries);
        };
    }

    /**
     * Direct Add Tile semantics for a level with no selected child. An existing sole inline
     * structure wins; otherwise a new inline structure is appended together with the tile.
     */
    public static DocumentEdit appendTileToSoleInlineOrCreate(
            String newEntrySourceId,
            StructureDocument newInlineStructure,
            TileRecord tile) {
        Objects.requireNonNull(newEntrySourceId, "newEntrySourceId");
        Objects.requireNonNull(newInlineStructure, "newInlineStructure");
        Objects.requireNonNull(tile, "tile");
        return document -> {
            LevelDocument level = requireLevel(document);
            LevelEntry soleInline = null;
            for (LevelEntry entry : level.entries()) {
                if (entry.kind() != LevelEntry.Kind.INLINE_STRUCTURE) continue;
                if (soleInline != null) {
                    throw new IllegalArgumentException(
                            "Select one inline structure before adding a tile");
                }
                soleInline = entry;
            }

            List<LevelEntry> entries = new ArrayList<>(level.entries().size() + 1);
            if (soleInline == null) {
                entries.addAll(level.entries());
                List<TileRecord> tiles = new ArrayList<>(newInlineStructure.tiles());
                tiles.add(tile);
                entries.add(LevelEntry.inline(newEntrySourceId,
                        newInlineStructure.withTiles(tiles)));
            } else {
                for (LevelEntry entry : level.entries()) {
                    if (entry != soleInline) {
                        entries.add(entry);
                        continue;
                    }
                    List<TileRecord> tiles = new ArrayList<>(
                            entry.inlineStructure().tiles());
                    tiles.add(tile);
                    entries.add(LevelEntry.inline(entry.sourceId(),
                            entry.inlineStructure().withTiles(tiles)));
                }
            }
            return level.withEntries(entries);
        };
    }

    public static DocumentEdit editInline(String entrySourceId, DocumentEdit structureEdit) {
        return document -> {
            LevelDocument level = requireLevel(document);
            List<LevelEntry> entries = new ArrayList<>(level.entries().size());
            boolean found = false;
            for (LevelEntry entry : level.entries()) {
                if (!entry.sourceId().equals(entrySourceId)) {
                    entries.add(entry);
                    continue;
                }
                if (entry.kind() != LevelEntry.Kind.INLINE_STRUCTURE) {
                    throw new IllegalArgumentException("Selected level entry is not inline");
                }
                TerrainSourceDocument edited = structureEdit.apply(entry.inlineStructure());
                if (!(edited instanceof StructureDocument structure)) {
                    throw new IllegalArgumentException("Inline edit did not return a structure");
                }
                entries.add(LevelEntry.inline(entry.sourceId(), structure));
                found = true;
            }
            if (!found) {
                throw new IllegalArgumentException("Unknown inline level entry " + entrySourceId);
            }
            return level.withEntries(entries);
        };
    }

    public static DocumentEdit delete(Set<String> entrySourceIds) {
        return document -> {
            LevelDocument level = requireLevel(document);
            List<LevelEntry> entries = new ArrayList<>();
            for (LevelEntry entry : level.entries()) {
                if (!entrySourceIds.contains(entry.sourceId())) {
                    entries.add(entry);
                }
            }
            return level.withEntries(entries);
        };
    }

    public static DocumentEdit reorder(List<String> orderedSourceIds) {
        return document -> {
            LevelDocument level = requireLevel(document);
            if (orderedSourceIds.size() != level.entries().size()
                    || new HashSet<>(orderedSourceIds).size() != orderedSourceIds.size()) {
                throw new IllegalArgumentException(
                        "reorder must contain every level entry exactly once");
            }
            Map<String, LevelEntry> byId = new HashMap<>();
            for (LevelEntry entry : level.entries()) {
                byId.put(entry.sourceId(), entry);
            }
            List<LevelEntry> entries = new ArrayList<>();
            for (String id : orderedSourceIds) {
                LevelEntry entry = byId.get(id);
                if (entry == null) {
                    throw new IllegalArgumentException("Unknown level entry " + id);
                }
                entries.add(entry);
            }
            return level.withEntries(entries);
        };
    }

    private static LevelDocument requireLevel(TerrainSourceDocument document) {
        if (!(document instanceof LevelDocument level)) {
            throw new IllegalArgumentException("Level operation requires a level document");
        }
        return level;
    }
}
