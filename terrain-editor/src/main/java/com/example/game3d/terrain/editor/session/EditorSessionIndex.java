package com.example.game3d.terrain.editor.session;

import com.example.game3d.terrain.editor.compile.EditorReferenceSnapshot;
import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import com.example.game3d.terrain.io.resolve.TerrainProjectContentIndex;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable open-document index and transitive reverse-reference graph. */
public final class EditorSessionIndex {
    private final EditorReferenceSnapshot references;
    private final Map<UUID, EditorReferenceSnapshot.OpenDocument> documents;
    private final Map<String, Set<UUID>> ownersById;
    private final Map<String, Set<String>> directDependentIdsById;

    public EditorSessionIndex(
            TerrainDocumentRepository saved,
            Collection<EditorReferenceSnapshot.OpenDocument> openDocuments) {
        Objects.requireNonNull(openDocuments, "openDocuments");
        this.references = new EditorReferenceSnapshot(saved, openDocuments);
        LinkedHashMap<UUID, EditorReferenceSnapshot.OpenDocument> byWorkspace =
                new LinkedHashMap<>();
        LinkedHashMap<String, LinkedHashSet<UUID>> owners = new LinkedHashMap<>();
        LinkedHashMap<String, LevelDocument> dependencyLevels = new LinkedHashMap<>();
        for (EditorReferenceSnapshot.OpenDocument open : openDocuments) {
            byWorkspace.put(open.workspaceId(), open);
            TerrainSourceDocument document = open.document();
            owners.computeIfAbsent(document.id(), ignored -> new LinkedHashSet<>())
                    .add(open.workspaceId());
            if (document instanceof LevelDocument level
                    && references.findLevel(level.id()) == level) {
                dependencyLevels.put(level.id(), level);
            }
        }
        for (Map.Entry<String, TerrainProjectContentIndex.Entry<LevelDocument>>
                savedLevel : saved.contentIndex().levelsById().entrySet()) {
            LevelDocument effective = references.findLevel(savedLevel.getKey());
            if (effective != null) dependencyLevels.put(effective.id(), effective);
        }
        LinkedHashMap<String, LinkedHashSet<String>> dependents = new LinkedHashMap<>();
        for (LevelDocument level : dependencyLevels.values()) {
            for (LevelEntry entry : level.entries()) {
                if (!entry.isReference()) continue;
                String referencedId = canonicalReferenceId(entry, references);
                dependents.computeIfAbsent(referencedId,
                                ignored -> new LinkedHashSet<>())
                        .add(level.id());
            }
        }
        this.documents = Map.copyOf(byWorkspace);
        this.ownersById = immutableSets(owners);
        this.directDependentIdsById = immutableStringSets(dependents);
    }

    public EditorReferenceSnapshot references() { return references; }

    public Set<UUID> allWorkspaces() { return documents.keySet(); }

    public String documentId(UUID workspaceId) {
        EditorReferenceSnapshot.OpenDocument open = documents.get(workspaceId);
        return open == null ? null : open.document().id();
    }

    /** Includes owners and every open level that directly or transitively references an ID. */
    public Set<UUID> affectedBy(Collection<String> changedDocumentIds) {
        LinkedHashSet<UUID> affected = new LinkedHashSet<>();
        LinkedHashSet<String> visitedIds = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        for (String id : changedDocumentIds) if (id != null && visitedIds.add(id)) pending.add(id);
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            Set<UUID> owners = ownersById.get(id);
            if (owners != null) affected.addAll(owners);
            Set<String> dependents = directDependentIdsById.get(id);
            if (dependents == null) continue;
            for (String dependentId : dependents) {
                if (visitedIds.add(dependentId)) pending.addLast(dependentId);
            }
        }
        return Set.copyOf(affected);
    }

    private static String canonicalReferenceId(
            LevelEntry entry, EditorReferenceSnapshot references) {
        if (entry.kind() == LevelEntry.Kind.LEVEL_REFERENCE) {
            LevelDocument level = references.findLevel(entry.referenceId());
            return level == null ? entry.referenceId() : level.id();
        }
        if (entry.kind() == LevelEntry.Kind.STRUCTURE_REFERENCE) {
            StructureDocument structure = references.findStructure(entry.referenceId());
            return structure == null ? entry.referenceId() : structure.id();
        }
        return entry.referenceId();
    }

    private static Map<String, Set<UUID>> immutableSets(
            Map<String, ? extends Set<UUID>> source) {
        LinkedHashMap<String, Set<UUID>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static Map<String, Set<String>> immutableStringSets(
            Map<String, ? extends Set<String>> source) {
        LinkedHashMap<String, Set<String>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
        return Map.copyOf(copy);
    }
}
