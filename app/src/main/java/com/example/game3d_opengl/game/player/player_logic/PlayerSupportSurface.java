package com.example.game3d_opengl.game.player.player_logic;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Minimal geometric support contract used by the old Android player diagnostic pipeline.
 *
 * <p>The canonical gameplay simulation does not use this interface. Keeping the probe contract in
 * the player package prevents the production player/presentation class from depending on the
 * retired mutable terrain implementation.</p>
 */
public interface PlayerSupportSurface {
    long getID();

    boolean isEmptySegment();

    int getTriangleCount();

    Vector3D getTriangleVertex(int triangleIndex, int vertexIndex);

    float applyHorizontalSpeed(float baseSpeed);
}
