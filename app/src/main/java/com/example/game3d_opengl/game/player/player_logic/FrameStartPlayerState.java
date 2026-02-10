package com.example.game3d_opengl.game.player.player_logic;

import static com.example.game3d_opengl.game.util.GameMath.getNormal;
import static com.example.game3d_opengl.game.util.GameMath.rayTriangleDistance;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import com.example.game3d_opengl.game.player.player_character.PlayerConfig;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Tile;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class FrameStartPlayerState {
    public float dtMillis;
    public Vector3D position;
    public float swipeDx;
    public float swipeDy;
    public boolean isTouchUp = true;

    private Vector3D dir;
    private Vector3D moveDir;
    private Vector3D lastMove = V3(0, 0, 0);
    private float fallSpeed = 0f;
    private Tile tileBelow = null;
    private Vector3D[] collisionTriangle = null;
    private float nearestGroundDistance = Float.POSITIVE_INFINITY;
    private static final int MAX_NEARBY_DEATH_SPIKES = 12;
    private final float[] nearbySpikeX = new float[MAX_NEARBY_DEATH_SPIKES];
    private final float[] nearbySpikeY = new float[MAX_NEARBY_DEATH_SPIKES];
    private final float[] nearbySpikeZ = new float[MAX_NEARBY_DEATH_SPIKES];
    private final float[] nearbySpikeDistSq = new float[MAX_NEARBY_DEATH_SPIKES];
    private int nearbySpikeCount = 0;
    private long nearestTileId = -1;
    private float stickyRotationTime = 0f;
    private float stickyRotationAng = 0f;
    private final PlayerConfig config;

    public Vector3D getDir() {
        return dir;
    }

    public void setDir(Vector3D dir) {
        this.dir = dir;
    }

    public Vector3D getMoveDir() {
        return moveDir;
    }

    public void setMoveDir(Vector3D moveDir) {
        this.moveDir = moveDir;
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

    public float getNearestGroundDistance() {
        return nearestGroundDistance;
    }

    public int getNearbyDeathSpikeCount() {
        return nearbySpikeCount;
    }

    public float getNearbyDeathSpikeX(int idx) {
        return nearbySpikeX[idx];
    }

    public float getNearbyDeathSpikeY(int idx) {
        return nearbySpikeY[idx];
    }

    public float getNearbyDeathSpikeZ(int idx) {
        return nearbySpikeZ[idx];
    }

    public void addNearbyDeathSpike(float x, float y, float z) {
        if (position == null) return;
        float dx = x - position.x;
        float dy = y - position.y;
        float dz = z - position.z;
        float distSq = dx * dx + dy * dy + dz * dz;

        int idx;
        if (nearbySpikeCount < MAX_NEARBY_DEATH_SPIKES) {
            idx = nearbySpikeCount;
            nearbySpikeCount += 1;
        } else {
            int farthestIdx = 0;
            float farthestDistSq = nearbySpikeDistSq[0];
            for (int i = 1; i < nearbySpikeCount; ++i) {
                if (nearbySpikeDistSq[i] > farthestDistSq) {
                    farthestDistSq = nearbySpikeDistSq[i];
                    farthestIdx = i;
                }
            }
            if (distSq >= farthestDistSq) return;
            idx = farthestIdx;
        }

        nearbySpikeX[idx] = x;
        nearbySpikeY[idx] = y;
        nearbySpikeZ[idx] = z;
        nearbySpikeDistSq[idx] = distSq;
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
        nearestGroundDistance = Float.POSITIVE_INFINITY;
        nearbySpikeCount = 0;
    }

    public Vector3D[] findCollisionTriangle(Tile tileBelow, float verticalTravel) {
        if (tileBelow == null || position == null) return null;
        float travel = Math.max(0f, verticalTravel);
        float maxDist = (config.playerHeight + travel) * config.fallCollisionSafetyMultiplier;
        Vector3D[] bestTri = null;
        float bestTriDist = Float.POSITIVE_INFINITY;
        for (Vector3D[] tri : tileBelow.triangles) {
            Vector3D triNormal = getNormal(tri);
            float d = rayTriangleDistance(
                    position,
                    triNormal.mult(-1),
                    tri[0], tri[1], tri[2]
            );
            if (!Float.isInfinite(d)
                    && d > config.playerHeight / 2f) {
                if (d < nearestGroundDistance) {
                    nearestGroundDistance = d;
                }
                if (d < maxDist && d < bestTriDist) {
                    bestTriDist = d;
                    bestTri = tri;
                }
            }
        }
        return bestTri;
    }

    public FrameStartPlayerState(PlayerConfig cfg){
        this.config = cfg;
        dir = new Vector3D(
                cfg.initialDirectionX,
                cfg.initialDirectionY,
                cfg.initialDirectionZ
        );
        moveDir = dir;
    }

}
