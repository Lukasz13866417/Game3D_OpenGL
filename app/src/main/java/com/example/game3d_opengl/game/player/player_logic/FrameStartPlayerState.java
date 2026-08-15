package com.example.game3d_opengl.game.player.player_logic;

import static com.example.game3d_opengl.game.util.GameMath.getUnitNormalTo;
import static com.example.game3d_opengl.game.util.GameMath.rayTriangleDistance;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import com.example.game3d_opengl.game.player.player_character.PlayerConfig;
import com.example.game3d_opengl.game.util.GameMath.MutableVec3;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class FrameStartPlayerState {
    private static final float SUPPORT_SEPARATION_MIN_DISTANCE = 0.008f;
    private static final float SUPPORT_SEPARATION_DISTANCE_FACTOR = 0.025f;
    private static final float SUPPORT_SEPARATION_MIN_CLEARANCE = 0.0015f;
    private static final float SUPPORT_SEPARATION_CLEARANCE_FACTOR = 0.0025f;

    public float dtMillis;
    public Vector3D position;
    public float swipeDx;
    public float swipeDy;
    public boolean isTouchUp = true;

    private Vector3D dir;
    private Vector3D moveDir;
    private Vector3D lastMove = V3(0, 0, 0);
    private float fallSpeed = 0f;
    private float activeHorizontalSpeed;
    private PlayerSupportSurface tileBelow = null;
    private int collisionTriangleIndex = -1;
    private float collisionProbeDistance = Float.POSITIVE_INFINITY;
    private float nearestGroundDistance = Float.POSITIVE_INFINITY;
    private float nearestGroundY = Float.POSITIVE_INFINITY;
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
    private final MutableVec3 collisionNormalTmp = new MutableVec3();

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

    public float getActiveHorizontalSpeed() {
        return activeHorizontalSpeed;
    }

    public void setActiveHorizontalSpeed(float activeHorizontalSpeed) {
        this.activeHorizontalSpeed = Math.max(0f, activeHorizontalSpeed);
    }

    public PlayerSupportSurface getTileBelow() {
        return tileBelow;
    }

    public void setTileBelow(PlayerSupportSurface tileBelow) {
        this.tileBelow = tileBelow;
        if (tileBelow != null) {
            nearestTileId = Math.max(nearestTileId, tileBelow.getID());
        }
    }

    public int getCollisionTriangleIndex() {
        return collisionTriangleIndex;
    }

    public void setCollisionTriangleIndex(int collisionTriangleIndex) {
        this.collisionTriangleIndex = collisionTriangleIndex;
    }

    public float getCollisionProbeDistance() {
        return collisionProbeDistance;
    }

    public float getNearestGroundDistance() {
        return nearestGroundDistance;
    }

    public float getNearestGroundY() {
        return nearestGroundY;
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
        collisionTriangleIndex = -1;
        collisionProbeDistance = Float.POSITIVE_INFINITY;
        nearestGroundDistance = Float.POSITIVE_INFINITY;
        nearestGroundY = Float.POSITIVE_INFINITY;
        nearbySpikeCount = 0;
    }

    public int probeCollisionTriangleIndex(
            PlayerSupportSurface tileBelow, float verticalTravel) {
        collisionProbeDistance = Float.POSITIVE_INFINITY;
        if (tileBelow == null || position == null) return -1;
        float travel = Math.max(0f, verticalTravel);
        float maxDist = (config.playerHeight + travel) * config.fallCollisionSafetyMultiplier;
        int bestTriangleIndex = -1;
        float bestTriDist = Float.POSITIVE_INFINITY;
        float posX = position.x;
        float posY = position.y;
        float posZ = position.z;
        for (int triangleIndex = 0; triangleIndex < tileBelow.getTriangleCount(); ++triangleIndex) {
            Vector3D a = tileBelow.getTriangleVertex(triangleIndex, 0);
            Vector3D b = tileBelow.getTriangleVertex(triangleIndex, 1);
            Vector3D c = tileBelow.getTriangleVertex(triangleIndex, 2);
            getUnitNormalTo(collisionNormalTmp, a, b, c);
            float d = rayTriangleDistance(
                    posX, posY, posZ,
                    -collisionNormalTmp.x, -collisionNormalTmp.y, -collisionNormalTmp.z,
                    a.x, a.y, a.z,
                    b.x, b.y, b.z,
                    c.x, c.y, c.z
            );
            if (!Float.isInfinite(d)
                    && d > config.playerHeight / 2f) {
                if (d < nearestGroundDistance) {
                    nearestGroundDistance = d;
                    nearestGroundY = posY - collisionNormalTmp.y * d;
                }
                if (isSeparatingFromTriangle(d, collisionNormalTmp.x, collisionNormalTmp.y, collisionNormalTmp.z)) {
                    continue;
                }
                if (d < maxDist && d < bestTriDist) {
                    bestTriDist = d;
                    bestTriangleIndex = triangleIndex;
                }
            }
        }
        collisionProbeDistance = bestTriDist;
        return bestTriangleIndex;
    }

    private boolean isSeparatingFromTriangle(
            float hitDistance,
            float normalX,
            float normalY,
            float normalZ
    ) {
        if (lastMove == null) {
            return false;
        }
        float supportClearance = hitDistance - config.playerHeight / 2f;
        float minClearance = Math.max(
                SUPPORT_SEPARATION_MIN_CLEARANCE,
                config.playerHeight * SUPPORT_SEPARATION_CLEARANCE_FACTOR
        );
        if (supportClearance <= minClearance) {
            return false;
        }
        float dt = Math.max(0f, dtMillis);
        if (dt <= 0f) {
            return false;
        }
        float separationSpeed = lastMove.x * normalX + lastMove.y * normalY + lastMove.z * normalZ;
        if (separationSpeed <= 0f) {
            return false;
        }
        float separationDistance = separationSpeed * dt;
        float minSeparationDistance = Math.max(
                SUPPORT_SEPARATION_MIN_DISTANCE,
                config.playerHeight * SUPPORT_SEPARATION_DISTANCE_FACTOR
        );
        return separationDistance > minSeparationDistance;
    }

    public FrameStartPlayerState(PlayerConfig cfg){
        this.config = cfg;
        dir = new Vector3D(
                cfg.initialDirectionX,
                cfg.initialDirectionY,
                cfg.initialDirectionZ
        );
        moveDir = dir;
        activeHorizontalSpeed = cfg.playerSpeed;
    }

}
