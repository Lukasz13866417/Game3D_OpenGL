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
import android.os.Trace;

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
import com.example.game3d_opengl.game.player.player_logic.PlayerSupportSurface;
import com.example.game3d_opengl.rendering.RenderTarget;
import com.example.game3d_opengl.rendering.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents the player character in the game world.
 * Handles movement, collision detection, physics, and rendering.
 */
public class Player implements WorldActor {
    private static final double TEMPORAL_SHUTTER_FRAME_FRACTION = 0.75;
    private static final double TEMPORAL_BLEND_START_PIXELS = 0.5;
    private static final double TEMPORAL_BLEND_FULL_PIXELS = 2.5;
    private static final double TEMPORAL_ACTIVATION_EPSILON = 1.0e-4;
    static final double DETAIL_GROOVES_FULL_BELOW_DEGREES = 8.0;
    static final double DETAIL_GROOVES_GONE_AT_DEGREES = 12.0;
    static final double SECONDARY_GROOVES_FULL_BELOW_DEGREES = 14.0;
    static final double SECONDARY_GROOVES_GONE_AT_DEGREES = 18.0;
    static final double PRIMARY_GROOVES_FULL_BELOW_DEGREES = 24.0;
    static final double PRIMARY_GROOVES_GONE_AT_DEGREES = 30.0;
    static final double CORE_GLOW_START_DEGREES = 22.0;
    static final double CORE_GLOW_FULL_DEGREES = 30.0;
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
    private double presentedAxleDeltaRadians;
    private boolean visualSpinInitialized;
    private boolean visualAxlePoseUpdated;
    private boolean presentedAxleInitialized;
    private double lastPresentedAxleRadians;
    private double visualFrameIntervalSeconds;
    private final WheelTemporalSamplingPlanner wheelSamplingPlanner =
            new WheelTemporalSamplingPlanner();
    private final WheelMotionGlowRenderer wheelMotionGlowRenderer =
            new WheelMotionGlowRenderer();
    private WheelTemporalSamplingPlanner.Plan preparedWheelMotionPlan;
    private float[] preparedWheelMotionViewProjection;
    private float preparedWheelCoreIntensity;
    private float preparedWheelBloomCorrectionBlend;
    private float preparedMintSharpScale = 1f;
    private float violetPrimaryVisibility = 1f;
    private float violetSecondaryVisibility = 1f;
    private float violetDetailVisibility = 1f;
    private float violetCoreGlow;
    private long authoritativeTurnVisualTimeNanos;

