package com.example.game3d.terrain.io.publish;

import com.google.gson.JsonElement;

import java.util.Objects;

public final class RuntimeCatalogEntry {
    private final String id;
    private final String digest;
    private final JsonElement compiledDefinition;

    public RuntimeCatalogEntry(String id, String digest, JsonElement compiledDefinition) {
        this.id = Objects.requireNonNull(id, "id");
        this.digest = Objects.requireNonNull(digest, "digest");
        this.compiledDefinition = Objects.requireNonNull(compiledDefinition, "compiledDefinition").deepCopy();
    }

    public String id() { return id; }
    public String digest() { return digest; }
    public JsonElement compiledDefinition() { return compiledDefinition.deepCopy(); }
}
