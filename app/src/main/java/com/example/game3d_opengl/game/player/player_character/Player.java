package com.example.game3d_opengl.game.player.player_character;

import static com.example.game3d_opengl.game.player.player_character.PlayerConfig.*;
import static com.example.game3d_opengl.game.player.player_character.PlayerAssets.*;
import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.game.util.GameMath.rotY;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;
import static java.lang.Float.max;
import static java.lang.Math.abs;
import static java.lang.Math.min;
import static java.lang.Math.signum;

import com.example.game3d_opengl.game.WorldActor;
import com.example.game3d_opengl.game.hud.HUDInputAPI;
import com.example.game3d_opengl.game.hud.GameHUD;
import com.example.game3d_opengl.game.player.player_logic.FrameStartPlayerState;
import com.example.game3d_opengl.game.player.player_logic.OutputNode;
import com.example.game3d_opengl.game.player.player_logic.PlayerLogic;
import com.example.game3d_opengl.game.player.player_logic.jump.JumpConfig;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Tile;
import com.example.game3d_opengl.game.terrain.track_elements.portal.ExitPortal;
import com.example.game3d_opengl.game.terrain.track_elements.portal.Portal;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Represents the player character in the game world.
 * Handles movement, collision detection, physics, and rendering.
 */
public class Player implements WorldActor {

    private final UnbatchedObject3DWithOutline object3D;
    private final PlayerLogic logic;
    private final FrameStartPlayerState frameStartState;

    private final PlayerInputAPI inputAPI;

    // transient input buffers (per-frame)
    private float pendingSwipeDx = 0f;
    private float pendingSwipeDy = 0f;
    private final float[] spikeHazardPointTmp = new float[3];

