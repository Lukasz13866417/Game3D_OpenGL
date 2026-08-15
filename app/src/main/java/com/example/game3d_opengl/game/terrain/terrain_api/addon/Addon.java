package com.example.game3d_opengl.game.terrain.terrain_api.addon;

import com.example.game3d_opengl.game.PlayerInteractable;
import com.example.game3d_opengl.game.terrain.terrain_api.TerrainElement;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * @deprecated Android-only mutable addon retained for legacy diagnostic stages. Production uses
 * renderer-neutral {@link com.example.game3d.core.terrain.addon.Addon} definitions.
 */
@Deprecated
public abstract class Addon implements TerrainElement, PlayerInteractable {
    /**
     * Assign the owning tile's ID
     */
    public void setTileId(long tileId) {
        this.tileId = tileId;
    }

    public long getTileId() {
        return tileId;
    }

    public Addon() {
        this.ready = false;
    }

    /**
     * Places this addon onto a terrain footprint defined by four corners.
     *
     * <p>The footprint may represent either a single grid cell or a larger
     * multi-cell region (for example, a horizontal segment region).
     */
    public void place(Vector3D fieldNearLeft,
                      Vector3D fieldNearRight,
                      Vector3D fieldFarLeft,
                      Vector3D fieldFarRight) {
        assert !ready;
        onPlace(fieldNearLeft, fieldNearRight, fieldFarLeft, fieldFarRight);
        ready = true;
    }

    public void place(float nearLeftX, float nearLeftY, float nearLeftZ,
                      float nearRightX, float nearRightY, float nearRightZ,
                      float farLeftX, float farLeftY, float farLeftZ,
                      float farRightX, float farRightY, float farRightZ) {
        assert !ready;
        onPlace(
                nearLeftX, nearLeftY, nearLeftZ,
                nearRightX, nearRightY, nearRightZ,
                farLeftX, farLeftY, farLeftZ,
                farRightX, farRightY, farRightZ
        );
        ready = true;
    }

    @Override
    public boolean isGoneBy(long playerTileID) {
        assert ready;
        return playerTileID - tileId > 50L;
    }

    protected void onPlace(Vector3D fieldNearLeft,
                           Vector3D fieldNearRight,
                           Vector3D fieldFarLeft,
                           Vector3D fieldFarRight) {
        onPlace(
                fieldNearLeft.x, fieldNearLeft.y, fieldNearLeft.z,
                fieldNearRight.x, fieldNearRight.y, fieldNearRight.z,
                fieldFarLeft.x, fieldFarLeft.y, fieldFarLeft.z,
                fieldFarRight.x, fieldFarRight.y, fieldFarRight.z
        );
    }

    protected abstract void onPlace(float nearLeftX, float nearLeftY, float nearLeftZ,
                                    float nearRightX, float nearRightY, float nearRightZ,
                                    float farLeftX, float farLeftY, float farLeftZ,
                                    float farRightX, float farRightY, float farRightZ);

    private boolean ready;
    private long tileId = -1L;

}
