package com.example.game3d_opengl.game.player.player_logic;

import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

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
        Tile tileBelow = in.getTileBelow();

        if (tileBelow == null) {
            // Falling
            Vector3D dwl = dir.withLen(config.playerSpeed);
            Vector3D last = lastMove != null ? lastMove : V3(0, 0, 0);
            move = V3(dwl.x, last.y, dwl.z);
            move = V3(move.x, move.y - fallSpeed, move.z);
            nextFallSpeed = fallSpeed + config.fallAcceleration;
        } else {
            Vector3D[] hitTri = in.getCollisionTriangle();
            if (hitTri != null) {
                Vector3D u = hitTri[1].sub(hitTri[0]);
                Vector3D w = hitTri[2].sub(hitTri[0]);
                Vector3D n = u.crossProduct(w);
                float det = calculateDeterminant(n, u, w);
                if (Math.abs(det) > 1e-6f) {
                    float beta = calculateBeta(n, w, dir, det);
                    float gamma = calculateGamma(n, u, dir, det);
                    move = u.mult(beta).add(w.mult(gamma)).withLen(config.playerSpeed);
                } else {
                    move = dir.withLen(config.playerSpeed);
                }
            } else {
                move = dir.withLen(config.playerSpeed);
            }
            nextFallSpeed = 0f;
        }

        data.move = move;
        data.nextFallSpeed = nextFallSpeed;
    }

    private static float calculateDeterminant(Vector3D n, Vector3D u, Vector3D w) {
        return n.x * u.y * w.z - n.x * u.z * w.y
                - n.y * u.x * w.z + n.y * u.z * w.x
                + n.z * u.x * w.y - n.z * u.y * w.x;
    }

    private static float calculateBeta(Vector3D n, Vector3D tangent2, Vector3D dir, float det) {
        return (n.x * dir.y * tangent2.z - n.x * dir.z * tangent2.y
                - n.y * dir.x * tangent2.z + n.y * dir.z * tangent2.x
                + n.z * dir.x * tangent2.y - n.z * dir.y * tangent2.x) / det;
    }

    private static float calculateGamma(Vector3D n, Vector3D tangent1, Vector3D dir, float det) {
        return (n.x * tangent1.y * dir.z - n.x * tangent1.z * dir.y
                - n.y * tangent1.x * dir.z + n.y * tangent1.z * dir.x
                + n.z * tangent1.x * dir.y - n.z * tangent1.y * dir.x) / det;
    }
}

