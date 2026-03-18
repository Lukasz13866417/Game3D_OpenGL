package com.example.game3d_opengl.game.terrain.track_elements.portal;

public final class PortalConfig {
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

    /** Default portal diameter in world units (bounding sphere of icosahedron). */
    static final float DEFAULT_WIDTH_WORLD = 1.5f;
    /** Clearance above terrain surface. */
    static final float BASE_CLEARANCE = 0.08f;
    static final float MIN_REGION_WIDTH = 0.1f;

    /** Chance (0..1) to place an entrance portal per structure. */
    public static final float ENTRANCE_PORTAL_CHANCE = 0.2f;

    // Icosahedron visual settings
    static final float ROTATION_SPEED_RAD_PER_SEC = 0.3f;
    static final float FILL_AMBIENT = 0.03f;
    static final float FILL_DIFFUSE = 1.0f;
    static final float FILL_SPECULAR = 0.6f;
    static final float FILL_SHININESS = 10.0f;
    static final float DARK_FACE_FACTOR = 0.55f;
    static final float WIREFRAME_PIXEL_WIDTH = 1.4f;

    private PortalConfig() {}
}
