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

import android.opengl.GLES20;

import com.example.game3d_opengl.game.WorldActor;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.simulation.PlayerSnapshot;
import com.example.game3d.core.simulation.SimulationFrameSnapshot;
import com.example.game3d_opengl.game.hud.HUDInputAPI;
import com.example.game3d_opengl.game.hud.GameHUD;
import com.example.game3d_opengl.game.player.player_logic.FrameStartPlayerState;
import com.example.game3d_opengl.game.player.player_logic.OutputNode;
import com.example.game3d_opengl.game.player.player_logic.PlayerLogic;
import com.example.game3d_opengl.game.player.player_logic.jump.JumpConfig;
import com.example.game3d_opengl.game.settings.TouchSensitivitySettings;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Tile;
import com.example.game3d_opengl.game.terrain.track_elements.portal.ExitPortal;
import com.example.game3d_opengl.game.terrain.track_elements.portal.Portal;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents the player character in the game world.
 * Handles movement, collision detection, physics, and rendering.
 */
public class Player implements WorldActor {
    static final double MAX_VISUAL_SPIN_RPS = 5.0;
    static final double DETAIL_GROOVES_FULL_BELOW_DEGREES = 8.0;
    static final double DETAIL_GROOVES_GONE_AT_DEGREES = 12.0;
    static final double SECONDARY_GROOVES_FULL_BELOW_DEGREES = 14.0;
    static final double SECONDARY_GROOVES_GONE_AT_DEGREES = 18.0;
    static final double PRIMARY_GROOVES_FULL_BELOW_DEGREES = 24.0;
    static final double PRIMARY_GROOVES_GONE_AT_DEGREES = 30.0;
    static final double CORE_GLOW_START_DEGREES = 22.0;
    static final double CORE_GLOW_FULL_DEGREES = 30.0;
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double VISUAL_SPIN_RESPONSE_PER_SECOND = 12.0;
    private static final double VISUAL_DETAIL_RESPONSE_PER_SECOND = 6.0;
    private static final double MAX_VISUAL_SPIN_STEP_SECONDS = 0.05;
    private static final float MAX_CORE_GLOW = 0.82f;
    private static final float LEGACY_STICKY_ROTATION_LASTING_TIME = 42f;
    private static final float LEGACY_STICKY_ROTATION_ANGLE_DECAY_RATE = 0.0575f;
    private static final float LEGACY_STICKY_ROTATION_COEFFICIENT = 0.0085f;
    private static final float LEGACY_ROTATION_SWIPE_SENSITIVITY = 0.00052f;

    private UnbatchedObject3DWithOutline object3D;
    private final PlayerLogic logic;
    private final FrameStartPlayerState frameStartState;

    private final PlayerInputAPI inputAPI;
    private final PhysicsConfig sharedPhysicsConfig = new PhysicsConfig();
    private final PlayerTurnVisualEffect turnVisualEffect =
            new PlayerTurnVisualEffect();
    private final AtomicBoolean turnVisualResetPending =
            new AtomicBoolean();
    private boolean authoritativeSimulation;
    private PlayerSnapshot authoritativeSnapshot;
    private Vector3D authoritativeDirection = V3(0f, 0f, -1f);
    private long authoritativeNearestTileId = -1L;
    private float authoritativeHorizontalSpeed;
    private float authoritativeModelYawDegrees;
    private double authoritativeRenderYawRadians;
    private double authoritativeTurnVisualYawRadians;
    private double authoritativeAngularVelocity;
    private double visualAxleRadians;
    private double visualAngularVelocity;
    private boolean visualSpinInitialized;
    private final SpinBlurPlan spinBlurPlan = new SpinBlurPlan();
    private float violetPrimaryVisibility = 1f;
    private float violetSecondaryVisibility = 1f;
    private float violetDetailVisibility = 1f;
    private float violetCoreGlow;
    private long authoritativeTurnVisualTimeNanos;

