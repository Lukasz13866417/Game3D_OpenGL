package com.example.game3d_opengl.game.stage.stages.main;


import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;
import android.content.res.AssetManager;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Trace;
import android.util.Log;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.simulation.FixedStepAccumulator;
import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.simulation.PlayerSnapshot;
import com.example.game3d.core.simulation.SimulationFrameSnapshot;
import com.example.game3d.core.simulation.SimulationEvent;
import com.example.game3d.core.simulation.StepResult;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainCollisionIndex;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.TerrainOutput;
import com.example.game3d.authoring.GameplayTerrainStream;
import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.hud.GameHUD;
import com.example.game3d_opengl.game.player.core.AndroidSimulationController;
import com.example.game3d_opengl.game.player.player_character.PlayerConfig;
import com.example.game3d_opengl.game.settings.TouchSensitivitySettings;
import com.example.game3d_opengl.game.settings.SlowFrameStats;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.stage.stage_api.RenderContext;
import com.example.game3d_opengl.game.stage.stage_api.SceneRenderer;
import com.example.game3d_opengl.game.terrain.presentation.TerrainPresentation;
import com.example.game3d_opengl.game.terrain.presentation.TerrainRendererRegistry;
import com.example.game3d_opengl.game.terrain.track_elements.portal.PortalLightingEnvironment;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.util.AndroidGameClock;
import com.example.game3d_opengl.game.util.GameRandom;

import java.util.List;

public class GameplayStage extends Stage {
    static final String FRAME_TIMING_LOG_TAG = "GameFrameTiming";
    static final String FRAME_DETAIL_LOG_TAG = "GameFrameDetail";
    static final int DEFAULT_INITIAL_AIR_JUMP_CHARGES = 0;
    private static final int REPLENISH_LEVEL_COUNT = 1;
    private static final int REPLENISH_TERRAIN_THRESHOLD = 400;
    private static final int LOW_TERRAIN_THRESHOLD = 300;
    private static final int NEXT_SESSION_ACTIVE_PREPARATION_CHUNK_BUDGET = 8;
    private static final int NEXT_SESSION_LOST_PREPARATION_CHUNK_BUDGET = 96;
    private static final float LOST_RUN_MAX_HIDDEN_WAIT_MS = 900f;
    private static final float COMMITTED_FRONTIER_LOOKAHEAD_MS = 1600f;
    private static final int COMMITTED_FRONTIER_MARGIN_TILES = 8;
    private static final int COMMITTED_FRONTIER_HARD_MIN_MARGIN_TILES = 2;
    private static final int COMMITTED_FRONTIER_GRACE_TILES = 16;
    static final long SIMULATION_OVERRUN_LOG_INTERVAL_NANOS = 1_000_000_000L;
    static final long INVARIANT_FAILURE_LOG_INTERVAL_NANOS = 1_000_000_000L;
    private static final int FRAME_TIMING_WINDOW_SIZE = 128;
    private static final float MISSED_120_HZ_SLOT_THRESHOLD_MS = 12.5f;
    // Emergency catch-up runs on the GL thread. A large command burst directly becomes a long
    // rendered frame; the prepared lead/grace window gives us time to spread this work out.
    static final int COMMITTED_FRONTIER_EXTRA_GENERATION_BUDGET = 16;
    static final int TERRAIN_AUTHORING_COMMAND_BUDGET = 128;

    private static final float INITIAL_PHASE_DURATION_MS = 2800f;
    private static final float PHASE_DURATION_GROWTH_FACTOR = 1.35f;
    private static final float THEME_TRANSITION_DURATION_MS = 900f;
    private static final float THEME_RGB_SUM = 1f;
    private static final float THEME_MIN_CHANNEL = 0.15f;
    private static final float THEME_MAX_CHANNEL = 0.80f;
    private static final float THEME_BRIGHTNESS_RED_COEFF = 0.92f;
    private static final float THEME_BRIGHTNESS_GREEN_COEFF = 1.28f;
    private static final float THEME_BRIGHTNESS_BLUE_COEFF = 0.84f;
    private static final float THEME_BRIGHTNESS_TARGET = 0.95f;
    private static final float THEME_BRIGHTNESS_COMPENSATION = 0.75f;
    private static final float THEME_PASTEL_WHITE_MIX = 0.34f;
    private static final int HUD_UPCOMING_PHASE_COUNT = 3;
    private static final float HUD_PHASE_PREVIEW_WINDOW_MS =
            INITIAL_PHASE_DURATION_MS
                    * (1f
                    + PHASE_DURATION_GROWTH_FACTOR
                    + PHASE_DURATION_GROWTH_FACTOR * PHASE_DURATION_GROWTH_FACTOR);
    private static final float TERRAIN_LIGHT_BEHIND_PLAYER_DISTANCE = 2.2f;
    private static final float ADDON_LIGHT_BEHIND_PLAYER_DISTANCE = 8.5f;
    private static final float TERRAIN_LIGHT_ABOVE_PLAYER_HEIGHT = 14f;
    private static final float ADDON_LIGHT_ABOVE_PLAYER_HEIGHT = 0.5f;

    private enum RunState {
        ACTIVE,
        LOST_WAITING_FOR_NEXT_SESSION
    }

    private PreparedGameplaySession preparedSession;
    private final TerrainRendererRegistry terrainRenderers;

    public GameplayStage(MyGLRenderer.StageManager stageManager, PreparedGameplaySession preparedSession){
        super(stageManager);
        if (preparedSession == null) {
            throw new IllegalArgumentException("preparedSession == null");
        }
        this.preparedSession = preparedSession;
        this.terrainRenderers = stageManager.terrainRendererRegistry();
    }

    @Override
    protected void setupAssets(AssetManager assetManager) {
        // Session assets are prepared before this stage is created.
    }

    @Override
    protected void onTouchDown(float x, float y) {
        onTouchDownTimed(x, y, AndroidGameClock.nowNanos(), -1L);
    }

    @Override
    protected void onTouchDownTimed(
            float x, float y, long timeNanos, long sequence) {
        resetPlayerTurnVisual();
        hudOwnsCurrentGesture = false;
        if (gameHUD != null && gameHUD.handleTouchDown(x, y)) {
            hudOwnsCurrentGesture = true;
            resetPlayerTurnVisual();
            return;
        }
        if (gameHUD != null && gameHUD.isPaused()) {
            hudOwnsCurrentGesture = true;
            return;
        }
        if (simulationController == null) {
            return;
        }
        if (simulationController.touchDown(timeNanos)
                && player != null) {
            gameplayOwnsCurrentGesture = true;
            gameplayGestureStartNanos = timeNanos;
            player.beginTurnVisualHold();
        }
    }

    @Override
    protected void onTouchUp(float x, float y) {
        onTouchUpTimed(x, y, AndroidGameClock.nowNanos(), -1L);
    }

    @Override
    protected void onTouchUpTimed(
            float x, float y, long timeNanos, long sequence) {
        if (hudOwnsCurrentGesture) {
            hudOwnsCurrentGesture = false;
            if (gameHUD != null) {
                applyHudAction(
                        gameHUD.handleTouchUp(x, y),
                        timeNanos
                );
            }
            return;
        }
        endGameplayTurnVisualHoldIfCurrent(timeNanos);
        if (gameHUD != null && gameHUD.isPaused()) {
            return;
        }
        if (simulationController == null) return;
        simulationController.touchUp(timeNanos);
    }

    @Override
    protected void onTouchMove(float x1, float y1, float x2, float y2) {
        onTouchMoveTimed(
                x1, y1, x2, y2, AndroidGameClock.nowNanos(), -1L);
    }

    @Override
    protected void onTouchMoveTimed(
            float x1, float y1, float x2, float y2,
            long timeNanos, long sequence) {
        if (hudOwnsCurrentGesture) {
            if (gameHUD != null) {
                gameHUD.handleTouchMove(x2, y2);
            }
            return;
        }
        if (gameHUD != null && gameHUD.isPaused()) {
            return;
        }
        if (simulationController == null) return;
        float rawDx = x2 - x1;
        float rawDy = y2 - y1;
        float scaledDx = rawDx
                * TouchSensitivitySettings.getHorizontalInputScale();
        float scaledDy = rawDy
                * TouchSensitivitySettings.getVerticalInputScale();
        simulationController.touchMoveDelta(
                scaledDx,
                scaledDy,
                rawDx,
                rawDy,
                timeNanos);
    }

    @Override
    protected void onTouchCancelTimed(
            float x, float y, long timeNanos, long sequence) {
        if (hudOwnsCurrentGesture) {
            hudOwnsCurrentGesture = false;
            if (gameHUD != null) {
                gameHUD.cancelTouchGesture();
            }
            return;
        }
        resetGameplayTurnVisualIfCurrent(timeNanos);
        if (simulationController != null) {
            simulationController.cancelGesture(timeNanos);
        }
    }

