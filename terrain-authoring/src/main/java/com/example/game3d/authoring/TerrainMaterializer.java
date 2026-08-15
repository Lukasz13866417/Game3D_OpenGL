package com.example.game3d.authoring;

import com.example.game3d.core.math.Vec3;

/** Stateless entry point used by validators and the desktop editor. */
public final class TerrainMaterializer {
    private TerrainMaterializer() {}

    public static MaterializedStructure materialize(
            BaseTerrainStructure<?> structure, TrackProfile profile,
            Vec3 startCenter, long seed) {
        return Terrain.materialize(structure, profile, startCenter, seed);
    }

    /**
     * Captures exact resolved tile commands from a fresh one-shot structure without publishing.
     * This is intended for editor import/export; simulation continues to consume snapshots.
     */
    public static CapturedStructureCommands captureResolvedCommands(
            BaseTerrainStructure<?> structure, TrackProfile profile, long seed) {
        return Terrain.captureResolvedCommands(structure, profile, seed);
    }

    /** Exact count of completed distance-spaced GRID rows for a fresh standalone structure. */
    public static int derivePhysicalGridRowCount(
            BaseTerrainStructure<?> structure, TrackProfile profile,
            Vec3 startCenter, long seed) {
        return Terrain.derivePhysicalGridRowCount(
                structure, profile, startCenter, seed);
    }
}
