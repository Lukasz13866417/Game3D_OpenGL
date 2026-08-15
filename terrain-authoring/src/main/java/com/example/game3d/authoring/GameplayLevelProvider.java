package com.example.game3d.authoring;

/** Creates a fresh one-shot structure for one deterministic gameplay-level ordinal. */
public interface GameplayLevelProvider {
    String stableId();

    BaseTerrainStructure<?> create(long levelOrdinal);
}
