package com.example.game3d.terrain.io.publish;

import java.util.Objects;

/** Compiler output: self-contained JSON plus SHA-256 of its Gson-normalized compact representation. */
public final class CompiledTerrainContent {
    private final String normalizedJson;
    private final String digest;

    public CompiledTerrainContent(String normalizedJson, String digest) {
        this.normalizedJson = Objects.requireNonNull(normalizedJson, "normalizedJson");
        this.digest = Objects.requireNonNull(digest, "digest");
    }

    public String normalizedJson() { return normalizedJson; }
    public String digest() { return digest; }
}
