package com.example.game3d.terrain.io.resolve;

import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.LevelEntry;
import com.example.game3d.terrain.io.model.StructureDocument;
import com.example.game3d.terrain.io.validation.TerrainContentLimits;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Resolves references once into an immutable list and detects recursive level composition. */
public final class TerrainReferenceResolver {
    public ResolvedLevel resolve(LevelDocument level, TerrainDocumentRepository repository)
            throws ResolutionException {
        List<ResolvedStructureOccurrence> out =
                new ArrayList<ResolvedStructureOccurrence>();
        List<LevelDocument> levels = new ArrayList<LevelDocument>();
        List<String> entryPath = new ArrayList<String>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> active = new HashSet<>();
        ResolutionSize size = new ResolutionSize();
        resolveInto(level, repository, out, levels, entryPath, stack, active, size);
        return new ResolvedLevel(level, levels, out);
    }

    private void resolveInto(LevelDocument level, TerrainDocumentRepository repository,
                             List<ResolvedStructureOccurrence> out,
                             List<LevelDocument> levels, List<String> entryPath,
                             Deque<String> stack, Set<String> active,
                             ResolutionSize size)
            throws ResolutionException {
        if (stack.size() >= TerrainContentLimits.MAX_REFERENCE_DEPTH) {
            throw new ResolutionException("Level reference depth exceeds "
                    + TerrainContentLimits.MAX_REFERENCE_DEPTH);
        }
        if (!active.add(level.id())) {
            StringBuilder cycle = new StringBuilder();
            for (String id : stack) cycle.append(id).append(" -> ");
            cycle.append(level.id());
            throw new ResolutionException("Level reference cycle: " + cycle);
        }
        size.levels++;
        if (size.levels > TerrainContentLimits.MAX_RESOLVED_LEVELS
                || level.entries().size() > TerrainContentLimits.MAX_LEVEL_ENTRIES) {
            throw new ResolutionException("Resolved level expansion exceeds terrain content limits");
        }
        levels.add(level);
        stack.addLast(level.id());
        for (LevelEntry entry : level.entries()) {
            entryPath.add(entry.sourceId());
            if (entry.kind() == LevelEntry.Kind.INLINE_STRUCTURE) {
                addStructure(entry.inlineStructure(), entryPath, out, size);
            } else if (entry.kind() == LevelEntry.Kind.STRUCTURE_REFERENCE) {
                StructureDocument structure = repository.findStructure(entry.structureRef());
                if (structure == null) throw new ResolutionException(
                        "Missing structure '" + entry.structureRef() + "' referenced by level '" + level.id() + "'");
                addStructure(structure, entryPath, out, size);
            } else {
                LevelDocument child = repository.findLevel(entry.levelRef());
                if (child == null) throw new ResolutionException(
                        "Missing level '" + entry.levelRef() + "' referenced by level '" + level.id() + "'");
                resolveInto(child, repository, out, levels, entryPath,
                        stack, active, size);
            }
            entryPath.remove(entryPath.size() - 1);
        }
        stack.removeLast();
        active.remove(level.id());
    }

    private static void addStructure(
            StructureDocument structure, List<String> entryPath,
            List<ResolvedStructureOccurrence> out,
            ResolutionSize size) throws ResolutionException {
        size.structures++;
        size.tiles += structure.tiles().size();
        size.addons += structure.addons().size();
        if (size.structures > TerrainContentLimits.MAX_RESOLVED_STRUCTURES
                || size.tiles > TerrainContentLimits.MAX_RESOLVED_TILES
                || size.addons > TerrainContentLimits.MAX_RESOLVED_ADDONS) {
            throw new ResolutionException(
                    "Resolved level exceeds terrain content limits");
        }
        out.add(new ResolvedStructureOccurrence(entryPath, structure));
    }

    private static final class ResolutionSize {
        int levels;
        int structures;
        long tiles;
        long addons;
    }
}
