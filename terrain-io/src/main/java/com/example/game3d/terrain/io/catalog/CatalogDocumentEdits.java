package com.example.game3d.terrain.io.catalog;

import com.example.game3d.terrain.io.model.CatalogDocument;
import com.example.game3d.terrain.io.model.CatalogEntry;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure immutable edits for the project gameplay catalog. */
public final class CatalogDocumentEdits {
    private CatalogDocumentEdits() {
    }

    public static CatalogDocument newGameplayCatalog(String catalogId) {
        List<CatalogEntry> entries = new ArrayList<CatalogEntry>();
        for (String id : GameplayCatalogPolicy.requiredBuiltinIds()) {
            entries.add(new CatalogEntry(
                    id, CatalogEntry.Kind.JAVA_PROVIDER, id, true));
        }
        return new CatalogDocument(
                TerrainSourceDocument.CURRENT_FORMAT_VERSION, catalogId, entries);
    }

    public static CatalogDocument addJsonLevel(
            CatalogDocument catalog,
            String entryId,
            String levelDocumentId,
            boolean enabled) {
        requireCatalog(catalog);
        requireValue(entryId, "entryId");
        requireValue(levelDocumentId, "levelDocumentId");
        if (find(catalog, entryId) != null) {
            throw new IllegalArgumentException(
                    "Duplicate gameplay catalog ID '" + entryId + "'");
        }
        List<CatalogEntry> entries = new ArrayList<CatalogEntry>(catalog.entries());
        entries.add(new CatalogEntry(entryId, CatalogEntry.Kind.JSON_LEVEL,
                levelDocumentId, enabled));
        return withEntries(catalog, entries);
    }

    public static CatalogDocument replaceJsonLevel(
            CatalogDocument catalog,
            String existingEntryId,
            String newEntryId,
            String levelDocumentId,
            boolean enabled) {
        requireCustom(catalog, existingEntryId);
        requireValue(newEntryId, "newEntryId");
        requireValue(levelDocumentId, "levelDocumentId");
        if (!existingEntryId.equals(newEntryId) && find(catalog, newEntryId) != null) {
            throw new IllegalArgumentException(
                    "Duplicate gameplay catalog ID '" + newEntryId + "'");
        }
        List<CatalogEntry> entries = new ArrayList<CatalogEntry>(catalog.entries());
        int index = indexOf(entries, existingEntryId);
        entries.set(index, new CatalogEntry(newEntryId,
                CatalogEntry.Kind.JSON_LEVEL, levelDocumentId, enabled));
        return withEntries(catalog, entries);
    }

    public static CatalogDocument setEnabled(
            CatalogDocument catalog, String entryId, boolean enabled) {
        CatalogEntry existing = requireCustom(catalog, entryId);
        return replaceJsonLevel(catalog, entryId, existing.id(),
                existing.location(), enabled);
    }

    public static CatalogDocument remove(
            CatalogDocument catalog, String entryId) {
        requireCustom(catalog, entryId);
        List<CatalogEntry> entries = new ArrayList<CatalogEntry>(catalog.entries());
        entries.remove(indexOf(entries, entryId));
        return withEntries(catalog, entries);
    }

    /** Reorders all and only custom entries while preserving the locked built-in prefix. */
    public static CatalogDocument reorderCustom(
            CatalogDocument catalog, List<String> orderedEntryIds) {
        requireCatalog(catalog);
        if (orderedEntryIds == null) {
            throw new IllegalArgumentException("orderedEntryIds == null");
        }
        Map<String, CatalogEntry> custom = new HashMap<String, CatalogEntry>();
        int first = GameplayCatalogPolicy.customEntryStartIndex();
        if (catalog.entries().size() < first) {
            throw new IllegalArgumentException("Gameplay catalog omits required built-ins");
        }
        for (int i = first; i < catalog.entries().size(); i++) {
            CatalogEntry entry = catalog.entries().get(i);
            if (custom.put(entry.id(), entry) != null) {
                throw new IllegalArgumentException(
                        "Duplicate gameplay catalog ID '" + entry.id() + "'");
            }
        }
        Set<String> ordered = new HashSet<String>(orderedEntryIds);
        if (ordered.size() != orderedEntryIds.size()
                || !ordered.equals(custom.keySet())) {
            throw new IllegalArgumentException(
                    "Custom order must name every custom catalog entry exactly once");
        }
        List<CatalogEntry> entries = new ArrayList<CatalogEntry>(catalog.entries().subList(0, first));
        for (String id : orderedEntryIds) {
            entries.add(custom.get(id));
        }
        return withEntries(catalog, entries);
    }

    public static List<CatalogEntry> customEntries(CatalogDocument catalog) {
        requireCatalog(catalog);
        int first = GameplayCatalogPolicy.customEntryStartIndex();
        if (catalog.entries().size() < first) {
            throw new IllegalArgumentException("Gameplay catalog omits required built-ins");
        }
        return java.util.Collections.unmodifiableList(new ArrayList<CatalogEntry>(
                catalog.entries().subList(first, catalog.entries().size())));
    }

    private static CatalogEntry requireCustom(
            CatalogDocument catalog, String entryId) {
        requireCatalog(catalog);
        if (GameplayCatalogPolicy.isRequiredBuiltinId(entryId)) {
            throw new IllegalArgumentException(
                    "Required built-in catalog entries are read-only: " + entryId);
        }
        CatalogEntry entry = find(catalog, entryId);
        if (entry == null) {
            throw new IllegalArgumentException(
                    "Unknown gameplay catalog entry '" + entryId + "'");
        }
        if (entry.kind() != CatalogEntry.Kind.JSON_LEVEL) {
            throw new IllegalArgumentException(
                    "Only custom JSON levels are editable: " + entryId);
        }
        return entry;
    }

    private static CatalogEntry find(CatalogDocument catalog, String id) {
        for (CatalogEntry entry : catalog.entries()) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    private static int indexOf(List<CatalogEntry> entries, String id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown gameplay catalog entry '" + id + "'");
    }

    private static CatalogDocument withEntries(
            CatalogDocument catalog, List<CatalogEntry> entries) {
        return new CatalogDocument(
                catalog.formatVersion(), catalog.id(), entries);
    }

    private static void requireCatalog(CatalogDocument catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog == null");
        }
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is empty");
        }
    }
}
