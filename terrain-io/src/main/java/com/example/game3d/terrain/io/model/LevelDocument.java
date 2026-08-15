package com.example.game3d.terrain.io.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class LevelDocument implements TerrainSourceDocument {
    private final int formatVersion;
    private final String id;
    private final String sessionProfileId;
    private final List<LevelEntry> entries;

    public LevelDocument(int formatVersion, String id, String sessionProfileId, List<LevelEntry> entries) {
        this.formatVersion = formatVersion;
        this.id = Objects.requireNonNull(id, "id");
        this.sessionProfileId = Objects.requireNonNull(sessionProfileId, "sessionProfileId");
        this.entries = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(entries, "entries")));
    }

    @Override public int formatVersion() { return formatVersion; }
    @Override public String id() { return id; }
    public String sessionProfileId() { return sessionProfileId; }
    public List<LevelEntry> entries() { return entries; }

    public LevelDocument withEntries(List<LevelEntry> value) {
        return new LevelDocument(formatVersion, id, sessionProfileId, value);
    }
}
