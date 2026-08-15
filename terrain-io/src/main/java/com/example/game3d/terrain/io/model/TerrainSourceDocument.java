package com.example.game3d.terrain.io.model;

/** A versioned terrain source document. */
public interface TerrainSourceDocument {
    int CURRENT_FORMAT_VERSION = 1;

    int formatVersion();

    String id();
}
