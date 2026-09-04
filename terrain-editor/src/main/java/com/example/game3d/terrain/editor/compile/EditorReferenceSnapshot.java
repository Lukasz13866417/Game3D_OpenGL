package com.example.game3d.terrain.editor.compile;

import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.model.TerrainSourceDocument;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import com.example.game3d.terrain.io.resolve.TerrainProjectContentIndex;
import com.example.game3d.terrain.io.validation.ValidationProblem;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;

/** Immutable project-plus-open-tab repository captured on the JavaFX thread. */
public final class EditorReferenceSnapshot implements TerrainDocumentRepository {
    public record OpenDocument(
            UUID workspaceId, Path sourcePath, TerrainSourceDocument document) {
        public OpenDocument {
            Objects.requireNonNull(workspaceId, "workspaceId");
            Objects.requireNonNull(document, "document");
            if (sourcePath != null) sourcePath = sourcePath.toAbsolutePath().normalize();
        }
    }

    private final TerrainDocumentRepository saved;
    private final Map<String, StructureDocument> structures;
    private final Map<String, LevelDocument> levels;
    private final List<ValidationProblem> problems;
    private final Set<String> ambiguousStructureIds;
    private final Set<String> ambiguousLevelIds;
    private final List<SavedShadow<StructureDocument>> savedStructureShadows;
    private final List<SavedShadow<LevelDocument>> savedLevelShadows;

    public EditorReferenceSnapshot(
            TerrainDocumentRepository saved,
            Collection<OpenDocument> openDocuments) {
        this.saved = Objects.requireNonNull(saved, "saved");
        Objects.requireNonNull(openDocuments, "openDocuments");
        LinkedHashMap<String, StructureDocument> nextStructures = new LinkedHashMap<>();
        LinkedHashMap<String, LevelDocument> nextLevels = new LinkedHashMap<>();
        LinkedHashMap<String, UUID> owners = new LinkedHashMap<>();
        java.util.LinkedHashSet<String> ambiguousStructures = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> ambiguousLevels = new java.util.LinkedHashSet<>();
        ArrayList<SavedShadow<StructureDocument>> shadowedStructures = new ArrayList<>();
        ArrayList<SavedShadow<LevelDocument>> shadowedLevels = new ArrayList<>();
        TerrainProjectContentIndex savedIndex = saved.contentIndex();
        ArrayList<ValidationProblem> nextProblems = new ArrayList<>();
        for (OpenDocument open : openDocuments) {
            TerrainSourceDocument document = open.document();
            // Source aliases are unambiguous even when the document's current ID is not. Record
            // the source shadow before ID validation so no lookup can leak the frozen saved value.
            if (document instanceof StructureDocument structure) {
                markShadowed(open.sourcePath(), structure,
                        savedIndex.structuresById(), shadowedStructures);
            } else if (document instanceof LevelDocument level) {
                markShadowed(open.sourcePath(), level,
                        savedIndex.levelsById(), shadowedLevels);
            }
            UUID previous = owners.putIfAbsent(document.id(), open.workspaceId());
            if (previous != null && !previous.equals(open.workspaceId())) {
                nextProblems.add(new ValidationProblem(
                        ValidationProblem.Severity.ERROR,
                        "$.id",
                        "Duplicate open document ID '" + document.id()
                                + "'; close or rename one tab"));
                nextStructures.remove(document.id());
                nextLevels.remove(document.id());
                ambiguousStructures.add(document.id());
                ambiguousLevels.add(document.id());
                continue;
            }
            if (document instanceof StructureDocument structure) {
                TerrainProjectContentIndex.Entry<StructureDocument> savedEntry =
                        savedIndex.structuresById().get(structure.id());
                if (savedEntry != null
                        && !sameSource(open.sourcePath(), savedEntry.sourcePath())) {
                    ambiguousStructures.add(structure.id());
                    nextProblems.add(differentSourceProblem(structure.id(), savedEntry.sourcePath()));
                    continue;
                }
                nextStructures.put(structure.id(), structure);
            } else if (document instanceof LevelDocument level) {
                TerrainProjectContentIndex.Entry<LevelDocument> savedEntry =
                        savedIndex.levelsById().get(level.id());
                if (savedEntry != null
                        && !sameSource(open.sourcePath(), savedEntry.sourcePath())) {
                    ambiguousLevels.add(level.id());
                    nextProblems.add(differentSourceProblem(level.id(), savedEntry.sourcePath()));
                    continue;
                }
                nextLevels.put(level.id(), level);
            }
        }
        this.structures = Map.copyOf(nextStructures);
        this.levels = Map.copyOf(nextLevels);
        this.problems = List.copyOf(nextProblems);
        this.ambiguousStructureIds = Set.copyOf(ambiguousStructures);
        this.ambiguousLevelIds = Set.copyOf(ambiguousLevels);
        this.savedStructureShadows = List.copyOf(shadowedStructures);
        this.savedLevelShadows = List.copyOf(shadowedLevels);
    }

