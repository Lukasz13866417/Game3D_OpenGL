
package com.example.game3d_opengl.game.terrain_api;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static java.lang.Math.min;

import androidx.annotation.NonNull;

import com.example.game3d_opengl.rendering.object3d.BasicPolygon3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Represents a single tile in the terrain system.
 * Each tile is defined by four corner vertices and contains the geometry
 * needed for rendering and collision detection.
 */
public class Tile implements TerrainElement {
    // Constants for magic numbers
    private static final float DEFAULT_COLOR_ALPHA = 1.0f;
    private static final float BRIGHTNESS_MULTIPLIER_BASE = 1.0f;
    private static final float BRIGHTNESS_MULTIPLIER_SCALE = 3.0f;
    private static final float MAX_COLOR_VALUE = 1.0f;
    private static final float EMPTY_SEGMENT_DEBUG_ALPHA = 0.0f;
    private static final float EMPTY_SEGMENT_DEBUG_GREEN = 1.0f;
    private static final long TILE_REMOVAL_THRESHOLD = 50L;

    private final long id;

    public long getID() {
        return id;
    }

    /**
     * The slope of this tile. Computed once from its geometry.
     * Used for visual effects and gameplay mechanics.
     */
    public final float slope;

    /**
     * Returns whether this tile represents an empty segment (a gap in the terrain path).
     * Empty segments are used for spacing in terrain generation.
     */
    public boolean isEmptySegment() {
        return isEmptySegment;
    }

    private final boolean isEmptySegment;

    /**
     * All four corners of this tile:
     * nearLeft, nearRight = "close edge" (closer to player)
     * farLeft,  farRight  = "far edge" (farther from player)
     * These vertices are already de-facto in world space.
     * The terrain doesn't move, the player does. And the camera follows him around.
     */
    public final Vector3D nearLeft, nearRight, farLeft, farRight;
    
    /**
     * The two triangles that make up this tile's surface.
     * Triangle 0: nearLeft -> nearRight -> farRight
     * Triangle 1: nearLeft -> farLeft -> farRight
     * Used for collision detection and physics calculations.
     */
    public final Vector3D[][] triangles;

    /**
     * Polygon wrapper that handles the vertex buffer, shaders, and rendering for this tile.
     */
    private final BasicPolygon3D polygon3D;
    
    /**
     * Reusable color instance to avoid per-frame allocations.
     * This prevents creating new FColor objects every frame when updating tile colors.
     */
    private final FColor cachedColor = CLR(1,1,1,1);