    GameplayTerrainStream terrainGenerator;

    private Camera camera;
    private int frameCounter = 0;
    private int timingMissedFrames;
    private int timingCollisionRebuilds;
    private int timingTerrainGenerationFrames;
    private float timingMaxDtMs;
    private long timingMaxCpuNanos;
    private long timingMaxTerrainNanos;
    private long timingMaxCollisionNanos;
    private long timingMaxSimulationNanos;
    private long timingMaxRenderNanos;
    private long timingMaxHudNanos;
    private long timingMaxPreparationNanos;
    private int timingSimulationTicks;
    private int timingMaxSimulationTicks;
    private int timingCatchUpFrames;
    private int timingSimulationOverruns;
    private long timingMaxRetainedSimulationNanos;
    private int timingTerrainCommits;
    private int timingRenderOriginChanges;
    private long timingMissPreviousMaxCpuNanos;
    private long timingMissPreviousMaxTerrainNanos;
    private long timingMissPreviousMaxCollisionNanos;
    private long timingMissPreviousMaxSimulationNanos;
    private long timingMissPreviousMaxRenderNanos;
    private long timingMissPreviousMaxHudNanos;
    private int timingMissPreviousMaxSimulationTicks;
    private int timingMissPreviousMaxTerrainCommits;
    private int timingMissesAfterStageCpuOverBudget;
    private int timingMissesAfterStageCpuUnderBudget;
    private int currentSimulationTicks;
    private boolean currentSimulationOverrun;
    private long currentRetainedSimulationNanos;
    private int currentTerrainCommitCount;
    private boolean currentRenderOriginChanged;
    private boolean hasPreviousFrameTiming;
    private long previousFrameCpuNanos;
    private long previousFrameTerrainNanos;
    private long previousFrameCollisionNanos;
    private long previousFrameSimulationNanos;
    private long previousFrameRenderNanos;
    private long previousFrameHudNanos;
    private int previousFrameSimulationTicks;
    private int previousFrameTerrainCommitCount;
    private final FColor colorTheme = CLR(0.33f, 0.66f, 0f, 1f);
    private float themeR = 0.7f;
    private float themeG = 0f;
    private float themeB = 0f;
    private float targetThemeR = themeR;
    private float targetThemeG = themeG;
    private float targetThemeB = themeB;
    private float deltaThemeR = 0f;
    private float deltaThemeG = 0f;
    private float deltaThemeB = 0f;
    private float themeTransitionRemainingMs = 0f;
    private boolean themeTransitioning = false;
    private final GameplayPhaseTimeline phaseTimeline =
            new GameplayPhaseTimeline(INITIAL_PHASE_DURATION_MS, PHASE_DURATION_GROWTH_FACTOR);
    private final float[] hudUpcomingPhaseMilestonesMs = new float[HUD_UPCOMING_PHASE_COUNT];
    private boolean phaseTriggeredThisFrame = false;
    private int nextRandomLevelIndex = 0;
    private Player player;
    private AndroidSimulationController simulationController;
    private TerrainOutput terrainOutput;
    private TerrainPresentation terrainPresentation;
    private final PhysicsConfig presentationPhysicsConfig = new PhysicsConfig();
    private Vec3 renderOrigin = Vec3.ZERO;
    private long deferredSimulationElapsedNanos;
    private long simulationWallCursorNanos = -1L;
    private long lastSimulationOverrunLogNanos = Long.MIN_VALUE;
    private long lastInvariantFailureLogNanos = Long.MIN_VALUE;
    private int suppressedInvariantFailureLogs;
    private boolean hudSimulationPaused;
    private boolean lifecycleSimulationPaused;
    private LightSource lightSource;
    private LightSource addonLightSource;

    private GameHUD gameHUD;
    private BloomPostProcessor bloomPostProcessor;
    private final RenderContext mainRenderCtx = new RenderContext();
    private final SceneRenderer sceneRenderer = this::renderScene;
    private PreparedGameplaySession nextPreparedSession;
    private RunState runState = RunState.ACTIVE;
    private float lostRunWaitMs = 0f;
    private float runElapsedMs = 0f;
    private int generationSchedulingFrame = 0;
    private boolean currentTerrainGeneratedThisFrame = false;
    private int gameplayScreenHeight;
    private boolean hudOwnsCurrentGesture;
    private boolean gameplayOwnsCurrentGesture;
    private long gameplayGestureStartNanos = Long.MAX_VALUE;

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        // --- Camera Setup ---
        this.camera = new Camera();
        gameplayScreenHeight = Math.max(1, screenHeight);
        resetFrameTimingWindow();
        initializeThemeCycle();

        this.camera.set(0f, 0f, 3f, // eye pos
                0f, 0f, 0f, // look at
                0f, 1f, 0f ); // which way is up
        camera.setProjectionAsScreen();

        PreparedGameplaySession session = preparedSession;
        if (session == null) {
            throw new IllegalStateException("Gameplay session was not prepared");
        }
        installPreparedSession(session);

        System.out.println("GAMEPLAY STAGE INIT");