    private final PlayerHUDAPI hudAPI;
    private final PlayerConfig config ;
    private final JumpConfig jumpConfig;
    private Player(UnbatchedObject3DWithOutline object3D) {
        this.object3D = object3D;
        config = new PlayerConfig();
        jumpConfig = new JumpConfig();
        this.frameStartState = new FrameStartPlayerState(config);
        this.logic = new PlayerLogic(config, jumpConfig);
        this.inputAPI = new PlayerInputAPI();
        this.hudAPI = new PlayerHUDAPI();
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

    public void beginFrame(float dtMillis) {
        frameStartState.dtMillis = dtMillis;
        frameStartState.position = V3(object3D.objX, object3D.objY, object3D.objZ);
    }

    @Override
    public void updateBeforeDraw(float dtMillis) {
        float swipeDx = pendingSwipeDx;
        float swipeDy = pendingSwipeDy;
        pendingSwipeDx = 0f;
        pendingSwipeDy = 0f;
        applyInput(dtMillis, swipeDx, swipeDy);
        beginFrame(dtMillis);

        OutputNode.Data output = logic.runLogic(frameStartState);
        assert output != null;
        assert output.move != null;
        assert output.oneTimePosOffset != null;

        frameStartState.setLastMove(output.move);
        frameStartState.setFallSpeed(output.nextFallSpeed);

        Vector3D move = frameStartState.getLastMove();
        object3D.objX += output.oneTimePosOffset.x;
        object3D.objY += output.oneTimePosOffset.y;
        object3D.objZ += output.oneTimePosOffset.z;

        object3D.objX += move.x * dtMillis;
        object3D.objY += move.y * dtMillis;
        object3D.objZ += move.z * dtMillis;
        object3D.objPitch -= dtMillis * PLAYER_SPEED / (PI * PLAYER_HEIGHT) * 2 * PI;

    }

    @Override
    public void draw(float[] mvpMatrix) {
        if (object3D != null) {
            object3D.draw(mvpMatrix);
        }
    }

    @Override
    public void updateAfterDraw(float dt) {
        frameStartState.resetFrame();
    }

    @Override
    public void rebasePosition(Vector3D delta) {
        if (delta == null) return;
        object3D.objX += delta.x;
        object3D.objY += delta.y;
        object3D.objZ += delta.z;
        if (frameStartState.position != null) {
            frameStartState.position = frameStartState.position.add(delta);
        }
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
        return frameStartState.getDir();
    }

    public long getNearestTileId() {
        return frameStartState.getNearestTileId();
    }

    public PlayerInputAPI getInputAPI() {
        return inputAPI;
    }

    public void interactWith(Tile tile) {
        if (tile == null || tile.isEmptySegment()) return;
        if (collidesTile(tile)) {
            setHasFooting(tile);
        }
    }

    public void interactWith(Potion potion) {
        // No-op for now (placeholder for potion effects)
    }

    public void interactWith(DeathSpike spike) {
        if (spike == null) return;
        if (spike.writeHazardPoint(spikeHazardPointTmp)) {
            frameStartState.addNearbyDeathSpike(
                    spikeHazardPointTmp[0],
                    spikeHazardPointTmp[1],
                    spikeHazardPointTmp[2]
            );
        }
    }

    public void interactWith(Portal portal) {
        // No-op for now (portal handling later)
    }

    public void interactWith(ExitPortal portal) {
        // No-op for now (portal handling later)
    }

    private void applyInput(float dtMillis, float swipeDx, float swipeDy) {

        frameStartState.swipeDx = swipeDx;
        frameStartState.swipeDy = swipeDy;

        // Sticky rotation decay
        float stickyTime = max(0f, frameStartState.getStickyRotationTime() - dtMillis);
        frameStartState.setStickyRotationTime(stickyTime);
        if (stickyTime == 0 && frameStartState.getStickyRotationAng() != 0f) {
            float dYaw = minByAbs(signum(frameStartState.getStickyRotationAng())
                    * STICKY_ROTATION_ANGLE_DECAY_RATE * dtMillis, frameStartState.getStickyRotationAng());
            object3D.objYaw -= dYaw;
            frameStartState.setStickyRotationAng(frameStartState.getStickyRotationAng() - dYaw);
        }


        if (swipeDx != 0f) {
            float dYaw = swipeDx * ROTATION_SWIPE_SENSITIVITY; // in radians
            frameStartState.setDir(rotY(frameStartState.getDir(), dYaw));

            // Visual rotation (degrees)
            object3D.objYaw -= dYaw * 180.0f / PI;

            // Sticky rotation effect
            frameStartState.setStickyRotationAng(frameStartState.getStickyRotationAng()
                    - swipeDx * STICKY_ROTATION_COEFFICIENT);
            frameStartState.setStickyRotationTime(STICKY_ROTATION_LASTING_TIME);
            object3D.objYaw -= swipeDx * STICKY_ROTATION_COEFFICIENT;
        }

        if (frameStartState.getTileBelow() != null) {
            frameStartState.setMoveDir(frameStartState.getDir());
        }
    }

    private float minByAbs(float a, float b) {
        return abs(a) < abs(b) ? a : b;
    }

    private float getVerticalTravelForCollision() {
        float dt = frameStartState.dtMillis;
        if (dt <= 0f) return 0f;
        float downwardVel = max(0f, -frameStartState.getLastMove().y);
        float verticalVel = downwardVel + config.fallAcceleration;
        return verticalVel * dt;
    }

    private void setHasFooting(Tile tile) {
        frameStartState.setTileBelow(tile);
    }

    private boolean collidesTile(Tile tile) {
        float verticalTravel = getVerticalTravelForCollision();
        Vector3D[] tri = frameStartState.findCollisionTriangle(tile, verticalTravel);
        if (tri != null) {
            frameStartState.setCollisionTriangle(tri);
            return true;
        }
        return false;
    }

    public final class PlayerInputAPI {
        public void swipe(float dx, float dy) {
            pendingSwipeDx += dx;
            pendingSwipeDy += dy;
        }

        public void setTouchUp(){
            frameStartState.isTouchUp = true;
        }

        public void setTouchDown(){
            frameStartState.isTouchUp = false;
        }

    }

    public PlayerHUDAPI getHUDAPI(){
        return hudAPI;
    }

    public class PlayerHUDAPI extends HUDInputAPI<GameHUD.InfoFromPlayer> {
        public void giveInfoToHUD(GameHUD.InfoFromPlayer target) {
            float swipeVal = logic.getCumulativeSwipeDy();
            target.jumpSwipeMin = 0f;
            target.jumpSwipeMax = jumpConfig.jumpMaxSwipe;
            target.jumpSwipeMilestones = new float[]{ jumpConfig.jumpSwipeThresholdPx };
            target.jumpSwipeValue = Math.min(swipeVal, jumpConfig.jumpMaxSwipe);
        }
    }


}
