package com.example.game3d_opengl.game.terrain.track_elements.portal;

public final class PortalConfig {
    /** Random level index at which portal encounters may start appearing. */
    public static final int PORTAL_UNLOCK_LEVEL_INDEX = 4;

    /** Chance (0..1) for an unlocked random level to contain a portal encounter. */
    public static final float PORTAL_LEVEL_CHANCE = 0.25f;

    /** Current encounter policy enables at most one portal section per random level. */
    public static final int MAX_PORTAL_SECTIONS_PER_LEVEL = 1;

    /**
     * Default number of horizontal grid cells reserved for a portal region.
     * Terrain structures can use this when selecting segment lengths.
     */
    public static final int CELLS_PER_PORTAL_SEGMENT = 3;

    /**
     * Number of rows owned by the dedicated exit-portal child structure.
     * These rows are reserved so external addons cannot spawn inside the exit zone.
     */
    public static final int EXIT_STRUCTURE_ROWS = 16;

    /**
     * Required minimum row distance between the exit row and entrance row.
     * With the current exit structure layout this is guaranteed by construction.
     */
    public static final int MIN_ENTRANCE_EXIT_ROW_GAP = 12;

    /** Default portal diameter in world units (bounding sphere). */
    static final float DEFAULT_WIDTH_WORLD = 1f;
    /** Clearance above terrain surface. */
    static final float BASE_CLEARANCE = 1.22f + 0.5f;
    static final float MIN_REGION_WIDTH = 0.1f;

    // Beacon visual settings
    static final float PRIMARY_ROTATION_SPEED_RAD_PER_SEC = 0.32f;
    static final float SECONDARY_ROTATION_SPEED_RAD_PER_SEC = -0.47f;
    static final float SHELL_ALPHA = 0.34f;
    static final float SHELL_AMBIENT = 0.18f;
    static final float SHELL_DIFFUSE = 0.62f;
    static final float SHELL_SPECULAR = 0.18f;
    static final float SHELL_SHININESS = 18.0f;
    static final float SHELL_DARK_FACE_FACTOR = 0.72f;
    static final float SHELL_WHITE_MIX = 0.18f;
    static final float WIREFRAME_PIXEL_WIDTH = 2.25f;
    static final float WIREFRAME_WHITE_MIX = 0.55f;
    static final float WIREFRAME_BRIGHTNESS = 1.15f;
    static final float CORE_RADIUS_FACTOR = 0.34f;
    static final float CORE_AMBIENT = 1.0f;
    static final float CORE_DIFFUSE = 0.10f;
    static final float CORE_SPECULAR = 0.08f;
    static final float CORE_SHININESS = 28.0f;
    static final float CORE_WHITE_MIX = 0.78f;
    static final float CORE_BRIGHTNESS = 1.30f;

    private PortalConfig() {}
}
