package com.example.game3d.terrain.io.resolve;

import com.example.game3d.terrain.io.model.LevelDocument;
import com.example.game3d.terrain.io.model.StructureDocument;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ResolvedLevel {
    private final LevelDocument source;
    private final List<LevelDocument> levels;
    private final List<ResolvedStructureOccurrence> occurrences;
    private final List<StructureDocument> structures;

    public ResolvedLevel(LevelDocument source, List<StructureDocument> structures) {
        this(source, Collections.singletonList(source),
                legacyOccurrences(source, structures));
    }

    public ResolvedLevel(LevelDocument source, List<LevelDocument> levels,
                         List<ResolvedStructureOccurrence> occurrences) {
        this.source = source;
        this.levels = Collections.unmodifiableList(new ArrayList<>(levels));
        this.occurrences = Collections.unmodifiableList(
                new ArrayList<ResolvedStructureOccurrence>(occurrences));
        ArrayList<StructureDocument> resolvedStructures =
                new ArrayList<StructureDocument>(occurrences.size());
        for (ResolvedStructureOccurrence occurrence : occurrences) {
            resolvedStructures.add(occurrence.structure());
        }
        this.structures = Collections.unmodifiableList(resolvedStructures);
    }

    public LevelDocument source() { return source; }
    /** Root followed by referenced level expansions in deterministic traversal order. */
    public List<LevelDocument> levels() { return levels; }
    public List<ResolvedStructureOccurrence> occurrences() { return occurrences; }
    public List<StructureDocument> structures() { return structures; }

    private static List<ResolvedStructureOccurrence> legacyOccurrences(
            LevelDocument source, List<StructureDocument> structures) {
        ArrayList<ResolvedStructureOccurrence> result =
                new ArrayList<ResolvedStructureOccurrence>(structures.size());
        for (int i = 0; i < structures.size(); i++) {
            String entryId = i < source.entries().size()
                    ? source.entries().get(i).sourceId() : "resolved-" + i;
            result.add(new ResolvedStructureOccurrence(
                    Collections.singletonList(entryId), structures.get(i)));
        }
        return result;
    }
}