    public List<ValidationProblem> problems() { return problems; }

    @Override public StructureDocument findStructure(String id) {
        if (ambiguousStructureIds.contains(id)) return null;
        StructureDocument open = structures.get(id);
        if (open != null) return open;
        StructureDocument savedValue = saved.findStructure(id);
        return shadowedValue(id, savedValue, savedStructureShadows);
    }

    @Override public LevelDocument findLevel(String id) {
        if (ambiguousLevelIds.contains(id)) return null;
        LevelDocument open = levels.get(id);
        if (open != null) return open;
        LevelDocument savedValue = saved.findLevel(id);
        return shadowedValue(id, savedValue, savedLevelShadows);
    }

    private static ValidationProblem differentSourceProblem(String id, Path savedPath) {
        return new ValidationProblem(ValidationProblem.Severity.ERROR, "$.id",
                "Open document ID '" + id + "' conflicts with a different saved source "
                        + savedPath);
    }

    private static <T extends TerrainSourceDocument> void markShadowed(
            Path openPath,
            T openDocument,
            Map<String, TerrainProjectContentIndex.Entry<T>> savedEntries,
            Collection<SavedShadow<T>> shadowed) {
        if (openPath == null) return;
        for (Map.Entry<String, TerrainProjectContentIndex.Entry<T>> entry
                : savedEntries.entrySet()) {
            if (sameSource(openPath, entry.getValue().sourcePath())) {
                shadowed.add(new SavedShadow<>(entry.getKey(),
                        entry.getValue().document(), openDocument));
            }
        }
    }

    /**
     * Resolves path aliases through the open replacement instead of returning frozen saved bytes.
     * The canonical old ID is deliberately removed when an open document has been renamed, while
     * path aliases continue to denote that source file and therefore resolve to its open value.
     */
    private static <T extends TerrainSourceDocument> T shadowedValue(
            String lookupId,
            T savedValue,
            List<SavedShadow<T>> shadows) {
        if (savedValue == null) return null;
        for (SavedShadow<T> shadow : shadows) {
            if (shadow.savedDocument() != savedValue) continue;
            if (lookupId.equals(shadow.savedCanonicalId())
                    && !lookupId.equals(shadow.openDocument().id())) {
                return null;
            }
            return shadow.openDocument();
        }
        return savedValue;
    }

    private record SavedShadow<T extends TerrainSourceDocument>(
            String savedCanonicalId, T savedDocument, T openDocument) { }

    private static boolean sameSource(Path left, Path right) {
        if (left == null || right == null) return false;
        Path a = left.toAbsolutePath().normalize();
        Path b = right.toAbsolutePath().normalize();
        if (a.equals(b)) return true;
        try {
            return java.nio.file.Files.exists(a) && java.nio.file.Files.exists(b)
                    && java.nio.file.Files.isSameFile(a, b);
        } catch (java.io.IOException unavailable) {
            return false;
        }
    }
}
