package com.example.game3d.terrain.io.publish;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RuntimeCatalog {
    private final int formatVersion;
    private final String sourceCatalogId;
    private final List<RuntimeCatalogEntry> entries;

    public RuntimeCatalog(int formatVersion, String sourceCatalogId, List<RuntimeCatalogEntry> entries) {
        this.formatVersion = formatVersion;
        this.sourceCatalogId = Objects.requireNonNull(sourceCatalogId, "sourceCatalogId");
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public int formatVersion() { return formatVersion; }
    public String sourceCatalogId() { return sourceCatalogId; }
    public List<RuntimeCatalogEntry> entries() { return entries; }
}
