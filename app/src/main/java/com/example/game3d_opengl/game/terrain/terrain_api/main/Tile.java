
package com.example.game3d_opengl.game.terrain.terrain_api.main;

import androidx.annotation.NonNull;

import com.example.game3d_opengl.game.PlayerInteractable;
import com.example.game3d_opengl.game.player.Player;
import com.example.game3d_opengl.game.player.player_state.infos.jump.PlayerJumpInfo;
import com.example.game3d_opengl.game.util.GameMath;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Represents a single tile in the terrain system.
 * Each tile is defined by four corner vertices and contains the geometry
 * needed for rendering and collision detection.
 */
public class Tile implements PlayerInteractable {
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

    @Override
    public void interactWithPlayer(Player.InteractableAPI api) {
        if (isEmptySegment()) return;
        // do collision test against both triangles using player position and triangle normals
        Vector3D playerPos = new Vector3D(api.getPlayerX(), api.getPlayerY(), api.getPlayerZ());
        // Prepare jump info when collision is detected
        if (collidesPlayer(playerPos)) {
            PlayerJumpInfo.PlayerHasFooting info =
                    new PlayerJumpInfo.PlayerHasFooting(this, triangles);
            api.addInfo(info);
        }
    }

    private boolean collidesPlayer(Vector3D playerPos){
        // For a collision, cast ray along inverted normal direction towards the tile and test distance
        Vector3D n1 = GameMath.getNormal(triangles[0]);
        Vector3D n2 = GameMath.getNormal(triangles[1]);
        float d1 = GameMath.rayTriangleDistance(
                playerPos,
                n1.mult(-Math.signum(n1.y)),
                triangles[0][0], triangles[0][1], triangles[0][2]
        );
        if (!Float.isInfinite(d1) && d1 / Player.PLAYER_HEIGHT < 1.05f) return true;
        float d2 = GameMath.rayTriangleDistance(
                playerPos,
                n2.mult(-Math.signum(n2.y)),
                triangles[1][0], triangles[1][1], triangles[1][2]
        );
        return !Float.isInfinite(d2) && d2 / Player.PLAYER_HEIGHT < 1.05f;
    }
}