package com.example.game3d_opengl.game.player.player_logic;

import static com.example.game3d_opengl.game.player.player_character.PlayerConfig.FALL_ACCELERATION;
import static com.example.game3d_opengl.game.player.player_character.PlayerConfig.FALL_COLLISION_SAFETY_MULTIPLIER;
import static com.example.game3d_opengl.game.player.player_character.PlayerConfig.PLAYER_HEIGHT;
import static com.example.game3d_opengl.game.player.player_character.PlayerConfig.PLAYER_SPEED;
import static com.example.game3d_opengl.game.util.GameMath.getNormal;
import static com.example.game3d_opengl.game.util.GameMath.rayTriangleDistance;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import com.example.game3d_opengl.game.logic_abstraction.StateInfoNode;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Tile;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class MoveNode extends StateInfoNode<MoveNode.Data> {
    public static final class Data {
        public Vector3D move;
        public float nextFallSpeed;
    }

    private final InputNode input;
    private final EffectsNode effects;
    private final JumpLogicNode jumpLogic;
    private Data data = new Data();

    public MoveNode(InputNode input, EffectsNode effects, JumpLogicNode jumpLogic) {
        this.input = input;
        this.effects = effects;
        this.jumpLogic = jumpLogic;
    }

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
        InputNode.Data in = input.getData();
        JumpLogicNode.Data jump = jumpLogic.getData();

        Vector3D move;
        float nextFallSpeed;
        Vector3D dir = input.getDir();
        Vector3D lastMove = input.getLastMove();
        float fallSpeed = input.getFallSpeed();
        Tile tileBelow = input.getTileBelow();

        if (jump != null && jump.shouldJump) {
            // TODO: real jump implementation.
            move = lastMove != null ? lastMove : dir.withLen(PLAYER_SPEED);
            nextFallSpeed = fallSpeed;
        } else if (tileBelow == null) {
            // Falling
            Vector3D dwl = dir.withLen(PLAYER_SPEED);
            Vector3D last = lastMove != null ? lastMove : V3(0, 0, 0);
            move = V3(dwl.x, last.y, dwl.z);
            move = V3(move.x, move.y - fallSpeed, move.z);
            nextFallSpeed = fallSpeed + FALL_ACCELERATION;
        } else {
            Vector3D[] hitTri = input.getCollisionTriangle();
            if (hitTri == null) {
                hitTri = findCollisionTriangle(in, tileBelow, fallSpeed);
            }
            if (hitTri != null) {
                Vector3D u = hitTri[1].sub(hitTri[0]);
                Vector3D w = hitTri[2].sub(hitTri[0]);
                Vector3D n = u.crossProduct(w);
                float det = calculateDeterminant(n, u, w);
                if (Math.abs(det) > 1e-6f) {
                    float beta = calculateBeta(n, w, dir, det);
                    float gamma = calculateGamma(n, u, dir, det);
                    move = u.mult(beta).add(w.mult(gamma)).withLen(PLAYER_SPEED);
                } else {
                    move = dir.withLen(PLAYER_SPEED);
                }
            } else {
                move = dir.withLen(PLAYER_SPEED);
            }
            nextFallSpeed = 0f;
        }

        data.move = move;
        data.nextFallSpeed = nextFallSpeed;
    }

    private static Vector3D[] findCollisionTriangle(InputNode.Data in, Tile tileBelow, float fallSpeed) {
        if (tileBelow == null || in == null || in.position == null) return null;
        for (Vector3D[] tri : tileBelow.triangles) {
            Vector3D triNormal = getNormal(tri);
            float d = rayTriangleDistance(
                    in.position,
                    triNormal.mult(-1),
                    tri[0], tri[1], tri[2]
            );
            if (!Float.isInfinite(d)
                    && d < (PLAYER_HEIGHT + fallSpeed) * FALL_COLLISION_SAFETY_MULTIPLIER
                    && d > PLAYER_HEIGHT / 2) {
                return tri;
            }
        }
        return null;
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

