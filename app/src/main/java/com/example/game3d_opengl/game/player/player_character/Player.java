package com.example.game3d_opengl.game.player.player_character;

import static com.example.game3d_opengl.game.player.player_character.PlayerConfig.*;
import static com.example.game3d_opengl.game.player.player_character.PlayerAssets.*;
import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.game.util.GameMath.getNormal;
import static com.example.game3d_opengl.game.util.GameMath.rayTriangleDistance;
import static com.example.game3d_opengl.game.util.GameMath.rotY;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;
import static java.lang.Float.max;
import static java.lang.Math.abs;
import static java.lang.Math.min;
import static java.lang.Math.signum;

import com.example.game3d_opengl.game.WorldActor;
import com.example.game3d_opengl.game.player.player_logic.InputNode;
import com.example.game3d_opengl.game.player.player_logic.OutputNode;
import com.example.game3d_opengl.game.player.player_logic.PlayerLogic;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Tile;
import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the player character in the game world.
 * Handles movement, collision detection, physics, and rendering.
 */
public class Player implements WorldActor {

    private final UnbatchedObject3DWithOutline object3D;
    private final PlayerLogic logic;
    private final InputNode inputNode;
    private final InputNode.Data logicInput;

    private final InteractableAPI interactableAPI;
    private final PlayerInputAPI inputAPI;

    // transient input buffers (per-frame)
    private float pendingSwipeDx = 0f;
    private float pendingSwipeDy = 0f;
    private boolean wantsJump = false;

    private Player(UnbatchedObject3DWithOutline object3D) {
        this.object3D = object3D;
        this.logic = new PlayerLogic();
        this.inputNode = logic.getInputNode();
        this.logicInput = new InputNode.Data();
        this.interactableAPI = new InteractableAPI();
        this.inputAPI = new PlayerInputAPI();
    }

    private static UnbatchedObject3DWithOutline getObject3D() {
        if (PLAYER_OBJECT == null) {
            throw new IllegalStateException(ERROR_ASSETS_NOT_LOADED);
        }
        return PLAYER_OBJECT;
    }

    public static Player createPlayer() {
        return new Player(getObject3D());
    }

    @Override
    public void updateBeforeDraw(float dtMillis) {
        applyInput(dtMillis);

        logicInput.dtMillis = dtMillis;
        logicInput.position = V3(object3D.objX, object3D.objY, object3D.objZ);
        logicInput.swipeDx = 0f;
        logicInput.swipeDy = 0f;
        logicInput.wantsJump = wantsJump;

        OutputNode.Data output = logic.runLogic(logicInput);
        if (output != null && output.move != null) {
            inputNode.setLastMove(output.move);
            inputNode.setFallSpeed(output.nextFallSpeed);
        }

        Vector3D move = inputNode.getLastMove();
        object3D.objX += move.x * dtMillis;
        object3D.objY += move.y * dtMillis;
        object3D.objZ += move.z * dtMillis;
        object3D.objPitch -= dtMillis * PLAYER_SPEED / (PI * PLAYER_HEIGHT) * 2 * PI;

        wantsJump = false;
    }

    @Override
    public void draw(float[] mvpMatrix) {
        if (object3D != null) {
            object3D.draw(mvpMatrix);
        }
    }

    @Override
    public void updateAfterDraw(float dt) {
        inputNode.resetFrame();
    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {
        if (object3D != null) {
            object3D.cleanupGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (object3D != null) {
            object3D.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    public float getX() { return object3D.objX; }
    public float getY() { return object3D.objY; }
    public float getZ() { return object3D.objZ; }

    public Vector3D getDir() {
        return inputNode.getDir();
    }

    public long getNearestTileId() {
        return inputNode.getNearestTileId();
    }

    public InteractableAPI getInteractableAPI() {
        return interactableAPI;
    }

    public PlayerInputAPI getInputAPI() {
        return inputAPI;
    }

    private void applyInput(float dtMillis) {
        // Sticky rotation decay
        float stickyTime = max(0f, inputNode.getStickyRotationTime() - dtMillis);
        inputNode.setStickyRotationTime(stickyTime);
        if (stickyTime == 0 && inputNode.getStickyRotationAng() != 0f) {
            float dYaw = minByAbs(signum(inputNode.getStickyRotationAng())
                    * STICKY_ROTATION_ANGLE_DECAY_RATE * dtMillis, inputNode.getStickyRotationAng());
            object3D.objYaw -= dYaw;
            inputNode.setStickyRotationAng(inputNode.getStickyRotationAng() - dYaw);
        }

        float dx = pendingSwipeDx;
        float dy = pendingSwipeDy;
        pendingSwipeDx = 0f;
        pendingSwipeDy = 0f;

        if (dx != 0f) {
            float dYaw = dx * ROTATION_SWIPE_SENSITIVITY; // in radians
            inputNode.setDir(rotY(inputNode.getDir(), dYaw));

            // Visual rotation (degrees)
            object3D.objYaw -= dYaw * 180.0f / PI;

            // Sticky rotation effect
            inputNode.setStickyRotationAng(inputNode.getStickyRotationAng()
                    - dx * STICKY_ROTATION_COEFFICIENT);
            inputNode.setStickyRotationTime(STICKY_ROTATION_LASTING_TIME);
            object3D.objYaw -= dx * STICKY_ROTATION_COEFFICIENT;
        }
    }

    private float minByAbs(float a, float b) {
        return abs(a) < abs(b) ? a : b;
    }

    private Vector3D[] findCollisionTriangle(Tile tile) {
        Vector3D origin = V3(object3D.objX, object3D.objY, object3D.objZ);
        for (Vector3D[] tri : tile.triangles) {
            Vector3D triNormal = getNormal(tri);
            float d = rayTriangleDistance(
                    origin,
                    triNormal.mult(-1),
                    tri[0], tri[1], tri[2]
            );
            if (!Float.isInfinite(d)
                    && d < (PLAYER_HEIGHT + inputNode.getFallSpeed()) * FALL_COLLISION_SAFETY_MULTIPLIER
                    && d > PLAYER_HEIGHT / 2) {
                return tri;
            }
        }
        return null;
    }

    public final class PlayerInputAPI {
        public void swipe(float dx, float dy) {
            pendingSwipeDx += dx;
            pendingSwipeDy += dy;
        }

    }

    public class InteractableAPI {

        public void setHasFooting(Tile tile) {
            inputNode.setTileBelow(tile);
        }

        public boolean collidesTile(Tile tile) {
            Vector3D[] tri = findCollisionTriangle(tile);
            if (tri != null) {
                inputNode.setCollisionTriangle(tri);
                return true;
            }
            return false;
        }

        public Vector3D getDir() {
            return inputNode.getDir();
        }
    }
}
