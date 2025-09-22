
package com.example.game3d_opengl.game.terrain_api.main;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static java.lang.Math.min;

import androidx.annotation.NonNull;

import com.example.game3d_opengl.game.terrain_api.TerrainElement;
import com.example.game3d_opengl.rendering.object3d.BasicPolygon3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Represents a single tile in the terrain system.
 * Each tile is defined by four corner vertices and contains the geometry
 * needed for rendering and collision detection.
 */
public class Tile {
    // Constants for magic numbers
    private static final long TILE_REMOVAL_THRESHOLD = 50L;

    private final long id;

    public long getID() {
        return id;
    }

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
     * Constructs a Tile using 4 corners plus slope.
     * The Polygon3D is created separately via factory method.
     *\
     * @param nl near-left corner vertex
     * @param nr near-right corner vertex  
     * @param fl far-left corner vertex
     * @param fr far-right corner vertex
     * @param l unique identifier for this tile
     * @param isEmptySegment whether this tile represents empty space
     */
    Tile(Vector3D nl, Vector3D nr, Vector3D fl, Vector3D fr, long l, boolean isEmptySegment) {
        this.nearLeft = nl;
        this.nearRight = nr;
        this.farLeft = fl;
        this.farRight = fr;
        this.id = l;
        this.isEmptySegment = isEmptySegment;

        // Create the two triangles that make up this tile
        // This allows for proper collision detection and physics
        this.triangles = new Vector3D[][]{
                new Vector3D[]{this.nearLeft,this.nearRight,this.farRight},
                new Vector3D[]{this.nearLeft,this.farLeft,this.farRight}
        };
    }

    @NonNull
    @Override
    public String toString() {
        return "TILE["
                + "NEAR L=" + nearLeft + ", R=" + nearRight+"\n"
                + "FAR  L=" + farLeft  + ", R=" + farRight+"\n"
                + "]";
    }

    /**
     * Determines if this tile should be removed based on player distance.
     * Tiles are removed when they are far behind the player to manage memory usage
     * and maintain performance. This prevents the terrain from growing indefinitely.
     * 
     * @param playerID the current player's tile ID
     * @return true if this tile should be removed
     */
    public boolean isGoneBy(long playerID) {
        return playerID - getID() > TILE_REMOVAL_THRESHOLD;
    }
}