    /**
     * Constructs a Tile using 4 corners plus slope.
     * The Polygon3D is created separately via factory method.
     * 
     * @param nl near-left corner vertex
     * @param nr near-right corner vertex  
     * @param fl far-left corner vertex
     * @param fr far-right corner vertex
     * @param slope the slope angle of this tile
     * @param l unique identifier for this tile
     * @param polygon3D the 3D polygon representation
     * @param isEmptySegment whether this tile represents empty space
     */
    private Tile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr, float slope, long l, BasicPolygon3D polygon3D, boolean isEmptySegment) {
        this.nearLeft = nl;
        this.nearRight = nr;
        this.farLeft = fl;
        this.farRight = fr;
        this.slope = slope;
        this.id = l;
        this.polygon3D = polygon3D;
        this.isEmptySegment = isEmptySegment;

        // Create the two triangles that make up this tile
        // This allows for proper collision detection and physics
        this.triangles = new Vector3D[][]{
            new Vector3D[]{this.nearLeft,this.nearRight,this.farRight},
            new Vector3D[]{this.nearLeft,this.farLeft,this.farRight}
        };
    }

    /**
     * Creates a Polygon3D representation of this tile's surface.
     * The polygon is built from the four corner vertices in a specific order:
     * nearLeft -> nearRight -> farRight -> farLeft
     * 
     * @param nl near-left corner vertex
     * @param nr near-right corner vertex
     * @param fl far-left corner vertex  
     * @param fr far-right corner vertex
     * @return a Polygon3D representing the tile surface
     */
    public static BasicPolygon3D makePolygon3D(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr) {
        // Build an array of perimeter cords: nearLeft -> nearRight -> farRight -> farLeft
        // The center will be computed automatically by Polygon3D
        float[] perimeterCoords = new float[]{
                nl.x, nl.y, nl.z,
                nr.x, nr.y, nr.z,
                fr.x, fr.y, fr.z,
                fl.x, fl.y, fl.z
        };

        // Use default colors that will be updated later via setTileColor
        FColor defaultColor = CLR(1,1,1,1);
        return new com.example.game3d_opengl.rendering.object3d.BasicPolygon3D.Builder()
                .fillColor(defaultColor)
                .edgeColor(defaultColor)
                .fromVertexData(perimeterCoords, false)
                .build();
    }
    
    /**
     * Factory method to create a new Tile instance.
     * This method handles the creation of the Polygon3D and ensures proper initialization.
     * 
     * @param nl near-left corner vertex
     * @param nr near-right corner vertex
     * @param fl far-left corner vertex
     * @param fr far-right corner vertex
     * @param slope the slope angle of this tile
     * @param l unique identifier for this tile
     * @param isEmpty whether this tile represents empty space
     * @return a new Tile instance
     */
    public static Tile createTile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr, float slope, long l, boolean isEmpty) {
        BasicPolygon3D polygon = makePolygon3D(nl, nr, fl, fr);
        return new Tile(nl, nr, fl, fr, slope, l, polygon, isEmpty);
    }

    /**
     * Computes a slope-based brightness color from the given colorTheme,
     * then applies it to the Polygon3D.
     * 
     * The brightness calculation uses a quadratic function based on the tile's slope
     * to create visual depth and highlight steep areas. Steeper slopes appear brighter,
     * creating a more dynamic and visually interesting terrain.
     * 
     * @param colorTheme the base color to apply brightness variations to
     */
    public void setTileColor(FColor colorTheme) {
        // Calculate brightness multiplier based on slope
        // Steeper slopes get brighter colors to create visual depth
        double brightnessMul = BRIGHTNESS_MULTIPLIER_BASE + BRIGHTNESS_MULTIPLIER_SCALE * slope * slope;

        float baseR = colorTheme.r();
        float baseG = colorTheme.g();
        float baseB = colorTheme.b();

        // Apply brightness multiplier while clamping to valid color range
        float finalR = (float) min(MAX_COLOR_VALUE, baseR * brightnessMul);
        float finalG = (float) min(MAX_COLOR_VALUE, baseG * brightnessMul);
        float finalB = (float) min(MAX_COLOR_VALUE, baseB * brightnessMul);

        // Update the cached color to avoid allocations
        cachedColor.rgba[0] = finalR;
        cachedColor.rgba[1] = finalG;
        cachedColor.rgba[2] = finalB;
        cachedColor.rgba[3] = DEFAULT_COLOR_ALPHA;

        if(!isEmptySegment) {
            // Set both fill and outline to the computed color
            polygon3D.setFillAndOutline(cachedColor, cachedColor);
        }else{
            // Empty segments get transparent fill and green outline for debugging
            polygon3D.setFillAndOutline(
                new FColor(0, 0, 0, EMPTY_SEGMENT_DEBUG_ALPHA), 
                new FColor(0, EMPTY_SEGMENT_DEBUG_GREEN, 0, DEFAULT_COLOR_ALPHA)
            );
        }
    }

    /**
     * Delegate the draw call to polygon3D.
     * Only renders non-empty tiles to avoid visual clutter.
     *
     * @param mvpMatrix The combined Model-View-Projection matrix.
     */
    @Override
    public void draw(float[] mvpMatrix) {
        if(!isEmptySegment) {
            polygon3D.draw(mvpMatrix);
        }
        // Empty segments are not rendered to avoid visual clutter
    }

    @NonNull
    @Override
    public String toString() {
        return "TILE["
                + "NEAR L=" + nearLeft + ", R=" + nearRight+"\n"
                + "FAR  L=" + farLeft  + ", R=" + farRight+"\n"
                + "]";
    }

    @Override
    public void updateBeforeDraw(float dt) {
        // Tiles are static and don't require per-frame updates
    }

    @Override
    public void updateAfterDraw(float dt) {
        // Tiles are static and don't require post-render updates
    }

    @Override
    public void cleanupGPUResources() {
        polygon3D.cleanup();
    }

    @Override
    public void resetGPUResources() {
        polygon3D.reload();
    }

    /**
     * Determines if this tile should be removed based on player distance.
     * Tiles are removed when they are far behind the player to manage memory usage
     * and maintain performance. This prevents the terrain from growing indefinitely.
     * 
     * @param playerID the current player's tile ID
     * @return true if this tile should be removed
     */
    @Override
    public boolean isGoneBy(long playerID) {
        return playerID - getID() > TILE_REMOVAL_THRESHOLD;
    }
}