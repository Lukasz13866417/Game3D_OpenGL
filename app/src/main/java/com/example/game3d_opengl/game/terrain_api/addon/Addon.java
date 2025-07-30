package com.example.game3d_opengl.game.terrain_api.addon;

import static com.example.game3d_opengl.rendering.util3d.GameMath.getCentroid;

import com.example.game3d_opengl.game.terrain_api.TerrainElement;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public abstract class Addon implements TerrainElement {
    private long tileId = -1L;

    /**
     * Assign the owning tile's ID
     */
    public void setTileId(long tileId) {
        this.tileId = tileId;
    }

    public Addon() {
        this.ready = false;
    }

    public void place(Vector3D fieldNearLeft,
                      Vector3D fieldNearRight,
                      Vector3D fieldFarLeft,
                      Vector3D fieldFarRight) {
        assert !ready;
        onPlace(
                fieldNearLeft,
                fieldNearRight,
                fieldFarLeft,
                fieldFarRight
        );
        ready = true;
    }

    @Override
    public boolean isGoneBy(long playerTileID) {
        assert ready;
        return playerTileID - tileId > 50L;
    }

    protected abstract void onPlace(Vector3D fieldNearLeft,
                                    Vector3D fieldNearRight,
                                    Vector3D fieldFarLeft,
                                    Vector3D fieldFarRight);

    private boolean ready;

}
