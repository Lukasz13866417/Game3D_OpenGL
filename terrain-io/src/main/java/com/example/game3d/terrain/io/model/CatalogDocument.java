package com.example.game3d.terrain.io.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CatalogDocument implements TerrainSourceDocument {
    private final int formatVersion;
    private final String id;
    private final List<CatalogEntry> entries;

    public CatalogDocument(int formatVersion, String id, List<CatalogEntry> entries) {
        this.formatVersion = formatVersion;
        this.id = Objects.requireNonNull(id, "id");
        this.entries = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(entries, "entries")));
    }

    @Override public int formatVersion() { return formatVersion; }
    @Override public String id() { return id; }
    public List<CatalogEntry> entries() { return entries; }
}
