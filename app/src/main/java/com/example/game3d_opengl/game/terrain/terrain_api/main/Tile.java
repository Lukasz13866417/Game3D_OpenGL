
package com.example.game3d_opengl.game.terrain.terrain_api.main;

import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import androidx.annotation.NonNull;

import com.example.game3d_opengl.game.PlayerInteractable;
import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.player.player_logic.PlayerSupportSurface;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Represents a single tile in the terrain system.
 * Each tile is defined by four corner vertices and contains the geometry
 * needed for rendering and collision detection.
 */
public class Tile implements PlayerInteractable, PlayerSupportSurface {
    public static final int TRIANGLE_COUNT = 2;
    public static final int TRIANGLE_VERTEX_COUNT = 3;

    private final long id;
    private final TileProfile profile;
    private final float brightnessMultiplier;

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

    public TileProfile getProfile() {
        return profile;
    }

    public float getHorizontalSpeedMultiplier() {
        return profile.getHorizontalSpeedMultiplier();
    }

    @Override
    public float applyHorizontalSpeed(float baseSpeed) {
        return profile.applyHorizontalSpeed(baseSpeed);
    }

    public float getBrightnessMultiplier() {
        return brightnessMultiplier;
    }

    /**
     * All four corners of this tile:
     * nearLeft, nearRight = "close edge" (closer to player)
     * farLeft,  farRight  = "far edge" (farther from player)
     * These vertices are already de-facto in world space.
     * The terrain doesn't move, the player does. And the camera follows him around.
     */
    public Vector3D nearLeft, nearRight, farLeft, farRight;
    
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
    public Tile(
            Vector3D nl,
            Vector3D nr,
            Vector3D fl,
            Vector3D fr,
            long l,
            boolean isEmptySegment,
            TileProfile profile
    ) {
        this(nl, nr, fl, fr, l, isEmptySegment, profile,
                profile != null ? profile.getBrightnessMultiplier() : TileProfile.NORMAL.getBrightnessMultiplier());
    }

    public Tile(
            Vector3D nl,
            Vector3D nr,
            Vector3D fl,
            Vector3D fr,
            long l,
            boolean isEmptySegment,
            TileProfile profile,
            float brightnessMultiplier
    ) {
        this.nearLeft = nl;
        this.nearRight = nr;
        this.farLeft = fl;
        this.farRight = fr;
        this.id = l;
        this.isEmptySegment = isEmptySegment;
        this.profile = profile != null ? profile : TileProfile.NORMAL;
        this.brightnessMultiplier = brightnessMultiplier;
    }

    public void rebasePosition(Vector3D delta) {
        if (delta == null) return;
        nearLeft = nearLeft.add(delta);
        nearRight = nearRight.add(delta);
        farLeft = farLeft.add(delta);
        farRight = farRight.add(delta);
    }

    public int getTriangleCount() {
        return TRIANGLE_COUNT;
    }

    public Vector3D getTriangleVertex(int triangleIndex, int vertexIndex) {
        switch (triangleIndex) {
            case 0:
                switch (vertexIndex) {
                    case 0:
                        return nearLeft;
                    case 1:
                        return nearRight;
                    case 2:
                        return farRight;
                    default:
                        break;
                }
                break;
            case 1:
                switch (vertexIndex) {
                    case 0:
                        return nearLeft;
                    case 1:
                        return farRight;
                    case 2:
                        return farLeft;
                    default:
                        break;
                }
                break;
            default:
                break;
        }
        throw new IndexOutOfBoundsException(
                "triangleIndex=" + triangleIndex + ", vertexIndex=" + vertexIndex
        );
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
    public void accept(Player player) {
        player.interactWith((PlayerSupportSurface) this);
    }


}
