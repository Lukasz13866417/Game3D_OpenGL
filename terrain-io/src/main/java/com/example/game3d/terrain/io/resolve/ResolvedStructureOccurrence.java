package com.example.game3d.terrain.io.resolve;

import com.example.game3d.terrain.io.model.StructureDocument;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** One resolved structure instance, identified by its complete level-entry path. */
public final class ResolvedStructureOccurrence {
    private final List<String> entrySourcePath;
    private final String occurrenceKey;
    private final StructureDocument structure;

    public ResolvedStructureOccurrence(
            List<String> entrySourcePath, StructureDocument structure) {
        if (entrySourcePath == null || entrySourcePath.isEmpty() || structure == null) {
            throw new IllegalArgumentException(
                    "A resolved structure occurrence needs a non-empty entry path");
        }
        ArrayList<String> path = new ArrayList<String>(entrySourcePath.size());
        StringBuilder key = new StringBuilder();
        for (String sourceId : entrySourcePath) {
            if (sourceId == null || sourceId.isEmpty()) {
                throw new IllegalArgumentException("An occurrence entry path is empty");
            }
            if (key.length() > 0) key.append('/');
            key.append(sourceId);
            path.add(sourceId);
        }
        this.entrySourcePath = Collections.unmodifiableList(path);
        this.occurrenceKey = key.toString();
        this.structure = structure;
    }

    public List<String> entrySourcePath() {
        return entrySourcePath;
    }

    public String occurrenceKey() {
        return occurrenceKey;
    }

    public StructureDocument structure() {
        return structure;
    }

    /** Collision-free tooling key that still exposes the authored local source UUID. */
    public String sourceKey(String localSourceId) {
        if (localSourceId == null || localSourceId.isEmpty()) {
            throw new IllegalArgumentException("localSourceId is empty");
        }
        return occurrenceKey + "/" + localSourceId;
    }

    /** Stable UUID used inside a self-contained flattened runtime occurrence. */
    public String namespacedSourceId(String localSourceId) {
        return UUID.nameUUIDFromBytes(
                ("terrain-occurrence:" + sourceKey(localSourceId))
                        .getBytes(StandardCharsets.UTF_8)).toString();
    }
}
