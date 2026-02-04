package com.example.game3d_opengl.game.player.player_logic;

import static com.example.game3d_opengl.game.player.player_character.PlayerConfig.INITIAL_DIRECTION_X;
import static com.example.game3d_opengl.game.player.player_character.PlayerConfig.INITIAL_DIRECTION_Y;
import static com.example.game3d_opengl.game.player.player_character.PlayerConfig.INITIAL_DIRECTION_Z;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import com.example.game3d_opengl.game.logic_abstraction.StateInfoNode;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Tile;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.util.List;

public final class InputNode extends StateInfoNode<InputNode.Data> {
    public static final class Data {
        public float dtMillis;
        public Vector3D position;
        public float swipeDx;
        public float swipeDy;
        public boolean wantsJump;
    }

    private Data data;
    // Persistent (cross-frame) values
    private Vector3D dir = new Vector3D(INITIAL_DIRECTION_X, INITIAL_DIRECTION_Y, INITIAL_DIRECTION_Z);
    private Vector3D lastMove = V3(0, 0, 0);
    private float fallSpeed = 0f;
    private Tile tileBelow = null;
    private Vector3D[] collisionTriangle = null;
    private long nearestTileId = -1;
    private float stickyRotationTime = 0f;
    private float stickyRotationAng = 0f;

    @Override
    public void setData(Data what) {
        this.data = what;
    }

    @Override
    public Data getData() {
        return data;
    }

    @Override
    public void calc() {
        // Input node is a passive data source for now.
    }

    public Vector3D getDir() {
        return dir;
    }

    public void setDir(Vector3D dir) {
        this.dir = dir;
    }

    public Vector3D getLastMove() {
        return lastMove;
    }

    public void setLastMove(Vector3D lastMove) {
        this.lastMove = lastMove;
    }

    public float getFallSpeed() {
        return fallSpeed;
    }

    public void setFallSpeed(float fallSpeed) {
        this.fallSpeed = fallSpeed;
    }

    public Tile getTileBelow() {
        return tileBelow;
    }

    public void setTileBelow(Tile tileBelow) {
        this.tileBelow = tileBelow;
        if (tileBelow != null) {
            nearestTileId = Math.max(nearestTileId, tileBelow.getID());
        }
    }

    public Vector3D[] getCollisionTriangle() {
        return collisionTriangle;
    }

    public void setCollisionTriangle(Vector3D[] collisionTriangle) {
        this.collisionTriangle = collisionTriangle;
    }

    public long getNearestTileId() {
        return nearestTileId;
    }

    public void setNearestTileId(long nearestTileId) {
        this.nearestTileId = nearestTileId;
    }

    public float getStickyRotationTime() {
        return stickyRotationTime;
    }

    public void setStickyRotationTime(float stickyRotationTime) {
        this.stickyRotationTime = stickyRotationTime;
    }

    public float getStickyRotationAng() {
        return stickyRotationAng;
    }

    public void setStickyRotationAng(float stickyRotationAng) {
        this.stickyRotationAng = stickyRotationAng;
    }

    public void resetFrame() {
        tileBelow = null;
        collisionTriangle = null;
    }
}