        bloomPostProcessor = new BloomPostProcessor(screenWidth, screenHeight);
        gameHUD = GameHUD.makeGameHUD(context, screenWidth, screenHeight);
        if (gameHUD != null) {
            gameHUD.setThemeColor(colorTheme);
        }

    }

    private void installPreparedSession(PreparedGameplaySession session) {
        if (session == null) {
            throw new IllegalArgumentException("session == null");
        }
        if (terrainPresentation != null) {
            terrainPresentation.cleanupGPUResourcesRecursively();
        }
        if (terrainOutput != null) {
            terrainOutput.close();
        }

        player = session.acquirePlayer();
        terrainGenerator = session.getTerrainGenerator();
        terrainOutput = session.acquireTerrainOutput();
        lightSource = session.getLightSource();
        addonLightSource = new LightSource(
                lightSource != null && lightSource.color != null
                        ? lightSource.color
                        : CLR(1f, 1f, 1f, 1f)
        );
        nextRandomLevelIndex = session.getNextRandomLevelIndex();
        preparedSession = null;
        nextPreparedSession = null;
        runState = RunState.ACTIVE;
        lostRunWaitMs = 0f;
        runElapsedMs = 0f;
        renderOrigin = Vec3.ZERO;
        deferredSimulationElapsedNanos = 0L;
        lastSimulationOverrunLogNanos = Long.MIN_VALUE;
        lastInvariantFailureLogNanos = Long.MIN_VALUE;
        suppressedInvariantFailureLogs = 0;
        hudSimulationPaused = false;
        lifecycleSimulationPaused = false;
        hudOwnsCurrentGesture = false;
        gameplayOwnsCurrentGesture = false;
        gameplayGestureStartNanos = Long.MAX_VALUE;
        long simulationEpochNanos = AndroidGameClock.nowNanos();
        simulationWallCursorNanos = simulationEpochNanos;
        TerrainSnapshot initialTerrain = terrainOutput.snapshot();
        TerrainCollisionIndex preparedCollision =
                session.acquirePreparedCollisionIndex(
                        initialTerrain.revision);
        if (preparedCollision == null) {
            throw new IllegalStateException(
                    "Gameplay session reached installation without its prepared collision index");
        }
        terrainPresentation =
                new TerrainPresentation(initialTerrain, terrainRenderers);
        Vec3 initialPosition =
                new Vec3(player.getX(), player.getY(), player.getZ());
        AndroidSimulationController.TickListener listener =
                createSimulationTickListener();
        simulationController = new AndroidSimulationController(
                preparedCollision,
                initialPosition,
                DEFAULT_INITIAL_AIR_JUMP_CHARGES,
                gameplayScreenHeight,
                simulationEpochNanos,
                listener);
        player.enableAuthoritativeSimulation();
        applyCurrentPhaseSpeed();
        applyAuthoritativePlayerSnapshot();
    }

    private AndroidSimulationController.TickListener
    createSimulationTickListener() {
        return new AndroidSimulationController.TickListener() {
            @Override
            public void onPhysicsTick(StepResult result) {
                onAuthoritativePhysicsTick(result);
            }

            @Override
            public void onSimulationOverrun(long retainedNanos) {
                long nowNanos = AndroidGameClock.nowNanos();
                if (shouldLogSimulationOverrun(
                        lastSimulationOverrunLogNanos, nowNanos)) {
                    lastSimulationOverrunLogNanos = nowNanos;
                    Log.w("Physics", "120 Hz backlog retained: "
                            + retainedNanos + " ns");
                }
            }
        };
    }

    @Override
    public void updateThenDraw(float dt) {
        updateThenDraw(dt, AndroidGameClock.nowNanos(),
                Math.max(0L, (long) (dt * 1_000_000.0)));
    }

    @Override
    public boolean requiresDefaultFramebufferClear() {
        return bloomPostProcessor == null
                || bloomPostProcessor.getSceneTarget() == null;
    }

    @Override
    public void updateThenDraw(
            float dt, long frameTimeNanos, long rawElapsedNanos) {
        boolean tracing = isAppTracingEnabled();
        long frameCpuStartNanos = System.nanoTime();
        long terrainNanos = 0L;
        long collisionNanos = 0L;
        long simulationNanos = 0L;
        long renderNanos = 0L;
        long hudNanos = 0L;
        long preparationNanos = 0L;
        boolean collisionRebuilt = false;
        currentTerrainGeneratedThisFrame = false;
        currentSimulationTicks = 0;
        currentSimulationOverrun = false;
        currentRetainedSimulationNanos = 0L;
        currentTerrainCommitCount = 0;
        currentRenderOriginChanged = false;

        processTouchEvents();
        accountSimulationWallTime(frameTimeNanos);

        if (isSimulationPaused()) {
            long phaseStartNanos = System.nanoTime();
            beginTraceSection(tracing, "G3D:render");
            try {
                prepareScenePresentation(0f);
                drawCurrentFrameWithoutSimulation(tracing);
            } finally {
                endTraceSection(tracing);
            }
            renderNanos = System.nanoTime() - phaseStartNanos;
            publishSlowFrameContext();
            finishFrameTiming(
                    dt, frameCpuStartNanos, terrainNanos, collisionNanos,
                    simulationNanos, renderNanos, hudNanos, preparationNanos,
                    collisionRebuilt);
            return;
        }

        float safeDt = Math.max(0f, dt);
        float turnVisualDtMillis =
                Math.max(0L, rawElapsedNanos) / 1_000_000f;

        generationSchedulingFrame++;
        boolean nextSessionReadyAtFrameStart =
                nextPreparedSession != null
                        && nextPreparedSession.isRuntimePreparedReady();
        boolean generateCurrentTerrainThisFrame =
                shouldGenerateCurrentTerrainThisFrame(
                        generationSchedulingFrame,
                        nextSessionReadyAtFrameStart
                );
        boolean generateNextSessionThisFrame =
                shouldGenerateNextSessionThisFrame(
                        generationSchedulingFrame,
                        nextSessionReadyAtFrameStart
                );

        if (runState == RunState.ACTIVE) {
            long phaseStartNanos = System.nanoTime();
            boolean committedLeadReady;
            beginTraceSection(tracing, "G3D:terrain");
            try {
                updateThemeCycle(safeDt);
                long streamingReferenceSegmentId =
                        resolveStreamingReferenceSegmentId();
                terrainGenerator.removeOldTerrainElements(
                        streamingReferenceSegmentId);
                committedLeadReady =
                        ensureCommittedTerrainLeadBeforeSimulation(
                                generateCurrentTerrainThisFrame,
                                streamingReferenceSegmentId);
            } finally {
                endTraceSection(tracing);
            }
            terrainNanos = System.nanoTime() - phaseStartNanos;

            // Catch-up generation publishes commits before simulation is allowed to resume.
            // Drain them even on a blocked frame so the visible ribbon cannot lag behind.
            phaseStartNanos = System.nanoTime();
            beginTraceSection(tracing, "G3D:collisionCommit");
            try {
                collisionRebuilt = refreshCollisionTerrainIfNeeded();
            } finally {
                endTraceSection(tracing);
            }
            collisionNanos = System.nanoTime() - phaseStartNanos;

            if (!committedLeadReady) {
                phaseStartNanos = System.nanoTime();
                beginTraceSection(tracing, "G3D:render");
                try {
                    prepareScenePresentation(turnVisualDtMillis);
                    drawCurrentFrameWithoutSimulation(tracing);
                } finally {
                    endTraceSection(tracing);
                }
                renderNanos = System.nanoTime() - phaseStartNanos;
                publishSlowFrameContext();
                finishFrameTiming(
                        dt, frameCpuStartNanos, terrainNanos, collisionNanos,
                        simulationNanos, renderNanos, hudNanos, preparationNanos,
                        collisionRebuilt);
                return;
            }

            phaseStartNanos = System.nanoTime();
            beginTraceSection(tracing, "G3D:simulation");
            try {
                recordSimulationAdvance(simulationController.advanceFrameNanos(
                        takeDeferredSimulationElapsedNanos()));
                synchronizeRenderOrigin();
                applyAuthoritativePlayerSnapshot();
                terrainPresentation.updateBeforeDraw(
                        safeDt, simulationController.currentFrameSnapshot());
                if (player.isRunLost()) {
                    runState = RunState.LOST_WAITING_FOR_NEXT_SESSION;
                    lostRunWaitMs = 0f;
                }
            } finally {
                endTraceSection(tracing);
            }
            simulationNanos = System.nanoTime() - phaseStartNanos;
        } else {
            long phaseStartNanos = System.nanoTime();
            beginTraceSection(tracing, "G3D:simulation");
            try {
                lostRunWaitMs += safeDt;
                recordSimulationAdvance(simulationController.advanceFrameNanos(
                        takeDeferredSimulationElapsedNanos()));
                synchronizeRenderOrigin();
                applyAuthoritativePlayerSnapshot();
                terrainPresentation.updateBeforeDraw(
                        safeDt, simulationController.currentFrameSnapshot());
            } finally {
                endTraceSection(tracing);
            }
            simulationNanos = System.nanoTime() - phaseStartNanos;
        }

        long phaseStartNanos = System.nanoTime();
        beginTraceSection(tracing, "G3D:render");
        try {
            prepareScenePresentation(turnVisualDtMillis);
            renderCurrentFrame(tracing);

            if (player != null) {
                player.updateAfterDraw(safeDt);
            }
        } finally {
            endTraceSection(tracing);
        }
        renderNanos = System.nanoTime() - phaseStartNanos;

        phaseStartNanos = System.nanoTime();
        beginTraceSection(tracing, "G3D:hud");
        try {
            drawHud();
        } finally {
            endTraceSection(tracing);
        }
        hudNanos = System.nanoTime() - phaseStartNanos;

        phaseStartNanos = System.nanoTime();
        beginTraceSection(tracing, "G3D:nextSessionPrep");
        try {
            if (runState == RunState.ACTIVE) {
                if (generateNextSessionThisFrame
                        && !currentTerrainGeneratedThisFrame) {
                    advanceNextSessionPreparation(
                            NEXT_SESSION_ACTIVE_PREPARATION_CHUNK_BUDGET);
                }
            } else {
                advanceNextSessionPreparation(
                        NEXT_SESSION_LOST_PREPARATION_CHUNK_BUDGET);
            }
        } finally {
            endTraceSection(tracing);
        }
        preparationNanos = System.nanoTime() - phaseStartNanos;

        if (runState == RunState.LOST_WAITING_FOR_NEXT_SESSION) {
            if (tryShowLoadingForPreparedNextSession()) {
                publishSlowFrameContext();
                finishFrameTiming(
                        dt, frameCpuStartNanos, terrainNanos, collisionNanos,
                        simulationNanos, renderNanos, hudNanos, preparationNanos,
                        collisionRebuilt);
                return;
            }
            boolean nextSessionReady = nextPreparedSession != null
                    && nextPreparedSession.isRuntimePreparedReady();
            if (shouldFallbackToLoading(lostRunWaitMs, nextSessionReady)) {
                handOffPreparedNextSessionToLoadingStage();
                publishSlowFrameContext();
                finishFrameTiming(
                        dt, frameCpuStartNanos, terrainNanos, collisionNanos,
                        simulationNanos, renderNanos, hudNanos, preparationNanos,
                        collisionRebuilt);
                return;
            }
        }

        publishSlowFrameContext();
        finishFrameTiming(
                dt, frameCpuStartNanos, terrainNanos, collisionNanos,
                simulationNanos, renderNanos, hudNanos, preparationNanos,
                collisionRebuilt);
    }

    private void finishFrameTiming(
            float dt,
            long frameCpuStartNanos,
            long terrainNanos,
            long collisionNanos,
            long simulationNanos,
            long renderNanos,
            long hudNanos,
            long preparationNanos,
            boolean collisionRebuilt) {
        long cpuNanos = Math.max(0L, System.nanoTime() - frameCpuStartNanos);
        frameCounter++;
        if (dt >= MISSED_120_HZ_SLOT_THRESHOLD_MS) {
            timingMissedFrames++;
            if (hasPreviousFrameTiming) {
                timingMissPreviousMaxCpuNanos = Math.max(
                        timingMissPreviousMaxCpuNanos, previousFrameCpuNanos);
                timingMissPreviousMaxTerrainNanos = Math.max(
                        timingMissPreviousMaxTerrainNanos,
                        previousFrameTerrainNanos);
                timingMissPreviousMaxCollisionNanos = Math.max(
                        timingMissPreviousMaxCollisionNanos,
                        previousFrameCollisionNanos);
                timingMissPreviousMaxSimulationNanos = Math.max(
                        timingMissPreviousMaxSimulationNanos,
                        previousFrameSimulationNanos);
                timingMissPreviousMaxRenderNanos = Math.max(
                        timingMissPreviousMaxRenderNanos,
                        previousFrameRenderNanos);
                timingMissPreviousMaxHudNanos = Math.max(
                        timingMissPreviousMaxHudNanos,
                        previousFrameHudNanos);
                timingMissPreviousMaxSimulationTicks = Math.max(
                        timingMissPreviousMaxSimulationTicks,
                        previousFrameSimulationTicks);
                timingMissPreviousMaxTerrainCommits = Math.max(
                        timingMissPreviousMaxTerrainCommits,
                        previousFrameTerrainCommitCount);
                if (previousFrameCpuNanos > PhysicsConfig.FIXED_DT_NANOS) {
                    timingMissesAfterStageCpuOverBudget++;
                } else {
                    timingMissesAfterStageCpuUnderBudget++;
                }
            }
        }
        if (collisionRebuilt) {
            timingCollisionRebuilds++;
        }
        if (currentTerrainGeneratedThisFrame) {
            timingTerrainGenerationFrames++;
        }
        timingMaxDtMs = Math.max(timingMaxDtMs, dt);
        timingMaxCpuNanos = Math.max(timingMaxCpuNanos, cpuNanos);
        timingMaxTerrainNanos = Math.max(timingMaxTerrainNanos, terrainNanos);
        timingMaxCollisionNanos = Math.max(timingMaxCollisionNanos, collisionNanos);
        timingMaxSimulationNanos = Math.max(timingMaxSimulationNanos, simulationNanos);
        timingMaxRenderNanos = Math.max(timingMaxRenderNanos, renderNanos);
        timingMaxHudNanos = Math.max(timingMaxHudNanos, hudNanos);
        timingMaxPreparationNanos = Math.max(
                timingMaxPreparationNanos, preparationNanos);
        timingSimulationTicks += currentSimulationTicks;
        timingMaxSimulationTicks = Math.max(
                timingMaxSimulationTicks, currentSimulationTicks);
        if (currentSimulationTicks > 1) {
            timingCatchUpFrames++;
        }
        if (currentSimulationOverrun) {
            timingSimulationOverruns++;
        }
        timingMaxRetainedSimulationNanos = Math.max(
                timingMaxRetainedSimulationNanos,
                currentRetainedSimulationNanos);
        timingTerrainCommits += currentTerrainCommitCount;
        if (currentRenderOriginChanged) {
            timingRenderOriginChanges++;
        }

        if (frameCounter < FRAME_TIMING_WINDOW_SIZE) {
            rememberCurrentFrameTiming(
                    cpuNanos, terrainNanos, collisionNanos,
                    simulationNanos, renderNanos, hudNanos);
            return;
        }

        // This deliberately stays tiny: a long concatenated diagnostic line can itself make
        // the GL thread miss the next 120 Hz slot. Detailed attribution is opt-in through
        // `adb shell setprop log.tag.GameFrameDetail DEBUG`.
        if (Log.isLoggable(FRAME_TIMING_LOG_TAG, Log.DEBUG)) {
            Log.d(FRAME_TIMING_LOG_TAG, "dt=" + dt);
        }
        if (Log.isLoggable(FRAME_DETAIL_LOG_TAG, Log.DEBUG)) {
            logDetailedFrameTiming(dt);
        }
        resetFrameTimingWindow();
        rememberCurrentFrameTiming(
                cpuNanos, terrainNanos, collisionNanos,
                simulationNanos, renderNanos, hudNanos);
    }

    private void logDetailedFrameTiming(float dt) {
        int potionDraws = terrainPresentation != null
                ? terrainPresentation.potionBatchDrawCalls() : 0;
        int potionInstances = terrainPresentation != null
                ? terrainPresentation.potionBatchInstanceCount() : 0;
        int spikeDraws = terrainPresentation != null
                ? terrainPresentation.spikeBatchDrawCalls() : 0;
        int spikeInstances = terrainPresentation != null
                ? terrainPresentation.spikeBatchInstanceCount() : 0;
        int visibleTiles = terrainPresentation != null
                ? terrainPresentation.visibleSegmentCount() : 0;
        int visibleAddons = terrainPresentation != null
                ? terrainPresentation.visibleAddonCount() : 0;
        Log.d(FRAME_DETAIL_LOG_TAG, "dt=" + dt
                + " visible=" + visibleTiles + "," + visibleAddons
                + " potionBatch=" + potionDraws + "/" + potionInstances
                + " spikeBatch=" + spikeDraws + "/" + spikeInstances
                + " runState=" + runState
                + " window=" + FRAME_TIMING_WINDOW_SIZE
                + " missed=" + timingMissedFrames
                + " maxDt=" + timingMaxDtMs
                + " stageWallMax=" + nanosToMillis(timingMaxCpuNanos)
                + " terrainMax=" + nanosToMillis(timingMaxTerrainNanos)
                + " collisionMax=" + nanosToMillis(timingMaxCollisionNanos)
                + " simulationMax=" + nanosToMillis(timingMaxSimulationNanos)
                + " renderMax=" + nanosToMillis(timingMaxRenderNanos)
                + " hudMax=" + nanosToMillis(timingMaxHudNanos)
                + " prepMax=" + nanosToMillis(timingMaxPreparationNanos)
                + " collisionRebuilds=" + timingCollisionRebuilds
                + " terrainFrames=" + timingTerrainGenerationFrames
                + " commits=" + timingTerrainCommits
                + " originChanges=" + timingRenderOriginChanges
                + " ticks=" + timingSimulationTicks
                + " ticksMax=" + timingMaxSimulationTicks
                + " catchUpFrames=" + timingCatchUpFrames
                + " overruns=" + timingSimulationOverruns
                + " retainedMax="
                + nanosToMillis(timingMaxRetainedSimulationNanos)
                + " missPrevStageWallMax="
                + nanosToMillis(timingMissPreviousMaxCpuNanos)
                + " missPrevTerrainMax="
                + nanosToMillis(timingMissPreviousMaxTerrainNanos)
                + " missPrevCollisionMax="
                + nanosToMillis(timingMissPreviousMaxCollisionNanos)
                + " missPrevSimulationMax="
                + nanosToMillis(timingMissPreviousMaxSimulationNanos)
                + " missPrevRenderMax="
                + nanosToMillis(timingMissPreviousMaxRenderNanos)
                + " missPrevHudMax="
                + nanosToMillis(timingMissPreviousMaxHudNanos)
                + " missPrevTicksMax="
                + timingMissPreviousMaxSimulationTicks
                + " missPrevCommitsMax="
                + timingMissPreviousMaxTerrainCommits
                + " missAfterStageWallOver="
                + timingMissesAfterStageCpuOverBudget
                + " missAfterStageWallUnder="
                + timingMissesAfterStageCpuUnderBudget);
    }

    private void recordSimulationAdvance(
            FixedStepAccumulator.AdvanceResult result) {
        if (result == null) {
            return;
        }
        currentSimulationTicks += result.executedTicks;
        currentSimulationOverrun |= result.overrun;
        currentRetainedSimulationNanos = Math.max(
                currentRetainedSimulationNanos, result.retainedNanos);
    }

    private void rememberCurrentFrameTiming(
            long cpuNanos,
            long terrainNanos,
            long collisionNanos,
            long simulationNanos,
            long renderNanos,
            long hudNanos) {
        hasPreviousFrameTiming = true;
        previousFrameCpuNanos = cpuNanos;
        previousFrameTerrainNanos = terrainNanos;
        previousFrameCollisionNanos = collisionNanos;
        previousFrameSimulationNanos = simulationNanos;
        previousFrameRenderNanos = renderNanos;
        previousFrameHudNanos = hudNanos;
        previousFrameSimulationTicks = currentSimulationTicks;
        previousFrameTerrainCommitCount = currentTerrainCommitCount;
    }

    private void resetFrameTimingWindow() {
        frameCounter = 0;
        timingMissedFrames = 0;
        timingCollisionRebuilds = 0;
        timingTerrainGenerationFrames = 0;
        timingMaxDtMs = 0f;
        timingMaxCpuNanos = 0L;
        timingMaxTerrainNanos = 0L;
        timingMaxCollisionNanos = 0L;
        timingMaxSimulationNanos = 0L;
        timingMaxRenderNanos = 0L;
        timingMaxHudNanos = 0L;
        timingMaxPreparationNanos = 0L;
        timingSimulationTicks = 0;
        timingMaxSimulationTicks = 0;
        timingCatchUpFrames = 0;
        timingSimulationOverruns = 0;
        timingMaxRetainedSimulationNanos = 0L;
        timingTerrainCommits = 0;
        timingRenderOriginChanges = 0;
        timingMissPreviousMaxCpuNanos = 0L;
        timingMissPreviousMaxTerrainNanos = 0L;
        timingMissPreviousMaxCollisionNanos = 0L;
        timingMissPreviousMaxSimulationNanos = 0L;
        timingMissPreviousMaxRenderNanos = 0L;
        timingMissPreviousMaxHudNanos = 0L;
        timingMissPreviousMaxSimulationTicks = 0;
        timingMissPreviousMaxTerrainCommits = 0;
        timingMissesAfterStageCpuOverBudget = 0;
        timingMissesAfterStageCpuUnderBudget = 0;
    }

    private static float nanosToMillis(long nanos) {
        return nanos / 1_000_000f;
    }

    private static boolean isAppTracingEnabled() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && Trace.isEnabled();
    }

    private static void beginTraceSection(
            boolean tracing, String sectionName) {
        if (tracing) {
            Trace.beginSection(sectionName);
        }
    }

    private static void endTraceSection(boolean tracing) {
        if (tracing) {
            Trace.endSection();
        }
    }

    private void updateCameraAndLighting() {
        if (player == null || camera == null || lightSource == null || addonLightSource == null) {
            return;
        }
        player.setThemeColor(colorTheme);
        Vector3D camPos = V3(player.getX(), player.getY() + 0.75f, player.getZ())
                 .sub(player.getDir().withLen(3.8f));
        camera.updateEyePos(camPos);
        camera.updateLookPos(camPos.add(player.getDir().setY(0.0f)));
        Vector3D playerPos = V3(player.getX(), player.getY(), player.getZ());
        Vector3D terrainLightBackOffset = player.getDir()
                .setY(0f)
                .withLen(TERRAIN_LIGHT_BEHIND_PLAYER_DISTANCE);
        lightSource.position = playerPos
                .sub(terrainLightBackOffset)
                .add(V3(0f, TERRAIN_LIGHT_ABOVE_PLAYER_HEIGHT, 0f));
        addonLightSource.color = lightSource.color;
        Vector3D addonLightBackOffset = player.getDir()
                .setY(0f)
                .withLen(ADDON_LIGHT_BEHIND_PLAYER_DISTANCE);
        addonLightSource.position = playerPos
                .sub(addonLightBackOffset)
                .add(V3(0f, ADDON_LIGHT_ABOVE_PLAYER_HEIGHT, 0f));
        PortalLightingEnvironment.update(addonLightSource, camPos, colorTheme);
    }

    /**
     * Camera and lighting intentionally consume only authoritative player state. The model-only
     * steering accent is composed afterwards, immediately before scene rendering.
     */
    private void prepareScenePresentation(float turnVisualDtMillis) {
        updateCameraAndLighting();
        if (player != null) {
            player.updateTurnVisualAfterCamera(turnVisualDtMillis);
        }
    }

    private void resetPlayerTurnVisual() {
        gameplayOwnsCurrentGesture = false;
        gameplayGestureStartNanos = Long.MAX_VALUE;
        if (player != null) {
            player.resetTurnVisual();
        }
    }

    private void endGameplayTurnVisualHoldIfCurrent(
            long eventTimeNanos) {
        if (gameplayOwnsCurrentGesture
                && eventTimeNanos >= gameplayGestureStartNanos) {
            endGameplayTurnVisualHold();
        }
    }

    private void resetGameplayTurnVisualIfCurrent(
            long eventTimeNanos) {
        if (gameplayOwnsCurrentGesture
                && eventTimeNanos >= gameplayGestureStartNanos) {
            resetPlayerTurnVisual();
        }
    }

    private void endGameplayTurnVisualHold() {
        gameplayOwnsCurrentGesture = false;
        gameplayGestureStartNanos = Long.MAX_VALUE;
        if (player != null) {
            player.endTurnVisualHold();
        }
    }

    private void applyHudAction(
            GameHUD.HudAction hudAction,
            long timeNanos) {
        if (hudAction == GameHUD.HudAction.TOGGLE_PAUSE) {
            resetPlayerTurnVisual();
            setHudSimulationPaused(gameHUD.isPaused(), timeNanos);
        } else if (hudAction == GameHUD.HudAction.OPEN_SETTINGS) {
            resetPlayerTurnVisual();
            gameHUD.setPaused(true);
            setHudSimulationPaused(true, timeNanos);
            stageManager.toSettings();
        }
    }

    private void renderCurrentFrame(boolean tracing) {
        if (camera == null) {
            return;
        }
        float[] vpMatrix = camera.getViewProjectionMatrix();
        if (bloomPostProcessor != null && bloomPostProcessor.getSceneTarget() != null) {
            mainRenderCtx.vp = vpMatrix;
            mainRenderCtx.target = bloomPostProcessor.getSceneTarget();
            mainRenderCtx.viewportW = mainRenderCtx.target.getWidth();
            mainRenderCtx.viewportH = mainRenderCtx.target.getHeight();
            mainRenderCtx.flags = 0;
            mainRenderCtx.clear = true;
            beginTraceSection(tracing, "G3D:sceneDraw");
            try {
                sceneRenderer.render(mainRenderCtx);
            } finally {
                endTraceSection(tracing);
            }
            beginTraceSection(tracing, "G3D:bloom");
            try {
                bloomPostProcessor.compositeToScreen();
            } finally {
                endTraceSection(tracing);
            }
        } else {
            mainRenderCtx.vp = vpMatrix;
            mainRenderCtx.target = null;
            mainRenderCtx.viewportW = com.example.game3d_opengl.rendering.ScreenInfo.getScreenW();
            mainRenderCtx.viewportH = com.example.game3d_opengl.rendering.ScreenInfo.getScreenH();
            mainRenderCtx.flags = 0;
            mainRenderCtx.clear = false; // main framebuffer is already cleared by renderer
            beginTraceSection(tracing, "G3D:sceneDraw");
            try {
                sceneRenderer.render(mainRenderCtx);
            } finally {
                endTraceSection(tracing);
            }
        }
    }

    private void drawHud() {
        if (gameHUD != null && simulationController != null) {
            gameHUD.setThemeColor(colorTheme);
            updateHudPhasePreview();
            gameHUD.collectInfo(
                    simulationController.currentFrameSnapshot(),
                    presentationPhysicsConfig);
            gameHUD.draw();
        }
    }

    private void drawCurrentFrameWithoutSimulation(boolean tracing) {
        if (camera == null) {
            return;
        }
        renderCurrentFrame(tracing);
        drawHud();
    }

    /**
     * Applies only complete procedural terrain transactions between fixed ticks.
     */
    private boolean refreshCollisionTerrainIfNeeded() {
        if (terrainOutput == null || simulationController == null) {
            return false;
        }
        List<TerrainCommit> commits = terrainOutput.drainPendingCommits();
        if (commits.isEmpty()) {
            return false;
        }
        currentTerrainCommitCount = commits.size();
        for (TerrainCommit commit : commits) {
            simulationController.applyTerrainCommit(commit);
            if (simulationController.terrainRevision() != commit.revision) {
                throw new IllegalStateException(
                        "Simulation terrain revision did not acknowledge commit "
                                + commit.revision);
            }
        }
        terrainPresentation.applyTerrainCommits(commits);
        long finalRevision = commits.get(commits.size() - 1).revision;
        if (terrainPresentation.terrainRevision() != finalRevision) {
            throw new IllegalStateException(
                    "Presentation terrain revision did not acknowledge commit "
                            + finalRevision);
        }
        return true;
    }

    /**
     * Handles non-visual simulation events. Feature visibility is read directly from the
     * immutable simulation frame, so no event is mirrored into legacy addon state.
     */
    private void handleSimulationEvents(StepResult result) {
        if (result == null) {
            return;
        }
        for (SimulationEvent event : result.events) {
            if (event.type == SimulationEvent.Type.INVARIANT_FAILURE) {
                long nowNanos = AndroidGameClock.nowNanos();
                if (shouldLogInvariantFailure(lastInvariantFailureLogNanos, nowNanos)) {
                    String suppressedSuffix = suppressedInvariantFailureLogs > 0
                            ? " (suppressed " + suppressedInvariantFailureLogs + " repeats)"
                            : "";
                    Log.e("Physics", "Simulation invariant failure: "
                            + event.detail + suppressedSuffix);
                    lastInvariantFailureLogNanos = nowNanos;
                    suppressedInvariantFailureLogs = 0;
                } else {
                    suppressedInvariantFailureLogs++;
                }
            }
        }
    }

    private void onAuthoritativePhysicsTick(StepResult result) {
        handleSimulationEvents(result);
        if (result == null || result.snapshot == null) {
            return;
        }
        runElapsedMs = (float) (result.snapshot.timeNanos / 1_000_000.0);
        if (result.snapshot.dead) {
            return;
        }
        int completedPhases = phaseTimeline.advance(
                (float) (PhysicsConfig.FIXED_DT_NANOS / 1_000_000.0));
        if (completedPhases > 0) {
            phaseTriggeredThisFrame = true;
            applyCurrentPhaseSpeed();
            startThemeTransition(randomConstrainedTheme());
        }
    }

    /**
     * Accounts wall time in the same uptime clock domain used by MotionEvent timestamps.
     * Paused spans are excluded; terrain-stall spans remain queued so physics can catch up.
     */
    private synchronized long accountSimulationWallTime(long wallTimeNanos) {
        long nonNegativeTime = Math.max(0L, wallTimeNanos);
        if (simulationWallCursorNanos < 0L) {
            simulationWallCursorNanos = nonNegativeTime;
            return nonNegativeTime;
        }
        long effectiveTime = Math.max(simulationWallCursorNanos, nonNegativeTime);
        if (!isSimulationPausedLocked()) {
            deferredSimulationElapsedNanos = saturatingAddNanos(
                    deferredSimulationElapsedNanos,
                    effectiveTime - simulationWallCursorNanos);
        }
        simulationWallCursorNanos = effectiveTime;
        return effectiveTime;
    }

    private synchronized void setHudSimulationPaused(
            boolean paused, long wallTimeNanos) {
        if (hudSimulationPaused == paused) {
            return;
        }
        boolean wasPaused = isSimulationPausedLocked();
        long effectiveTime = accountSimulationWallTime(wallTimeNanos);
        hudSimulationPaused = paused;
        notifyAggregatePauseTransition(wasPaused, effectiveTime);
    }

    private synchronized void setLifecycleSimulationPaused(
            boolean paused, long wallTimeNanos) {
        if (lifecycleSimulationPaused == paused) {
            return;
        }
        boolean wasPaused = isSimulationPausedLocked();
        long effectiveTime = accountSimulationWallTime(wallTimeNanos);
        lifecycleSimulationPaused = paused;
        notifyAggregatePauseTransition(wasPaused, effectiveTime);
    }

    private void notifyAggregatePauseTransition(
            boolean wasPaused, long effectiveTime) {
        boolean isPaused = isSimulationPausedLocked();
        if (simulationController == null || wasPaused == isPaused) {
            return;
        }
        if (isPaused) {
            simulationController.pauseAt(effectiveTime);
        } else {
            simulationController.resumeAt(effectiveTime);
        }
    }

    private synchronized boolean isSimulationPaused() {
        return isSimulationPausedLocked();
    }

    private synchronized long takeDeferredSimulationElapsedNanos() {
        if (isSimulationPausedLocked()) {
            return 0L;
        }
        long result = deferredSimulationElapsedNanos;
        deferredSimulationElapsedNanos = 0L;
        return result;
    }

    private boolean isSimulationPausedLocked() {
        return hudSimulationPaused || lifecycleSimulationPaused;
    }

    /**
     * game-core owns the canonical double-precision position and chooses render origins.
     * Presentation subtracts that origin while the simulation keeps absolute coordinates.
     */
    private void synchronizeRenderOrigin() {
        if (simulationController == null || terrainPresentation == null) {
            return;
        }
        PlayerSnapshot current = simulationController.currentSnapshot();
        if (current == null) {
            return;
        }
        Vec3 nextOrigin = current.renderOrigin;
        if (renderOrigin.equals(nextOrigin)) {
            return;
        }
        renderOrigin = nextOrigin;
        currentRenderOriginChanged = true;
        if (terrainPresentation != null) {
            terrainPresentation.setRenderOrigin(renderOrigin);
        }
    }

    private void applyAuthoritativePlayerSnapshot() {
        if (player == null || simulationController == null) {
            return;
        }
        SimulationFrameSnapshot currentFrame =
                simulationController.currentFrameSnapshot();
        PlayerSnapshot previous = simulationController.previousSnapshot();
        if (currentFrame == null || currentFrame.player == null
                || previous == null) {
            return;
        }
        player.applySimulationFrame(
                previous,
                currentFrame,
                simulationController.renderAlpha(),
                renderOrigin);
    }

    private void enqueueGameplayLevels(int count) {
        for (int i = 0; i < count; ++i) {
            terrainGenerator.enqueueGameplayLevel(nextRandomLevelIndex++);
        }
    }

    private void fillPlannedGameplayTerrain() {
        int guard = 0;
        while (terrainGenerator.getPlannedSegmentCount()
                < REPLENISH_TERRAIN_THRESHOLD) {
            int before = terrainGenerator.getPlannedSegmentCount();
            enqueueGameplayLevels(REPLENISH_LEVEL_COUNT);
            int after = terrainGenerator.getPlannedSegmentCount();
            if (after <= before) {
                throw new IllegalStateException(
                        "A gameplay level did not add planned terrain");
            }
            if (++guard > REPLENISH_TERRAIN_THRESHOLD) {
                throw new IllegalStateException(
                        "Gameplay catalog could not fill the terrain plan");
            }
        }
    }

    private void advanceNextSessionPreparation(int chunkBudget) {
        if (chunkBudget <= 0) {
            return;
        }
        if (nextPreparedSession != null
                && nextPreparedSession.isRuntimePreparedReady()) {
            return;
        }
        if (nextPreparedSession == null) {
            nextPreparedSession = PreparedGameplaySession.createInitialSession(
                    terrainGenerator.catalog());
        }
        if (!nextPreparedSession.isSpawnPlayableReady()) {
            SlowFrameStats.markTerrainGenerating();
            nextPreparedSession.generateTerrainChunks(chunkBudget);
        }
        if (nextPreparedSession.isSpawnPlayableReady()) {
            nextPreparedSession.beginRuntimePreparationAsync();
        }
    }

    private boolean tryShowLoadingForPreparedNextSession() {
        if (nextPreparedSession == null
                || !nextPreparedSession.isRuntimePreparedReady()) {
            return false;
        }
        PreparedGameplaySession session = nextPreparedSession;
        nextPreparedSession = null;
        showRestartLoadingStage(session);
        return true;
    }

    private void handOffPreparedNextSessionToLoadingStage() {
        if (nextPreparedSession == null) {
            nextPreparedSession = PreparedGameplaySession.createInitialSession(
                    terrainGenerator.catalog());
        }
        PreparedGameplaySession session = nextPreparedSession;
        nextPreparedSession = null;
        session.beginRuntimePreparationAsync();
        showRestartLoadingStage(session);
    }

    private void showRestartLoadingStage(
            PreparedGameplaySession session) {
        stageManager.push(new LoadingStage(
                stageManager,
                session,
                new LoadingStage.ReadyAction() {
                    @Override
                    public void onReady(
                            PreparedGameplaySession readySession) {
                        restartWithPreparedSession(readySession);
                    }
                }));
    }

    private void restartWithPreparedSession(
            PreparedGameplaySession session) {
        clearPendingTouchEvents();
        resetFrameTimingWindow();
        hasPreviousFrameTiming = false;
        initializeThemeCycle();
        installPreparedSession(session);
        generationSchedulingFrame = 0;
        currentTerrainGeneratedThisFrame = false;
        if (gameHUD != null) {
            gameHUD.setPaused(false);
            gameHUD.setThemeColor(colorTheme);
        }
    }

    static boolean shouldFallbackToLoading(float lostRunWaitMs, boolean nextSessionReady) {
        return !nextSessionReady && lostRunWaitMs >= LOST_RUN_MAX_HIDDEN_WAIT_MS;
    }

    static boolean shouldEnqueueGameplayLevels(int tileCount, boolean hasPendingGenerationWork) {
        return tileCount < REPLENISH_TERRAIN_THRESHOLD && !hasPendingGenerationWork;
    }

    static boolean shouldGenerateCurrentTerrainThisFrame(int frameNumber, boolean nextSessionReady) {
        return nextSessionReady || (frameNumber & 1) == 1;
    }

    static boolean shouldGenerateNextSessionThisFrame(int frameNumber, boolean nextSessionReady) {
        return !nextSessionReady && (frameNumber & 1) == 0;
    }

    static boolean shouldLogSimulationOverrun(
            long lastLogNanos, long nowNanos) {
        return lastLogNanos == Long.MIN_VALUE
                || nowNanos < lastLogNanos
                || nowNanos - lastLogNanos
                >= SIMULATION_OVERRUN_LOG_INTERVAL_NANOS;
    }

    static boolean shouldLogInvariantFailure(
            long lastLogNanos, long nowNanos) {
        return lastLogNanos == Long.MIN_VALUE
                || nowNanos < lastLogNanos
                || nowNanos - lastLogNanos
                >= INVARIANT_FAILURE_LOG_INTERVAL_NANOS;
    }

    private long resolveStreamingReferenceSegmentId() {
        long lastSupportedSegmentId = player != null
                ? player.getNearestTileId()
                : -1L;
        if (terrainGenerator == null) {
            return lastSupportedSegmentId;
        }
        Vec3 absolutePosition = null;
        if (simulationController != null
                && simulationController.currentSnapshot() != null) {
            absolutePosition =
                    simulationController.currentSnapshot().absolutePosition;
        } else if (player != null) {
            absolutePosition = new Vec3(
                    player.getX(), player.getY(), player.getZ());
        }
        if (absolutePosition == null) {
            return lastSupportedSegmentId;
        }
        return terrainGenerator.resolveStreamingReferenceSegmentId(
                lastSupportedSegmentId,
                absolutePosition);
    }

    private boolean ensureCommittedTerrainLeadBeforeSimulation(
            boolean generateCurrentTerrainThisFrame,
            long referenceTileId) {
        if (terrainGenerator == null || player == null) {
            return true;
        }

        int interactionAheadTiles =
                terrainGenerator.getInteractionWindowAhead();
        int targetLeadTiles = computeTargetCommittedLeadTiles(
                Math.max(
                        player.getMoveSpeed() * 1000f,
                        player.getActiveHorizontalSpeedUnitsPerSecond()),
                (float) terrainGenerator.getSegmentLength(),
                interactionAheadTiles
        );
        int hardMinLeadTiles = computeHardMinimumCommittedLeadTiles(
                targetLeadTiles,
                interactionAheadTiles
        );
        int committedLeadTiles =
                terrainGenerator.getCommittedLeadAheadOf(referenceTileId);
        boolean mustCatchUpCurrentTerrain = committedLeadTiles < hardMinLeadTiles;
        boolean allowRoutineCurrentTerrainGeneration =
                generateCurrentTerrainThisFrame || mustCatchUpCurrentTerrain;

        if (allowRoutineCurrentTerrainGeneration
                && terrainGenerator.getPlannedSegmentCount()
                < REPLENISH_TERRAIN_THRESHOLD) {
            fillPlannedGameplayTerrain();
        }
        committedLeadTiles =
                terrainGenerator.getCommittedLeadAheadOf(referenceTileId);
        int desiredLeadTiles = generateCurrentTerrainThisFrame ? targetLeadTiles : hardMinLeadTiles;
        int generationBudget = 0;
        if (allowRoutineCurrentTerrainGeneration
                && terrainGenerator.getSegmentCount()
                < LOW_TERRAIN_THRESHOLD) {
            generationBudget = 1;
        }
        if (committedLeadTiles < desiredLeadTiles
                && terrainGenerator.hasPendingGenerationWork()) {
            generationBudget = Math.max(
                    generationBudget,
                    Math.min(
                            COMMITTED_FRONTIER_EXTRA_GENERATION_BUDGET,
                            desiredLeadTiles - committedLeadTiles));
        }
        if (generationBudget > 0
                && terrainGenerator.hasPendingGenerationWork()) {
            SlowFrameStats.markTerrainGenerating();
            currentTerrainGeneratedThisFrame = true;
            terrainGenerator.generate(
                    TERRAIN_AUTHORING_COMMAND_BUDGET, generationBudget);
            committedLeadTiles =
                    terrainGenerator.getCommittedLeadAheadOf(referenceTileId);
        }

        // A same-frame drain can empty the authoring queue. Refill immediately when lead is
        // still short so even frames / next-session prep cannot leave the frontier empty.
        if (committedLeadTiles < desiredLeadTiles
                && terrainGenerator.getPlannedSegmentCount()
                < REPLENISH_TERRAIN_THRESHOLD) {
            fillPlannedGameplayTerrain();
            int refillBudget = Math.min(
                    COMMITTED_FRONTIER_EXTRA_GENERATION_BUDGET,
                    desiredLeadTiles - committedLeadTiles);
            if (refillBudget > 0
                    && terrainGenerator.hasPendingGenerationWork()) {
                SlowFrameStats.markTerrainGenerating();
                currentTerrainGeneratedThisFrame = true;
                terrainGenerator.generate(
                        TERRAIN_AUTHORING_COMMAND_BUDGET, refillBudget);
                committedLeadTiles =
                        terrainGenerator.getCommittedLeadAheadOf(referenceTileId);
            }
        }

        return !shouldBlockPlayerSimulation(committedLeadTiles, hardMinLeadTiles);
    }

    static int computeTargetCommittedLeadTiles(
            float horizontalSpeedUnitsPerSecond,
            float segmentLength,
            int interactionAheadTiles
    ) {
        if (segmentLength <= 1e-5f) {
            return Math.max(1, interactionAheadTiles + COMMITTED_FRONTIER_MARGIN_TILES);
        }
        float lookaheadSeconds = COMMITTED_FRONTIER_LOOKAHEAD_MS / 1000f;
        int speedDrivenTiles = (int) Math.ceil(
                Math.max(0f, horizontalSpeedUnitsPerSecond)
                        * lookaheadSeconds
                        / segmentLength
        );
        return Math.max(
                interactionAheadTiles + COMMITTED_FRONTIER_MARGIN_TILES,
                speedDrivenTiles + COMMITTED_FRONTIER_MARGIN_TILES
        );
    }

    static int computeHardMinimumCommittedLeadTiles(int targetLeadTiles, int interactionAheadTiles) {
        return Math.max(
                interactionAheadTiles + COMMITTED_FRONTIER_HARD_MIN_MARGIN_TILES,
                targetLeadTiles - COMMITTED_FRONTIER_GRACE_TILES
        );
    }

    static boolean shouldBlockPlayerSimulation(int committedLeadTiles, int hardMinLeadTiles) {
        return committedLeadTiles < hardMinLeadTiles;
    }



    @Override
    protected void onDeactivated(DeactivationReason reason) {
        hudOwnsCurrentGesture = false;
        if (gameHUD != null) {
            gameHUD.cancelTouchGesture();
        }
        if (reason == DeactivationReason.COVERED) {
            resetPlayerTurnVisual();
            System.out.println("SWITCHING FROM GAMEPLAY");
        }
    }

    @Override
    protected void onActivated(ActivationReason reason) {
        if (reason == ActivationReason.REVEALED) {
            System.out.println("RETURNING TO GAMEPLAY");
        }
    }

    @Override
    public void onPause() {
        long pauseNanos = AndroidGameClock.nowNanos();
        hudOwnsCurrentGesture = false;
        gameplayOwnsCurrentGesture = false;
        gameplayGestureStartNanos = Long.MAX_VALUE;
        if (gameHUD != null) {
            gameHUD.cancelTouchGesture();
        }
        if (player != null) {
            player.requestTurnVisualReset();
        }
        setLifecycleSimulationPaused(true, pauseNanos);
    }

    @Override
    public void onResume() {
        setLifecycleSimulationPaused(false, AndroidGameClock.nowNanos());
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (player != null) {
            player.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (terrainPresentation != null) {
            terrainPresentation.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (nextPreparedSession != null) {
            nextPreparedSession.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (bloomPostProcessor != null) {
            bloomPostProcessor.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (gameHUD != null) {
            gameHUD.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (player != null) {
            player.cleanupGPUResourcesRecursively();
        }
        if (terrainPresentation != null) {
            terrainPresentation.cleanupGPUResourcesRecursively();
        }
        if (nextPreparedSession != null) {
            nextPreparedSession.cleanupGPUResourcesRecursively();
        }
        if (bloomPostProcessor != null) {
            bloomPostProcessor.cleanupGPUResourcesRecursively();
        }
        if (gameHUD != null) {
            gameHUD.cleanupGPUResourcesRecursively();
        }
    }

    @Override
    protected void releaseOwnedResourcesOnDiscard() {
        terrainGenerator = null;
        camera = null;
        resetFrameTimingWindow();
        colorTheme.rgba[0] = 0.33f;
        colorTheme.rgba[1] = 0.66f;
        colorTheme.rgba[2] = 0f;
        colorTheme.rgba[3] = 1f;
        themeR = 0.7f;
        themeG = 0f;
        themeB = 0f;
        targetThemeR = themeR;
        targetThemeG = themeG;
        targetThemeB = themeB;
        deltaThemeR = 0f;
        deltaThemeG = 0f;
        deltaThemeB = 0f;
        themeTransitionRemainingMs = 0f;
        themeTransitioning = false;
        phaseTimeline.reset();
        phaseTriggeredThisFrame = false;
        nextRandomLevelIndex = 0;
        player = null;
        simulationController = null;
        if (terrainOutput != null) {
            terrainOutput.close();
        }
        terrainOutput = null;
        terrainPresentation = null;
        renderOrigin = Vec3.ZERO;
        deferredSimulationElapsedNanos = 0L;
        simulationWallCursorNanos = -1L;
        hudSimulationPaused = false;
        lifecycleSimulationPaused = false;
        lightSource = null;
        addonLightSource = null;
        if (gameHUD != null) {
            gameHUD.cancelTouchGesture();
        }
        gameHUD = null;
        bloomPostProcessor = null;
        mainRenderCtx.vp = null;
        mainRenderCtx.target = null;
        mainRenderCtx.viewportW = 0;
        mainRenderCtx.viewportH = 0;
        mainRenderCtx.flags = 0;
        mainRenderCtx.clear = false;
        preparedSession = null;
        nextPreparedSession = null;
        runState = RunState.ACTIVE;
        lostRunWaitMs = 0f;
        runElapsedMs = 0f;
        generationSchedulingFrame = 0;
        currentTerrainGeneratedThisFrame = false;
        hudOwnsCurrentGesture = false;
        gameplayOwnsCurrentGesture = false;
        gameplayGestureStartNanos = Long.MAX_VALUE;
    }

    private void publishSlowFrameContext() {
        SlowFrameStats.markGameplayRunElapsed(runElapsedMs);
    }

    private void renderScene(RenderContext ctx) {
        if (ctx == null || ctx.vp == null) return;
        if (ctx.target != null) {
            ctx.target.bind();
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }
        GLES20.glViewport(0, 0, ctx.viewportW, ctx.viewportH);
        if (ctx.clear) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        }

        if (player != null) {
            player.draw(ctx.vp);
        }
        if (terrainPresentation != null && simulationController != null) {
            terrainPresentation.draw(
                    ctx.vp,
                    colorTheme,
                    lightSource,
                    simulationController.currentFrameSnapshot(),
                    camera.getEyeY());
        }
        if (ctx.target != null) {
            ctx.target.unbind();
        }
    }

    private void updateThemeCycle(float dt) {
        phaseTriggeredThisFrame = false;

        if (themeTransitioning) {
            float step = Math.min(Math.max(0f, dt), themeTransitionRemainingMs);
            themeR += deltaThemeR * step;
            themeG += deltaThemeG * step;
            themeB += deltaThemeB * step;
            themeTransitionRemainingMs -= step;
            updateColorThemeObject();

            if (themeTransitionRemainingMs <= 1e-5f) {
                themeR = targetThemeR;
                themeG = targetThemeG;
                themeB = targetThemeB;
                updateColorThemeObject();
                themeTransitionRemainingMs = 0f;
                themeTransitioning = false;
            }
        }
    }

    private void initializeThemeCycle() {
        FColor initialTheme = randomConstrainedTheme();
        themeR = clamp01(initialTheme.r());
        themeG = clamp01(initialTheme.g());
        themeB = clamp01(initialTheme.b());
        targetThemeR = themeR;
        targetThemeG = themeG;
        targetThemeB = themeB;
        deltaThemeR = 0f;
        deltaThemeG = 0f;
        deltaThemeB = 0f;
        themeTransitionRemainingMs = 0f;
        themeTransitioning = false;
        phaseTimeline.reset();
        phaseTriggeredThisFrame = false;
        updateColorThemeObject();
    }

    private void startThemeTransition(FColor targetTheme) {
        if (targetTheme == null) {
            return;
        }

        targetThemeR = clamp01(targetTheme.r());
        targetThemeG = clamp01(targetTheme.g());
        targetThemeB = clamp01(targetTheme.b());

        float totalDelta = Math.abs(targetThemeR - themeR)
                + Math.abs(targetThemeG - themeG)
                + Math.abs(targetThemeB - themeB);
        if (totalDelta <= 1e-6f) {
            themeTransitioning = false;
            themeTransitionRemainingMs = 0f;
            return;
        }

        float durationMs = Math.max(1f, THEME_TRANSITION_DURATION_MS);
        // Per-ms color deltas so all channels reach target at the same moment.
        deltaThemeR = (targetThemeR - themeR) / durationMs;
        deltaThemeG = (targetThemeG - themeG) / durationMs;
        deltaThemeB = (targetThemeB - themeB) / durationMs;
        themeTransitionRemainingMs = durationMs;
        themeTransitioning = true;
    }

    private void applyCurrentPhaseSpeed() {
        if (player == null) {
            return;
        }
        player.setMoveSpeed(PlayerConfig.speedForCompletedPhases(phaseTimeline.getCompletedPhaseCount()));
        if (simulationController != null) {
            simulationController.setCruisingSpeed(player.getMoveSpeed() * 1000.0);
        }
    }

    private void updateHudPhasePreview() {
        if (gameHUD == null) {
            return;
        }
        boolean includeImmediateBoundary = phaseTriggeredThisFrame;
        phaseTriggeredThisFrame = false;
        int count = phaseTimeline.fillUpcomingTransitionOffsets(
                hudUpcomingPhaseMilestonesMs,
                includeImmediateBoundary
        );
        gameHUD.setUpcomingPhasePreview(
                HUD_PHASE_PREVIEW_WINDOW_MS,
                hudUpcomingPhaseMilestonesMs,
                count
        );
    }

    private void updateColorThemeObject() {
        colorTheme.rgba[0] = clamp01(themeR);
        colorTheme.rgba[1] = clamp01(themeG);
        colorTheme.rgba[2] = clamp01(themeB);
        colorTheme.rgba[3] = 1f;
    }

    private static FColor randomConstrainedTheme() {
        for (int attempt = 0; attempt < 64; ++attempt) {
            float r = GameRandom.randFloat(THEME_MIN_CHANNEL, THEME_MAX_CHANNEL, 4);
            float gMin = Math.max(THEME_MIN_CHANNEL, THEME_RGB_SUM - r - THEME_MAX_CHANNEL);
            float gMax = Math.min(THEME_MAX_CHANNEL, THEME_RGB_SUM - r - THEME_MIN_CHANNEL);
            if (gMin > gMax) {
                continue;
            }
            float g = GameRandom.randFloat(gMin, gMax, 4);
            float b = THEME_RGB_SUM - r - g;
            if (b >= THEME_MIN_CHANNEL && b <= THEME_MAX_CHANNEL) {
                return makePastelTheme(
                        applyThemeBrightnessCoefficients(r, g, b));
            }
        }
        float c = clamp01(THEME_RGB_SUM / 3f);
        return makePastelTheme(CLR(c, c, c, 1f));
    }

    static FColor makePastelTheme(FColor color) {
        return CLR(
                lerp(color.r(), 1f, THEME_PASTEL_WHITE_MIX),
                lerp(color.g(), 1f, THEME_PASTEL_WHITE_MIX),
                lerp(color.b(), 1f, THEME_PASTEL_WHITE_MIX),
                1f
        );
    }

    static FColor applyThemeBrightnessCoefficients(float r, float g, float b) {
        float weightedBrightness = r * THEME_BRIGHTNESS_RED_COEFF
                + g * THEME_BRIGHTNESS_GREEN_COEFF
                + b * THEME_BRIGHTNESS_BLUE_COEFF;
        float fullCompensationScale = weightedBrightness > 1e-6f
                ? Math.min(1f, THEME_BRIGHTNESS_TARGET / weightedBrightness)
                : 1f;
        float blendedScale = lerp(1f, fullCompensationScale, THEME_BRIGHTNESS_COMPENSATION);
        return CLR(
                clamp01(r * blendedScale),
                clamp01(g * blendedScale),
                clamp01(b * blendedScale),
                1f
        );
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float lerp(float start, float end, float t) {
        float clampedT = clamp01(t);
        return start + (end - start) * clampedT;
    }

    private static long saturatingAddNanos(long left, long right) {
        long nonNegativeRight = Math.max(0L, right);
        return Long.MAX_VALUE - left < nonNegativeRight
                ? Long.MAX_VALUE
                : left + nonNegativeRight;
    }
}
