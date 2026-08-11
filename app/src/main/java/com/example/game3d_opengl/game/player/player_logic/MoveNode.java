package com.example.game3d_opengl.game.player.player_logic;

import com.example.game3d_opengl.game.logic_abstraction.StateInfoNode;
import com.example.game3d_opengl.game.player.player_character.PlayerConfig;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Tile;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class MoveNode extends StateInfoNode<MoveNode.Data> {
    public static final class Data {
        public Vector3D move;
        public float nextFallSpeed;
    }

    private final InputNode input;
    private final PlayerConfig config;
    private final Data data = new Data();

    public MoveNode(InputNode input, PlayerConfig config) {
        this.input = input;
        this.config = config;
    }


    @Override
    public Data getData() {
        return data;
    }

    @Override
    public void calc() {
        FrameStartPlayerState in = input.getData();

        Vector3D move;
        float nextFallSpeed;
        Vector3D dir = in.getMoveDir();
        Vector3D lastMove = in.getLastMove();
        float fallSpeed = in.getFallSpeed();
        float activeHorizontalSpeed = in.getActiveHorizontalSpeed();
        Tile tileBelow = in.getTileBelow();
        float dirX = dir != null ? dir.x : 0f;
        float dirY = dir != null ? dir.y : 0f;
        float dirZ = dir != null ? dir.z : 0f;

        if (tileBelow == null) {
            // Falling
            float horizontalScale = scaleToLength(dirX, dirY, dirZ, activeHorizontalSpeed);
            float lastY = lastMove != null ? lastMove.y : 0f;
            move = new Vector3D(
                    dirX * horizontalScale,
                    lastY - fallSpeed,
                    dirZ * horizontalScale
            );
            nextFallSpeed = fallSpeed + config.fallAcceleration;
        } else {
            int hitTriangleIndex = in.getCollisionTriangleIndex();
            if (hitTriangleIndex >= 0) {
                Vector3D tri0 = tileBelow.getTriangleVertex(hitTriangleIndex, 0);
                Vector3D tri1 = tileBelow.getTriangleVertex(hitTriangleIndex, 1);
                Vector3D tri2 = tileBelow.getTriangleVertex(hitTriangleIndex, 2);
                float ux = tri1.x - tri0.x;
                float uy = tri1.y - tri0.y;
                float uz = tri1.z - tri0.z;
                float wx = tri2.x - tri0.x;
                float wy = tri2.y - tri0.y;
                float wz = tri2.z - tri0.z;
                float nx = uy * wz - uz * wy;
                float ny = uz * wx - ux * wz;
                float nz = ux * wy - uy * wx;
                float det = calculateDeterminant(nx, ny, nz, ux, uy, uz, wx, wy, wz);
                if (Math.abs(det) > 1e-6f) {
                    float beta = calculateBeta(nx, ny, nz, wx, wy, wz, dirX, dirY, dirZ, det);
                    float gamma = calculateGamma(nx, ny, nz, ux, uy, uz, dirX, dirY, dirZ, det);
                    float moveX = ux * beta + wx * gamma;
                    float moveY = uy * beta + wy * gamma;
                    float moveZ = uz * beta + wz * gamma;
                    float projectionScale = scaleToLength(moveX, moveY, moveZ, activeHorizontalSpeed);
                    move = new Vector3D(
                            moveX * projectionScale,
                            moveY * projectionScale,
                            moveZ * projectionScale
                    );
                } else {
                    float horizontalScale = scaleToLength(dirX, dirY, dirZ, activeHorizontalSpeed);
                    move = new Vector3D(
                            dirX * horizontalScale,
                            dirY * horizontalScale,
                            dirZ * horizontalScale
                    );
                }
            } else {
                float horizontalScale = scaleToLength(dirX, dirY, dirZ, activeHorizontalSpeed);
                move = new Vector3D(
                        dirX * horizontalScale,
                        dirY * horizontalScale,
                        dirZ * horizontalScale
                );
            }
            nextFallSpeed = 0f;
        }

        data.move = move;
        data.nextFallSpeed = nextFallSpeed;
    }

    private static float scaleToLength(float x, float y, float z, float length) {
        float currentLen = (float) Math.sqrt(x * x + y * y + z * z);
        if (currentLen < 1e-8f) {
            return 0f;
        }
        return length / currentLen;
    }

    private static float calculateDeterminant(
            float nx, float ny, float nz,
            float ux, float uy, float uz,
            float wx, float wy, float wz
    ) {
        return nx * uy * wz - nx * uz * wy
                - ny * ux * wz + ny * uz * wx
                + nz * ux * wy - nz * uy * wx;
    }

    private static float calculateBeta(
            float nx, float ny, float nz,
            float tangent2x, float tangent2y, float tangent2z,
            float dirX, float dirY, float dirZ,
            float det
    ) {
        return (nx * dirY * tangent2z - nx * dirZ * tangent2y
                - ny * dirX * tangent2z + ny * dirZ * tangent2x
                + nz * dirX * tangent2y - nz * dirY * tangent2x) / det;
    }

    private static float calculateGamma(
            float nx, float ny, float nz,
            float tangent1x, float tangent1y, float tangent1z,
            float dirX, float dirY, float dirZ,
            float det
    ) {
        return (nx * tangent1y * dirZ - nx * tangent1z * dirY
                - ny * tangent1x * dirZ + ny * tangent1z * dirX
                + nz * tangent1x * dirY - nz * tangent1y * dirX) / det;
    }
}

