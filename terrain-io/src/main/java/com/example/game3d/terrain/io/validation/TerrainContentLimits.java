package com.example.game3d.terrain.io.validation;

/**
 * Hard authoring/runtime limits that keep source loading and synchronous command capture bounded.
 *
 * <p>The gameplay built-ins are far below these ceilings (the largest parity-locked level has
 * 432 tiles). Limits apply to drafts as validation errors and to resolved/published content as
 * strict load failures.</p>
 */
public final class TerrainContentLimits {
    public static final int MAX_CATALOG_ENTRIES = 256;
    public static final int MAX_LEVEL_ENTRIES = 256;
    public static final int MAX_STRUCTURE_TILES = 4096;
    public static final int MAX_STRUCTURE_ADDONS = 4096;
    public static final int MAX_RESOLVED_LEVELS = 256;
    public static final int MAX_RESOLVED_STRUCTURES = 256;
    public static final int MAX_RESOLVED_TILES = 8192;
    public static final int MAX_RESOLVED_ADDONS = 8192;
    public static final int MAX_PUBLISHED_CUSTOM_TILES = 65536;
    public static final int MAX_PUBLISHED_CUSTOM_ADDONS = 32768;
    public static final int MAX_REFERENCE_DEPTH = 64;

    private TerrainContentLimits() {
    }
}
