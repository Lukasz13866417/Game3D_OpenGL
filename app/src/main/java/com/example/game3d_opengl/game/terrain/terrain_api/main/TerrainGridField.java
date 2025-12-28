package com.example.game3d_opengl.game.terrain.terrain_api.main;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * A single terrain grid "cell" in world space, expressed with named corners.
 *
 * Naming matches {@link com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon#place}:
 * - nearLeft / nearRight: closer edge (towards the player)
 * - farLeft  / farRight : farther edge (away from the player)
 */
public final class TerrainGridField {
    public final Vector3D nearLeft;
    public final Vector3D nearRight;
    public final Vector3D farLeft;
    public final Vector3D farRight;

    public TerrainGridField(Vector3D nearLeft, Vector3D nearRight, Vector3D farLeft, Vector3D farRight) {
        this.nearLeft = nearLeft;
        this.nearRight = nearRight;
        this.farLeft = farLeft;
        this.farRight = farRight;
    }
}