    // transient input buffers (per-frame)
    private float pendingSwipeDx = 0f;
    private float pendingSwipeDy = 0f;
    private final float[] spikeHazardPointTmp = new float[3];
    private static final float SPIKE_KILL_RADIUS_FACTOR = 1.6f;
    private static final float SPIKE_KILL_MIN_RADIUS = 0.3f;
    private static final float SPIKE_KILL_HEIGHT_FACTOR = 0.90f;
    private static final float SPIKE_KILL_MIN_HEIGHT = 0.36f;
    static final float UNRECOVERABLE_FALL_DROP_FACTOR = 1.35f;
    static final float UNRECOVERABLE_FALL_MIN_DROP = 0.58f;
    static final float UNRECOVERABLE_FALL_RECOVERY_GROUND_FACTOR = 1.15f;
    static final float UNRECOVERABLE_FALL_BELOW_TILE_MARGIN_FACTOR = 0.8f;
    static final float UNRECOVERABLE_FALL_BELOW_TILE_MIN_MARGIN = 4.54f;
    private boolean dead = false;
    private float lastRecoverableTrackY;
    private boolean hasRecoverableTrackAnchor = false;
    private RecoveryCapabilities recoveryCapabilities = RecoveryCapabilities.NONE;
    private Tile pendingFootingTile = null;
    private int pendingFootingTriangleIndex = -1;
    private float pendingFootingDistance = Float.POSITIVE_INFINITY;
    private boolean tileInteractionSweepOpen = false;

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
        this.lastRecoverableTrackY = object3D != null ? object3D.objY : INITIAL_POSITION_Y;
    }

    private static UnbatchedObject3DWithOutline getObject3D() {
        if (PLAYER_OBJECT == null) {
            throw new IllegalStateException(ERROR_ASSETS_NOT_LOADED);
        }
        return PLAYER_OBJECT;
    }

    public static Player createPlayer() {
        UnbatchedObject3DWithOutline obj = getObject3D();
        // Reset transform so restarts always begin from a clean initial state.
        obj.objX = INITIAL_POSITION_X;
        obj.objY = INITIAL_POSITION_Y;
        obj.objZ = INITIAL_POSITION_Z;
        obj.objYaw = 0f;
        obj.objPitch = 0f;
        obj.objRoll = 0f;
        setVioletSpinAppearance(1f, 1f, 1f, 0f);
        return new Player(obj);
    }

    public static float initialPositionX() {
        return INITIAL_POSITION_X;
    }

    public static float initialPositionY() {
        return INITIAL_POSITION_Y;
    }

    public static float initialPositionZ() {
        return INITIAL_POSITION_Z;
    }

    public void beginFrame(float dtMillis) {
        refreshObject3DReference();
        if (authoritativeSimulation) {
            return;
        }
        frameStartState.dtMillis = dtMillis;
        frameStartState.position = V3(object3D.objX, object3D.objY, object3D.objZ);
    }

    @Override
    public void updateBeforeDraw(float dtMillis) {
        refreshObject3DReference();
        if (authoritativeSimulation) {
            return;
        }
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
        object3D.objPitch -= dtMillis
                * frameStartState.getActiveHorizontalSpeed()
                / (PI * config.playerHeight)
                * 2
                * PI;
        updateDeathSpikeCollisionState();
        updateUnrecoverableFallDeathState();

    }

    @Override
    public void draw(float[] mvpMatrix) {
        refreshObject3DReference();
        if (object3D != null) {
            object3D.draw(mvpMatrix);
            drawSpinBlur(mvpMatrix);
        }
    }

    /** Draws an adaptive translucent trail for tread/glow materials only. */
    private void drawSpinBlur(float[] vpMatrix) {
        if (!spinBlurPlan.isActive()) {
            return;
        }
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(
                GLES20.GL_SRC_ALPHA,
                GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        try {
            object3D.drawSpinBlur(
                    vpMatrix,
                    spinBlurPlan.sampleCount(),
                    spinBlurPlan.startAngleRadians(),
                    spinBlurPlan.angleStepRadians(),
                    spinBlurPlan.sampleOpacity());
        } finally {
            // Scene rendering owns these baseline states; terrain is rendered immediately next.
            GLES20.glDepthFunc(GLES20.GL_LESS);
            GLES20.glDepthMask(true);
            GLES20.glDisable(GLES20.GL_BLEND);
        }
    }

    @Override
    public void updateAfterDraw(float dt) {
        if (authoritativeSimulation) {
            return;
        }
        frameStartState.resetFrame();
    }

    @Override
    public void rebasePosition(Vector3D delta) {
        refreshObject3DReference();
        if (delta == null) return;
        object3D.objX += delta.x;
        object3D.objY += delta.y;
        object3D.objZ += delta.z;
        if (frameStartState.position != null) {
            frameStartState.position = frameStartState.position.add(delta);
        }
        lastRecoverableTrackY += delta.y;
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        // Shared player assets stay resident across stage transitions so a buffered
        // next session can be activated without a forced asset reload.
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        reloadLoadedGPUResourcesOnContextLoss();
    }

    public float getX() { return object3D.objX; }
    public float getY() { return object3D.objY; }
    public float getZ() { return object3D.objZ; }

    public Vector3D getDir() {
        return authoritativeSimulation
                ? authoritativeDirection
                : frameStartState.getDir();
    }

    public long getNearestTileId() {
        return authoritativeSimulation
                ? authoritativeNearestTileId
                : frameStartState.getNearestTileId();
    }

    public boolean isDead() {
        return dead;
    }

    public boolean isRunLost() {
        return dead;
    }

    public boolean isAlive() {
        return !dead;
    }

    public void setMoveSpeed(float speed) {
        config.playerSpeed = Math.max(0f, speed);
    }

    public void setThemeColor(FColor themeColor) {
        setMintThemeColor(themeColor);
    }

    public float getMoveSpeed() {
        return config.playerSpeed;
    }

    public float getActiveHorizontalSpeed() {
        return authoritativeSimulation
                ? authoritativeHorizontalSpeed
                : frameStartState.getActiveHorizontalSpeed();
    }

    /** Authoritative/core speed in world units per second. */
    public float getActiveHorizontalSpeedUnitsPerSecond() {
        return authoritativeSimulation
                ? authoritativeHorizontalSpeed * 1000f
                : frameStartState.getActiveHorizontalSpeed() * 1000f;
    }

    /**
     * Makes the shared game-core snapshot authoritative for gameplay state and rendering.
     * Legacy PlayerLogic remains available only to old isolated test stages.
     */
    public void enableAuthoritativeSimulation() {
        if (authoritativeSimulation) {
            return;
        }
        authoritativeSimulation = true;
        pendingSwipeDx = 0f;
        pendingSwipeDy = 0f;
        authoritativeModelYawDegrees =
                object3D != null ? object3D.objYaw : 0f;
        authoritativeRenderYawRadians =
                -Math.toRadians(authoritativeModelYawDegrees);
        authoritativeTurnVisualYawRadians = authoritativeRenderYawRadians;
        authoritativeTurnVisualTimeNanos = 0L;
        turnVisualResetPending.set(false);
        turnVisualEffect.reset();
    }

    public void applySimulationSnapshots(
            PlayerSnapshot previous,
            PlayerSnapshot current,
            double alpha,
            Vec3 renderOrigin,
            long legacySupportTileId) {
        refreshObject3DReference();
        if (previous == null || current == null || object3D == null) {
            return;
        }
        enableAuthoritativeSimulation();
        double t = Math.max(0.0, Math.min(1.0, alpha));
        Vec3 origin = renderOrigin == null ? Vec3.ZERO : renderOrigin;
        Vec3 from = previous.absolutePosition;
        Vec3 to = current.absolutePosition;
        object3D.objX = (float) (from.x + (to.x - from.x) * t - origin.x);
        object3D.objY = (float) (from.y + (to.y - from.y) * t - origin.y);
        object3D.objZ = (float) (from.z + (to.z - from.z) * t - origin.z);

        double yaw = interpolateAngle(previous.yawRadians, current.yawRadians, t);
        authoritativeTurnVisualYawRadians = current.yawRadians;
        authoritativeTurnVisualTimeNanos = current.timeNanos;
        double axle = interpolateAxleRadians(
                previous.axleRadians, current.axleDeltaRadians, t);
        authoritativeAngularVelocity =
                previous.angularVelocity
                        + (current.angularVelocity
                        - previous.angularVelocity) * t;
        if (!visualSpinInitialized) {
            visualAxleRadians = axle;
            visualAngularVelocity =
                    compressVisualAngularVelocity(
                            authoritativeAngularVelocity);
            visualSpinInitialized = true;
        }
        authoritativeRenderYawRadians = yaw;
        authoritativeModelYawDegrees = (float) -Math.toDegrees(yaw);
        object3D.objYaw = authoritativeModelYawDegrees;
        object3D.objPitch = (float) Math.toDegrees(visualAxleRadians);
        object3D.objRoll = 0f;
        authoritativeDirection = V3(
                (float) Math.sin(yaw), 0f, (float) -Math.cos(yaw));
        authoritativeHorizontalSpeed = (float) (
                Math.sqrt(current.velocity.x * current.velocity.x
                        + current.velocity.z * current.velocity.z) / 1000.0);
        if (legacySupportTileId >= 0L) {
            authoritativeNearestTileId = legacySupportTileId;
        }
        authoritativeSnapshot = current;
        dead = current.dead;
    }

    /** Starts the angle-driven cosmetic accent for an accepted gameplay touch. */
    public void beginTurnVisualHold() {
        if (!authoritativeSimulation) {
            return;
        }
        // A stale lifecycle request from before this accepted DOWN must not cancel the new hold.
        turnVisualResetPending.set(false);
        turnVisualEffect.beginHold(
                authoritativeTurnVisualYawRadians,
                authoritativeTurnVisualTimeNanos
        );
    }

    /** Starts the cosmetic return immediately when an ordinary gameplay touch is released. */
    public void endTurnVisualHold() {
        turnVisualEffect.endHold();
    }

    /** Clears presentation-only turn state immediately on the GL thread. */
    public void resetTurnVisual() {
        turnVisualResetPending.set(false);
        turnVisualEffect.reset();
        if (authoritativeSimulation && object3D != null) {
            object3D.objYaw = authoritativeModelYawDegrees;
        }
    }

    /**
     * Requests a reset from a lifecycle/UI thread. The GL thread consumes it after computing
     * the next frame's camera, before composing the player model.
     */
    public void requestTurnVisualReset() {
        turnVisualResetPending.set(true);
    }

    /**
     * Composes the visual turn accent after the camera has consumed authoritative facing.
     *
     * <p>The authoritative base yaw is assigned every time, so the effect cannot accumulate
     * transform drift or leak back into simulation/camera state.</p>
     */
    public void updateTurnVisualAfterCamera(float dtMillis) {
        refreshObject3DReference();
        if (!authoritativeSimulation || object3D == null) {
            return;
        }
        updateVisualSpin(dtMillis);
        consumeTurnVisualResetRequest();
        turnVisualEffect.update(
                authoritativeTurnVisualYawRadians,
                authoritativeTurnVisualTimeNanos,
                dtMillis
        );
        object3D.objYaw =
                authoritativeModelYawDegrees
                        + turnVisualEffect.yawOffsetDegrees();
    }

    private void consumeTurnVisualResetRequest() {
        if (turnVisualResetPending.getAndSet(false)) {
            turnVisualEffect.reset();
        }
    }

    private void updateVisualSpin(float dtMillis) {
        if (!visualSpinInitialized) {
            spinBlurPlan.clear();
            return;
        }
        double dtSeconds = Math.max(
                0.0,
                Math.min(
                        MAX_VISUAL_SPIN_STEP_SECONDS,
                        dtMillis / 1000.0));
        double targetAngularVelocity =
                compressVisualAngularVelocity(
                        authoritativeAngularVelocity);
        double response = 1.0 - Math.exp(
                -VISUAL_SPIN_RESPONSE_PER_SECOND * dtSeconds);
        visualAngularVelocity +=
                (targetAngularVelocity - visualAngularVelocity)
                        * response;
        visualAxleRadians = Math.IEEEremainder(
                visualAxleRadians + visualAngularVelocity * dtSeconds,
                TWO_PI);
        object3D.objPitch =
                (float) Math.toDegrees(visualAxleRadians);
        updateVioletSpinAppearance(dtSeconds);
        // Blur the same presentation velocity that drives objPitch. Raw physical RPM is
        // intentionally compressed above, so phase and trail can never disagree.
        spinBlurPlan.update(visualAngularVelocity, dtMillis);
    }

    static double compressVisualAngularVelocity(
            double physicalAngularVelocity
    ) {
        double maxAngularVelocity = MAX_VISUAL_SPIN_RPS * TWO_PI;
        return maxAngularVelocity * Math.tanh(
                physicalAngularVelocity / maxAngularVelocity);
    }

    private void updateVioletSpinAppearance(double dtSeconds) {
        double degreesPerFrame = Math.toDegrees(
                Math.abs(visualAngularVelocity) * dtSeconds);
        float targetDetail = fadeOutForAngle(
                degreesPerFrame,
                DETAIL_GROOVES_FULL_BELOW_DEGREES,
                DETAIL_GROOVES_GONE_AT_DEGREES);
        float targetSecondary = fadeOutForAngle(
                degreesPerFrame,
                SECONDARY_GROOVES_FULL_BELOW_DEGREES,
                SECONDARY_GROOVES_GONE_AT_DEGREES);
        float targetPrimary = fadeOutForAngle(
                degreesPerFrame,
                PRIMARY_GROOVES_FULL_BELOW_DEGREES,
                PRIMARY_GROOVES_GONE_AT_DEGREES);
        float targetCore = MAX_CORE_GLOW * fadeInForAngle(
                degreesPerFrame,
                CORE_GLOW_START_DEGREES,
                CORE_GLOW_FULL_DEGREES);
        float response = (float) (1.0 - Math.exp(
                -VISUAL_DETAIL_RESPONSE_PER_SECOND * dtSeconds));
        violetDetailVisibility +=
                (targetDetail - violetDetailVisibility) * response;
        violetSecondaryVisibility +=
                (targetSecondary - violetSecondaryVisibility) * response;
        violetPrimaryVisibility +=
                (targetPrimary - violetPrimaryVisibility) * response;
        violetCoreGlow +=
                (targetCore - violetCoreGlow) * response;
        setVioletSpinAppearance(
                violetPrimaryVisibility,
                violetSecondaryVisibility,
                violetDetailVisibility,
                violetCoreGlow);
    }

    static float fadeOutForAngle(
            double degreesPerFrame,
            double fullBelowDegrees,
            double goneAtDegrees
    ) {
        return 1f - smoothAngleTransition(
                degreesPerFrame,
                fullBelowDegrees,
                goneAtDegrees);
    }

    static float fadeInForAngle(
            double degreesPerFrame,
            double startDegrees,
            double fullAtDegrees
    ) {
        return smoothAngleTransition(
                degreesPerFrame,
                startDegrees,
                fullAtDegrees);
    }

    private static float smoothAngleTransition(
            double degreesPerFrame,
            double startDegrees,
            double endDegrees
    ) {
        double normalized = Math.max(0.0, Math.min(
                1.0,
                (degreesPerFrame - startDegrees)
                        / (endDegrees - startDegrees)));
        double smooth = normalized * normalized
                * (3.0 - 2.0 * normalized);
        return (float) smooth;
    }

    /**
     * Adopts a wheel selected while Settings covered gameplay and preserves its pose.
     */
    private void refreshObject3DReference() {
        UnbatchedObject3DWithOutline selectedObject = PLAYER_OBJECT;
        if (selectedObject == null || selectedObject == object3D) {
            return;
        }
        if (object3D != null) {
            selectedObject.objX = object3D.objX;
            selectedObject.objY = object3D.objY;
            selectedObject.objZ = object3D.objZ;
            selectedObject.objYaw = object3D.objYaw;
            selectedObject.objPitch = object3D.objPitch;
            selectedObject.objRoll = object3D.objRoll;
        }
        object3D = selectedObject;
    }

    /**
     * Updates the visual player directly from the canonical presentation frame.
     * Logical segment IDs are also the terrain generator's stable tile IDs.
     */
    public void applySimulationFrame(
            PlayerSnapshot previous,
            SimulationFrameSnapshot currentFrame,
            double alpha,
            Vec3 renderOrigin) {
        if (currentFrame == null) {
            return;
        }
        PlayerSnapshot current = currentFrame.player;
        applySimulationSnapshots(
                previous,
                current,
                alpha,
                renderOrigin,
                current.lastSupportedSegmentId);
    }

    private static double interpolateAngle(double from, double to, double alpha) {
        double delta = to - from;
        while (delta > Math.PI) delta -= Math.PI * 2.0;
        while (delta < -Math.PI) delta += Math.PI * 2.0;
        return from + delta * alpha;
    }

    /**
     * Interpolates the axle using the authoritative signed motion performed during the tick.
     * Endpoint phases alone are ambiguous whenever a tick turns more than half a revolution.
     */
    static double interpolateAxleRadians(
            double previousAxleRadians, double currentAxleDeltaRadians,
            double alpha) {
        double angle = previousAxleRadians + currentAxleDeltaRadians * alpha;
        return Math.IEEEremainder(angle, Math.PI * 2.0);
    }

    public PlayerInputAPI getInputAPI() {
        return inputAPI;
    }

    public void beginTileInteractionSweep() {
        tileInteractionSweepOpen = true;
        clearPendingFootingCandidate();
        frameStartState.setTileBelow(null);
        frameStartState.setCollisionTriangleIndex(-1);
    }

    public void finishTileInteractionSweep() {
        if (!tileInteractionSweepOpen) {
            return;
        }
        tileInteractionSweepOpen = false;
        if (pendingFootingTile != null) {
            setHasFooting(pendingFootingTile, pendingFootingTriangleIndex);
        }
        clearPendingFootingCandidate();
    }

    public void interactWith(Tile tile) {
        boolean openedLocalSweep = false;
        if (!tileInteractionSweepOpen) {
            beginTileInteractionSweep();
            openedLocalSweep = true;
        }
        if (tile != null && !tile.isEmptySegment()) {
            probeTileForFooting(tile);
        }
        if (openedLocalSweep) {
            finishTileInteractionSweep();
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
        boolean grounded = frameStartState.getTileBelow() != null;

        // Sticky rotation decay
        float stickyTime = max(0f, frameStartState.getStickyRotationTime() - dtMillis);
        frameStartState.setStickyRotationTime(stickyTime);
        if (stickyTime == 0 && frameStartState.getStickyRotationAng() != 0f) {
            float dYaw = minByAbs(signum(frameStartState.getStickyRotationAng())
                    * LEGACY_STICKY_ROTATION_ANGLE_DECAY_RATE * dtMillis,
                    frameStartState.getStickyRotationAng());
            object3D.objYaw -= dYaw;
            frameStartState.setStickyRotationAng(frameStartState.getStickyRotationAng() - dYaw);
        }


        if (swipeDx != 0f) {
            float dYaw =
                    swipeDx * LEGACY_ROTATION_SWIPE_SENSITIVITY;
            frameStartState.setDir(rotY(frameStartState.getDir(), dYaw));

            // Visual rotation (degrees)
            object3D.objYaw -= dYaw * 180.0f / PI;

            // Sticky rotation effect
            frameStartState.setStickyRotationAng(frameStartState.getStickyRotationAng()
                    - swipeDx * LEGACY_STICKY_ROTATION_COEFFICIENT);
            frameStartState.setStickyRotationTime(
                    LEGACY_STICKY_ROTATION_LASTING_TIME);
            object3D.objYaw -=
                    swipeDx * LEGACY_STICKY_ROTATION_COEFFICIENT;
        }

        refreshActiveHorizontalSpeed();

        if (grounded) {
            frameStartState.setMoveDir(frameStartState.getDir());
        }
    }

    private void refreshActiveHorizontalSpeed() {
        Tile groundedTile = frameStartState.getTileBelow();
        if (groundedTile != null) {
            frameStartState.setActiveHorizontalSpeed(
                    groundedTile.getProfile().applyHorizontalSpeed(config.playerSpeed)
            );
            return;
        }
        frameStartState.setActiveHorizontalSpeed(
                max(frameStartState.getActiveHorizontalSpeed(), config.playerSpeed)
        );
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

    private void setHasFooting(Tile tile, int collisionTriangleIndex) {
        frameStartState.setTileBelow(tile);
        frameStartState.setCollisionTriangleIndex(collisionTriangleIndex);
        lastRecoverableTrackY = object3D.objY;
        hasRecoverableTrackAnchor = true;
    }

    private void probeTileForFooting(Tile tile) {
        float verticalTravel = getVerticalTravelForCollision();
        int triangleIndex = frameStartState.probeCollisionTriangleIndex(tile, verticalTravel);
        if (triangleIndex < 0) {
            return;
        }
        float collisionDistance = frameStartState.getCollisionProbeDistance();
        if (collisionDistance < pendingFootingDistance) {
            pendingFootingTile = tile;
            pendingFootingTriangleIndex = triangleIndex;
            pendingFootingDistance = collisionDistance;
        }
    }

    private void clearPendingFootingCandidate() {
        pendingFootingTile = null;
        pendingFootingTriangleIndex = -1;
        pendingFootingDistance = Float.POSITIVE_INFINITY;
    }

    private void updateDeathSpikeCollisionState() {
        if (dead) return;
        int spikeCount = frameStartState.getNearbyDeathSpikeCount();
        if (spikeCount <= 0) return;

        float hazardRadius = max(config.playerWidth * SPIKE_KILL_RADIUS_FACTOR, SPIKE_KILL_MIN_RADIUS);
        float hazardRadiusSq = hazardRadius * hazardRadius;
        float hazardHeightTolerance = max(config.playerHeight * SPIKE_KILL_HEIGHT_FACTOR, SPIKE_KILL_MIN_HEIGHT);

        float px = object3D.objX;
        float py = object3D.objY;
        float pz = object3D.objZ;

        for (int i = 0; i < spikeCount; ++i) {
            float dx = px - frameStartState.getNearbyDeathSpikeX(i);
            float dz = pz - frameStartState.getNearbyDeathSpikeZ(i);
            if (dx * dx + dz * dz > hazardRadiusSq) {
                continue;
            }
            float dy = abs(py - frameStartState.getNearbyDeathSpikeY(i));
            if (dy <= hazardHeightTolerance) {
                dead = true;
                return;
            }
        }
    }

    private void updateUnrecoverableFallDeathState() {
        if (dead || object3D == null) {
            return;
        }
        if (RecoverabilityJudge.shouldLoseRunFromUnrecoverableFall(
                config,
                recoveryCapabilities,
                hasRecoverableTrackAnchor,
                frameStartState.getTileBelow() != null,
                frameStartState.getNearestGroundDistance(),
                frameStartState.getNearestGroundY(),
                frameStartState.getLastMove().y,
                object3D.objY,
                lastRecoverableTrackY
        )) {
            dead = true;
        }
    }

    static boolean shouldDieFromUnrecoverableFall(
            PlayerConfig config,
            boolean hasRecoverableTrackAnchor,
            boolean hasFooting,
            float nearestGroundDistance,
            float nearestGroundY,
            float verticalMoveY,
            float playerY,
            float lastRecoverableTrackY
    ) {
        return RecoverabilityJudge.shouldLoseRunFromUnrecoverableFall(
                config,
                RecoveryCapabilities.NONE,
                hasRecoverableTrackAnchor,
                hasFooting,
                nearestGroundDistance,
                nearestGroundY,
                verticalMoveY,
                playerY,
                lastRecoverableTrackY
        );
    }

    static float unrecoverableDropThreshold(PlayerConfig config) {
        return max(
                config.playerHeight * UNRECOVERABLE_FALL_DROP_FACTOR,
                UNRECOVERABLE_FALL_MIN_DROP
        );
    }

    static float recoverableGroundDistance(PlayerConfig config) {
        return max(
                config.playerHeight * UNRECOVERABLE_FALL_RECOVERY_GROUND_FACTOR,
                config.playerHeight
        );
    }

    static float belowTileMarginThreshold(PlayerConfig config) {
        return max(
                config.playerHeight * UNRECOVERABLE_FALL_BELOW_TILE_MARGIN_FACTOR,
                UNRECOVERABLE_FALL_BELOW_TILE_MIN_MARGIN
        );
    }

    public final class PlayerInputAPI {
        public void swipe(float dx, float dy) {
            pendingSwipeDx += dx
                    * TouchSensitivitySettings.getHorizontalInputScale();
            pendingSwipeDy += dy
                    * TouchSensitivitySettings.getVerticalInputScale();
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
            if (authoritativeSimulation && authoritativeSnapshot != null) {
                target.jumpSwipeMin = 0f;
                target.jumpSwipeMax = 1f;
                target.jumpSwipeMilestones = new float[]{
                        (float) sharedPhysicsConfig.jumpChargeThreshold
                };
                target.jumpSwipeValue = (float) authoritativeSnapshot.gestureCharge;
                target.airJumpCharges = authoritativeSnapshot.airJumpCharges;
                return;
            }
            float swipeVal = logic.getCumulativeSwipeDy();
            target.jumpSwipeMin = 0f;
            target.jumpSwipeMax = jumpConfig.jumpMaxSwipe;
            target.jumpSwipeMilestones = new float[]{ jumpConfig.jumpSwipeThresholdPx };
            target.jumpSwipeValue = Math.min(swipeVal, jumpConfig.jumpMaxSwipe);
            target.airJumpCharges = 0;
        }
    }


}