    // transient input buffers (per-frame)
    private float pendingSwipeDx = 0f;
    private float pendingSwipeDy = 0f;
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
    private PlayerSupportSurface pendingFootingTile = null;
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
        PlayerAssets.setMintGrooveSharpScale(1f);
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
            drawMultipartWheelBody(mvpMatrix, false);
        }
    }

    /**
     * Draws either the ordinary complete player or, while a temporal exposure is active, only
     * the non-groove body. The latter leaves an emitter-free depth buffer for temporal occlusion.
     *
     * @return true when the groove is being supplied by the temporal renderer
     */
    public boolean drawBodyBeforeTerrain(float[] viewProjection) {
        refreshObject3DReference();
        if (object3D == null) {
            return false;
        }
        Mesh3DInfill groove = PlayerAssets.mintGrooveEmissiveMesh();
        boolean deferGroove = preparedWheelMotionPlan != null
                && groove != null;
        drawMultipartWheelBody(viewProjection, deferGroove);
        return deferGroove;
    }

    /**
     * Keeps mathematically rotationally symmetric parts at a stable roll phase. Their faceted
     * polygon shells otherwise reveal a second high-frequency temporal signal even after the
     * truthful groove pattern has been band-limited. The tread/grooves still use physical roll;
     * the dedicated motion-band shell is always supplied by the temporal renderer.
     */
    private void drawMultipartWheelBody(
            float[] viewProjection,
            boolean deferGroove) {
        Mesh3DInfill groove = PlayerAssets.mintGrooveEmissiveMesh();
        Mesh3DInfill motionBand =
                PlayerAssets.mintMotionBandEmissiveMesh();
        Mesh3DInfill[] rollInvariant =
                PlayerAssets.mintRollInvariantMeshes();
        boolean isMintMultipart = groove != null || motionBand != null
                || rollInvariant.length > 0;
        if (!isMintMultipart) {
            object3D.draw(viewProjection);
            return;
        }

        object3D.drawExcludingFillMeshes(
                viewProjection,
                rollInvariant,
                motionBand,
                deferGroove ? groove : null);
        float physicalPitch = object3D.objPitch;
        try {
            object3D.objPitch = 0f;
            for (Mesh3DInfill invariantMesh : rollInvariant) {
                object3D.drawOnlyFillMesh(
                        viewProjection, invariantMesh);
            }
        } finally {
            object3D.objPitch = physicalPitch;
        }
    }

    /**
     * Draws the ordinary sharp groove fallback after the body but before terrain. The successful
     * temporal path sets its scale to zero, so this method becomes a cheap no-op.
     */
    public void drawDeferredSharpGroove(float[] viewProjection) {
        refreshObject3DReference();
        Mesh3DInfill groove = PlayerAssets.mintGrooveEmissiveMesh();
        if (object3D == null || groove == null
                || !(preparedMintSharpScale > 1.0e-4f)) {
            return;
        }
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthMask(false);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        GLES20.glDisable(GLES20.GL_BLEND);
        object3D.drawOnlyFillMesh(viewProjection, groove);
        GLES20.glDepthFunc(GLES20.GL_LESS);
        GLES20.glDepthMask(true);
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
        // Shared meshes stay resident across stage transitions. The temporal atlas/program are
        // owned by this presentation instance and are rebuilt lazily.
        wheelMotionGlowRenderer.cleanupGPUResourcesRecursively();
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        reloadLoadedGPUResourcesOnContextLoss();
        wheelMotionGlowRenderer.reloadGPUResourcesRecursivelyOnContextLoss();
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
        visualSpinInitialized = false;
        visualAxlePoseUpdated = false;
        presentedAxleInitialized = false;
        visualFrameIntervalSeconds = 0.0;
        presentedAxleDeltaRadians = 0.0;
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
        // The fixed-step snapshots already provide an unwrapped axle delta specifically so the
        // presentation does not need to guess a shortest angular path. Use that exact phase and
        // matching interpolated velocity; independently integrating a smoothed copy drifts from
        // both physics and the temporal exposure.
        visualAxleRadians = axle;
        visualAngularVelocity =
                sanitizeVisualAngularVelocity(authoritativeAngularVelocity);
        visualSpinInitialized = true;
        visualAxlePoseUpdated = true;
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
            visualFrameIntervalSeconds = 0.0;
            presentedAxleDeltaRadians = 0.0;
            return;
        }
        double rawDtSeconds = Float.isFinite(dtMillis)
                ? Math.max(0.0, dtMillis / 1000.0)
                : 0.0;
        // Drive alias classification from the phase that actually reached presentation. A frame
        // intentionally rendered without a simulation update must remain sharp even when the
        // authoritative motor velocity is non-zero. The physical velocity only unwraps rotations
        // larger than one turn, where the displayed wrapped endpoints are inherently ambiguous.
        visualFrameIntervalSeconds = rawDtSeconds;
        visualAngularVelocity =
                sanitizeVisualAngularVelocity(authoritativeAngularVelocity);
        if (!presentedAxleInitialized) {
            lastPresentedAxleRadians = visualAxleRadians;
            presentedAxleInitialized = true;
            presentedAxleDeltaRadians = 0.0;
        } else if (!visualAxlePoseUpdated || !(rawDtSeconds > 0.0)) {
            presentedAxleDeltaRadians = 0.0;
        } else {
            double expectedDelta = saturatedSignedProduct(
                    visualAngularVelocity, rawDtSeconds);
            double presentedDelta = resolvePresentedAxleDelta(
                    lastPresentedAxleRadians,
                    visualAxleRadians,
                    expectedDelta);
            presentedAxleDeltaRadians = presentedDelta;
        }
        lastPresentedAxleRadians = visualAxleRadians;
        visualAxlePoseUpdated = false;
        object3D.objPitch =
                (float) Math.toDegrees(visualAxleRadians);
        updateVioletSpinAppearance(Math.min(
                MAX_VISUAL_SPIN_STEP_SECONDS, rawDtSeconds));
    }

    static double sanitizeVisualAngularVelocity(
            double physicalAngularVelocity
    ) {
        // This presentation boundary intentionally no longer compresses or clamps RPM. Temporal
        // filtering and its phase-invariant high-speed limit handle unrestricted finite speed.
        return Double.isFinite(physicalAngularVelocity)
                ? physicalAngularVelocity
                : 0.0;
    }

    /**
     * Recovers the signed displayed axle delta from wrapped endpoints. The expected physical
     * delta is used only to choose the equivalent whole-turn branch.
     */
    static double resolvePresentedAxleDelta(
            double previousRadians,
            double currentRadians,
            double expectedDeltaRadians) {
        double twoPi = Math.PI * 2.0;
        if (!Double.isFinite(previousRadians)
                || !Double.isFinite(currentRadians)) {
            return 0.0;
        }
        double wrapped = Math.IEEEremainder(
                currentRadians - previousRadians, twoPi);
        if (!Double.isFinite(expectedDeltaRadians)) {
            return wrapped;
        }
        double turns = Math.rint(
                (expectedDeltaRadians - wrapped) / twoPi);
        double resolved = wrapped + turns * twoPi;
        return Double.isFinite(resolved)
                ? resolved
                : expectedDeltaRadians;
    }

    private static double saturatedSignedProduct(
            double signedValue,
            double nonNegativeValue) {
        if (signedValue == 0.0 || !(nonNegativeValue > 0.0)) {
            return 0.0;
        }
        double magnitude = Math.abs(signedValue);
        if (magnitude > Double.MAX_VALUE / nonNegativeValue) {
            return Math.copySign(Double.MAX_VALUE, signedValue);
        }
        return signedValue * nonNegativeValue;
    }

    static double saturatedSignedQuotient(
            double signedValue,
            double positiveDivisor) {
        if (signedValue == 0.0 || !(positiveDivisor > 0.0)
                || !Double.isFinite(signedValue)
                || !Double.isFinite(positiveDivisor)) {
            return 0.0;
        }
        double magnitude = Math.abs(signedValue);
        if (positiveDivisor < 1.0
                && magnitude > Double.MAX_VALUE * positiveDivisor) {
            return Math.copySign(Double.MAX_VALUE, signedValue);
        }
        return signedValue / positiveDivisor;
    }

    /**
     * Plans the mint-groove exposure before the sharp scene draw. The current-pose core and the
     * later direct bloom contribution therefore consume one consistent, allocation-free plan.
     */
    public void prepareWheelMotionGlow(
            float[] viewProjection,
            int bloomWidth,
            int bloomHeight) {
        refreshObject3DReference();
        preparedWheelMotionPlan = null;
        preparedWheelMotionViewProjection = null;
        preparedWheelCoreIntensity = 0f;
        preparedWheelBloomCorrectionBlend = 0f;
        preparedMintSharpScale = 1f;
        wheelMotionGlowRenderer.discardPreparedFrame();
        if (object3D == null || PlayerAssets.mintGrooveEmissiveMesh() == null
                || viewProjection == null || !(visualFrameIntervalSeconds > 0.0)
                || !wheelMotionGlowRenderer.isSupported()) {
            PlayerAssets.setMintGrooveSharpScale(1f);
            return;
        }

        float projectedRadius = wheelMotionGlowRenderer.projectedRadiusPixels(
                viewProjection,
                object3D,
                config.playerHeight * 0.5f,
                bloomWidth,
                bloomHeight);
        if (!(projectedRadius > 0.25f)) {
            PlayerAssets.setMintGrooveSharpScale(1f);
            return;
        }
        if (!wheelMotionGlowRenderer.prepareFrameCapacity(
                bloomWidth, bloomHeight, projectedRadius)) {
            PlayerAssets.setMintGrooveSharpScale(1f);
            return;
        }
        WheelTemporalSamplingPlanner.Plan plan =
                wheelSamplingPlanner.planFromPresentedDelta(
                presentedAxleDeltaRadians,
                visualFrameIntervalSeconds * TEMPORAL_SHUTTER_FRAME_FRACTION,
                visualFrameIntervalSeconds,
                projectedRadius);
        if (!shouldUseTemporalExposure(plan)) {
            PlayerAssets.setMintGrooveSharpScale(1f);
            return;
        }

        // Once temporal filtering activates, use one complete normalized exposure instead of an
        // opaque dim sharp mesh plus additive copies. At the 0.5-pixel activation boundary the
        // exposure is already visually indistinguishable from the current pose, so this avoids
        // both dark transition grooves and double energy without a visible pop.
        PlayerAssets.setMintGrooveSharpScale(0f);
        preparedMintSharpScale = 0f;
        preparedWheelMotionPlan = plan;
        preparedWheelMotionViewProjection = viewProjection;
        preparedWheelCoreIntensity = 1f;
        preparedWheelBloomCorrectionBlend =
                (float) wheelBloomCorrectionBlend(plan);
    }

    /** Prewarms shaders, buffers and the fixed temporal atlas outside active frame timing. */
    public void prepareWheelMotionGlowResources(
            int bloomWidth,
            int bloomHeight) {
        wheelMotionGlowRenderer.preload(bloomWidth, bloomHeight);
    }

    /** Inserts the prepared wheel-local exposure before terrain is drawn. */
    public boolean renderPreparedWheelMotionCore(
            RenderTarget sceneTarget,
            int viewportWidth,
            int viewportHeight) {
        if (preparedWheelMotionPlan == null
                || preparedWheelMotionViewProjection == null
                || !(preparedWheelCoreIntensity > 0f)) {
            return false;
        }
        boolean rendered;
        Trace.beginSection("G3D:wheelMotionCore");
        try {
            rendered = wheelMotionGlowRenderer.renderSceneCore(
                    sceneTarget,
                    viewportWidth,
                    viewportHeight,
                    preparedWheelMotionViewProjection,
                    object3D,
                    preparedWheelMotionPlan,
                    preparedWheelCoreIntensity);
        } finally {
            Trace.endSection();
        }
        if (!rendered) {
            preparedWheelMotionPlan = null;
            preparedWheelMotionViewProjection = null;
            preparedWheelCoreIntensity = 0f;
            preparedWheelBloomCorrectionBlend = 0f;
            preparedMintSharpScale = 1f;
            PlayerAssets.setMintGrooveSharpScale(1f);
        }
        return rendered;
    }

    /** Adds the per-pixel bloom residual missing from the ordinary scene bright pass. */
    public void contributeWheelMotionGlow(
            RenderTarget destination,
            RenderTarget sceneSource,
            int viewportWidth,
            int viewportHeight) {
        if (preparedWheelMotionPlan == null
                || preparedWheelMotionViewProjection == null
                || !(preparedWheelBloomCorrectionBlend > 0f)) {
            return;
        }
        Trace.beginSection("G3D:wheelMotionGlow");
        try {
            wheelMotionGlowRenderer.contributeBloom(
                    destination,
                    sceneSource,
                    viewportWidth,
                    viewportHeight,
                    preparedWheelMotionPlan,
                    preparedWheelBloomCorrectionBlend);
        } finally {
            Trace.endSection();
        }
    }

    /** Restores an ordinary sharp source when the bloom path is unavailable. */
    public void disableWheelMotionGlow() {
        preparedWheelMotionPlan = null;
        preparedWheelMotionViewProjection = null;
        preparedWheelCoreIntensity = 0f;
        preparedWheelBloomCorrectionBlend = 0f;
        preparedMintSharpScale = 1f;
        PlayerAssets.setMintGrooveSharpScale(1f);
        wheelMotionGlowRenderer.discardPreparedFrame();
    }

    static double wheelTemporalBlend(
            WheelTemporalSamplingPlanner.Plan plan) {
        if (plan == null) {
            return 0.0;
        }
        return Math.max(
                wheelBloomCorrectionBlend(plan),
                plan.continuousBandBlend());
    }

    /** Smoothly changes from legacy sharp bloom to the normalized temporal bright-pass policy. */
    static double wheelBloomCorrectionBlend(
            WheelTemporalSamplingPlanner.Plan plan) {
        if (plan == null) {
            return 0.0;
        }
        return smoothStep(
                TEMPORAL_BLEND_START_PIXELS,
                TEMPORAL_BLEND_FULL_PIXELS,
                plan.projectedSweepPixels());
    }

    static boolean shouldUseTemporalExposure(
            WheelTemporalSamplingPlanner.Plan plan) {
        return wheelTemporalBlend(plan) > TEMPORAL_ACTIVATION_EPSILON;
    }

    private static double smoothStep(double lower, double upper, double value) {
        if (!(value > lower)) {
            return 0.0;
        }
        if (value >= upper) {
            return 1.0;
        }
        double unit = (value - lower) / (upper - lower);
        return unit * unit * (3.0 - 2.0 * unit);
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

    public void interactWith(PlayerSupportSurface tile) {
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

    /** Compatibility hook for the old diagnostic spike addon. */
    public void recordLegacyDeathSpike(float x, float y, float z) {
        frameStartState.addNearbyDeathSpike(x, y, z);
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
        PlayerSupportSurface groundedTile = frameStartState.getTileBelow();
        if (groundedTile != null) {
            frameStartState.setActiveHorizontalSpeed(
                    groundedTile.applyHorizontalSpeed(config.playerSpeed)
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

    private void setHasFooting(
            PlayerSupportSurface tile, int collisionTriangleIndex) {
        frameStartState.setTileBelow(tile);
        frameStartState.setCollisionTriangleIndex(collisionTriangleIndex);
        lastRecoverableTrackY = object3D.objY;
        hasRecoverableTrackAnchor = true;
    }

    private void probeTileForFooting(PlayerSupportSurface tile) {
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
