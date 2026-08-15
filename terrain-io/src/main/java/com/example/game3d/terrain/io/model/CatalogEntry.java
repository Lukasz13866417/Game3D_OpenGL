package com.example.game3d.terrain.io.model;

import java.util.Objects;

public final class CatalogEntry {
    public enum Kind { JAVA_PROVIDER, JSON_LEVEL }

    private final String id;
    private final Kind kind;
    private final String location;
    private final boolean enabled;

    public CatalogEntry(String id, Kind kind, String location, boolean enabled) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.location = Objects.requireNonNull(location, "location");
        this.enabled = enabled;
    }

    public String id() { return id; }
    public Kind kind() { return kind; }
    public String location() { return location; }
    public boolean enabled() { return enabled; }
}
