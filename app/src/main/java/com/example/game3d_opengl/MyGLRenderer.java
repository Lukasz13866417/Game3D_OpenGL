package com.example.game3d_opengl;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Debug;
import android.os.Trace;
import android.util.Log;

import java.util.ArrayDeque;

import com.example.game3d_opengl.game.player.player_character.PlayerAssets;
import com.example.game3d_opengl.game.settings.GameSettingsPersistence;
import com.example.game3d_opengl.game.settings.SlowFrameStats;
import com.example.game3d_opengl.game.settings.SlowFrameStatsSettings;
import com.example.game3d_opengl.game.util.AndroidGameClock;
import com.example.game3d_opengl.game.stage.stages.main.AssetLoadingStage;
import com.example.game3d_opengl.game.stage.stages.main.GameplayStage;
import com.example.game3d_opengl.game.stage.stages.main.PreparedGameplaySession;
import com.example.game3d_opengl.game.terrain.track_elements.GameplayElementBatchRenderers;
import com.example.game3d_opengl.game.terrain.track_elements.portal.CanonicalPortalVisual;
import com.example.game3d_opengl.game.stage.stages.main.LoadingStage;
import com.example.game3d_opengl.game.stage.stages.main.MenuStage;
import com.example.game3d_opengl.game.stage.stages.main.SettingsStage;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.stage.stages.test.AddonPlacementTestStage;
import com.example.game3d_opengl.game.stage.stages.test.TestGridRowsStage;
import com.example.game3d_opengl.game.stage.stages.test.TestGridRowsStageWithAddons;
import com.example.game3d_opengl.game.stage.stages.test.TestTerrainStage;
import com.example.game3d_opengl.game.stage.stages.test.TestTerrainStageSimulated;
import com.example.game3d_opengl.game.stage.stages.test.TestTileManagerStage;
import com.example.game3d_opengl.game.util.GameVersion;
import com.example.game3d_opengl.rendering.ScreenInfo;

public class MyGLRenderer implements GLSurfaceView.Renderer {
    private static final int REQUIRED_GL_MAJOR = 3;
    private static final int REQUIRED_GL_MINOR = 1;

    private static final long TARGET_FRAME_NS = 9_000_000L;
    private static final float MAX_SIMULATION_DT_MS = 33.333f;
    private static final String FRAME_PACING_LOG_TAG = "GameFramePacing";
    private static final int FRAME_PACING_WINDOW_SIZE = 128;
    private static final long TARGET_120_HZ_FRAME_NANOS = 8_333_333L;
    private static final long MISSED_120_HZ_SLOT_NANOS = 12_500_000L;
    static final long SLOW_FRAME_LOG_INTERVAL_NANOS = 1_000_000_000L;

    private final Context androidContext;
    private int surfaceW = 0, surfaceH = 0;
    private long lastFrameTime = -1;
    private long lastSlowFrameLogNanos = Long.MIN_VALUE;
    // Last UI vsync timestamp provided by Choreographer (set from UI thread)
    private volatile long lastVsyncNanos = -1;
    private volatile long lastVsyncSequence = 0L;
    private long lastConsumedVsyncSequence = -1L;
    private long lastDrawStartNanos = -1L;
    private long lastCallbackCpuNanos = 0L;
    private int pacingFrameCount;
    private int pacingMissedCallbacks;
    private int pacingSkipped120HzSlots;
    private int pacingCoalescedVsyncs;
    private int pacingMissesAfterCpuOverBudget;
    private int pacingMissesAfterCpuUnderBudget;
    private long pacingMaxRawVsyncNanos;
    private long pacingMaxCallbackWallNanos;
    private long pacingThreadCpuAtMaxCallbackWallNanos;
    private long pacingOffCpuAtMaxCallbackWallNanos;
    private long pacingMaxPreviousCpuOnMissNanos;
    private long pacingMaxDrawStartGapNanos;
    private long pacingMaxVsyncAgeNanos;
    private long pacingMaxSequenceGap;
    private final StageManager stageManager;
    // Stage transition requested by UI thread, applied next frame on GL thread
    private volatile PendingStageTransition pendingTransition = null;
    private final ArrayDeque<Stage> stageStack = new ArrayDeque<>();
    private final MenuStage menuStage;
    private final AssetLoadingStage assetLoadingStage;
    private final SettingsStage settingsStage;
    private volatile boolean useFrameCap = true;

    public void setUseFrameCap(boolean useFrameCap) {
        this.useFrameCap = useFrameCap;
    }

    // Called from UI thread's Choreographer callback to provide vsync time
    public void onVsync(long frameTimeNanos) {
        this.lastVsyncNanos = frameTimeNanos;
        this.lastVsyncSequence += 1L;
    }


    // Simple API that enables stages to order the renderer to switch stages
    public class StageManager {
        public void toMenu() {
            resetTo(menuStage);
        }

        public void startGameplay(PreparedGameplaySession session) {
            resetTo(new GameplayStage(this, session));
        }

        public void toLoadingThenGameplay() {
            resetTo(assetLoadingStage);
        }

        public void toVisualLoadingThenGameplay(PreparedGameplaySession session) {
            resetTo(new LoadingStage(this, session));
        }

        public void toSettings() {
            push(settingsStage);
        }

        public void push(Stage stage) {
            requestTransition(PendingStageTransition.Type.PUSH, stage);
        }

        public void replace(Stage stage) {
            requestTransition(PendingStageTransition.Type.REPLACE_TOP, stage);
        }

        public void resetTo(Stage stage) {
            requestTransition(PendingStageTransition.Type.RESET_STACK, stage);
        }

        public void pop() {
            requestTransition(PendingStageTransition.Type.POP, null);
        }

        private void requestTransition(PendingStageTransition.Type type, Stage stage) {
            pendingTransition = new PendingStageTransition(type, stage);
        }
    }

    public MyGLRenderer(Context androidContext) {
        this.androidContext = androidContext;
        GameVersion.initialize(androidContext);
        GameSettingsPersistence.initialize(androidContext);
        this.stageManager = new StageManager();
        this.menuStage = new MenuStage(stageManager);
        this.assetLoadingStage = new AssetLoadingStage(stageManager);
        this.settingsStage = new SettingsStage(stageManager);
        Stage initialStage = //new TestTerrainStageSimulated(stageManager);
                             //new TestTerrainStage(stageManager);
                             //new TestTileManagerStage(stageManager);
                             //new TestGridRowsStage(stageManager);
                             //new TestGridRowsStageWithAddons(stageManager);
                             //new AddonPlacementTestStage(stageManager);
                             //new GameplayStage(stageManager, PreparedGameplaySession.createInitialSession());
                             menuStage;
                             //new TestWireframeStage(stageManager);
                             //new IconTestStage(stageManager);
        stageStack.push(initialStage);
    }

    public Stage getCurrentStage() {
        return stageStack.peek();
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        validateEs31Context();
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        lastFrameTime = System.nanoTime();
        resetFramePacingWindow();
        lastConsumedVsyncSequence = -1L;
        lastDrawStartNanos = -1L;
        lastCallbackCpuNanos = 0L;
        PlayerAssets.markLoadedGPUResourcesDirty();
        GameplayElementBatchRenderers.markDefaultGpuResourcesDirty();
        CanonicalPortalVisual.markSharedGpuAssetsDirty();
        for (Stage stage : stageStack) {
            if (stage != null && stage.isInitialized()) {
                stage.reloadGPUResourcesRecursivelyOnContextLoss();
            }
        }
    }

    private void validateEs31Context() {
        int[] major = new int[1];
        int[] minor = new int[1];
        int[] sampleBuffers = new int[1];
        int[] samples = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_MAJOR_VERSION, major, 0);
        GLES30.glGetIntegerv(GLES30.GL_MINOR_VERSION, minor, 0);
        GLES20.glGetIntegerv(
                GLES20.GL_SAMPLE_BUFFERS, sampleBuffers, 0);
        GLES20.glGetIntegerv(GLES20.GL_SAMPLES, samples, 0);
        String version = GLES20.glGetString(GLES20.GL_VERSION);
        String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
        String vendor = GLES20.glGetString(GLES20.GL_VENDOR);
        Log.i("GL", "OpenGL context: version=" + version
                + " renderer=" + renderer
                + " vendor=" + vendor
                + " parsed=" + major[0] + "." + minor[0]
                + " defaultFramebufferMsaa="
                + sampleBuffers[0] + "x" + samples[0]);
        if (major[0] < REQUIRED_GL_MAJOR
                || (major[0] == REQUIRED_GL_MAJOR && minor[0] < REQUIRED_GL_MINOR)) {
            throw new IllegalStateException(
                    "OpenGL ES 3.1 or newer is required, but got "
                            + major[0] + "." + minor[0]
                            + " (" + version + ")"
            );
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        boolean tracing = isAppTracingEnabled();
        if (tracing) {
            Trace.beginSection("G3D:frameJava");
        }
        try {
            drawFrame(gl);
        } finally {
            if (tracing) {
                Trace.endSection();
            }
        }
    }

    private void drawFrame(GL10 gl) {
        long callbackStartNanos = System.nanoTime();
        long callbackThreadCpuStartNanos = Debug.threadCpuTimeNanos();
        long previousCallbackCpuNanos = lastCallbackCpuNanos;
        applyPendingTransition();
        if (lastFrameTime == -1) {
            lastFrameTime = System.nanoTime();
        }

        // Frame pacing and dt:
        // If Choreographer provides a vsync timestamp, prefer that for stable dt.
        // Otherwise, fall back to System.nanoTime() with optional sleep-based cap.
        long vsync = lastVsyncNanos;
        long vsyncSequence = lastVsyncSequence;
        long now = System.nanoTime();
        long referenceNow = (vsync > 0 ? vsync : now);

        long elapsed = (lastFrameTime > 0 ? (referenceNow - lastFrameTime) : 0);
        if (vsync <= 0) {
            // No vsync provided: optionally apply coarse sleep-based cap
            if (useFrameCap && elapsed < TARGET_FRAME_NS) {
                long sleepNs = TARGET_FRAME_NS - elapsed;
                long sleepMs = sleepNs / 1_000_000L;
                int extraNs = (int) (sleepNs % 1_000_000L);
                try {
                    if (sleepMs > 0 || extraNs > 0) {
                        Thread.sleep(sleepMs, extraNs);
                    }
                } catch (InterruptedException ignored) {}
                now = System.nanoTime();
                referenceNow = now;
                elapsed = (lastFrameTime > 0 ? (referenceNow - lastFrameTime) : 0);
            }
        }
        float deltaTime = (elapsed <= 0 ? 0.0f : (elapsed / 1_000_000f)); // ms
        lastFrameTime = referenceNow;
        float simulationDt = Math.min(deltaTime, MAX_SIMULATION_DT_MS);

        Stage currentStage = getCurrentStage();
        boolean slowFrameCaptureEnabled =
                SlowFrameStatsSettings.isCaptureEnabled();
        if (slowFrameCaptureEnabled
                && deltaTime > SlowFrameStats.SLOW_FRAME_THRESHOLD_MS
                && shouldLogSlowFrameWarning(
                        lastSlowFrameLogNanos, referenceNow)) {
            lastSlowFrameLogNanos = referenceNow;
            SlowFrameStats.FrameSnapshot previousFrame =
                    SlowFrameStats.getLastCompletedFrameSnapshot();
            Log.w(
                    "Perf",
                    "perf: SLOW FRAME " + (int) deltaTime + " ms"
                            + " | stage=" + previousFrame.getStageName()
                            + " | context="
                            + (previousFrame.isGameplay()
                            ? ("gameplay@" + (int) previousFrame.getRunElapsedMs() + "ms")
                            : "outside_gameplay")
                            + " | terrainGenerating=" + previousFrame.isTerrainGenerating()
            );
        }
        SlowFrameStats.maybeCaptureCompletedSlowFrame(deltaTime);
        SlowFrameStats.beginFrame(currentStage != null ? currentStage.getClass().getSimpleName() : "None");

        boolean stageWillDraw =
                currentStage != null && !currentStage.isPaused();
        if (!stageWillDraw || currentStage.requiresDefaultFramebufferClear()) {
            GLES20.glClear(
                    GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        }
        if (stageWillDraw) {
            currentStage.updateThenDraw(
                    simulationDt,
                    AndroidGameClock.nowNanos(),
                    Math.max(0L, elapsed));
        }
        SlowFrameStats.endFrame();
        finishFramePacing(
                callbackStartNanos,
                callbackThreadCpuStartNanos,
                previousCallbackCpuNanos,
                vsync,
                vsyncSequence,
                elapsed);

    }

    private void finishFramePacing(
            long callbackStartNanos,
            long callbackThreadCpuStartNanos,
            long previousCallbackCpuNanos,
            long vsyncNanos,
            long vsyncSequence,
            long rawVsyncElapsedNanos) {
        long callbackWallNanos =
                Math.max(0L, System.nanoTime() - callbackStartNanos);
        long callbackThreadCpuNanos = Math.max(
                0L,
                Debug.threadCpuTimeNanos() - callbackThreadCpuStartNanos);
        long callbackOffCpuNanos = Math.max(
                0L, callbackWallNanos - callbackThreadCpuNanos);
        long drawStartGapNanos = lastDrawStartNanos >= 0L
                ? Math.max(0L, callbackStartNanos - lastDrawStartNanos)
                : 0L;
        long vsyncAgeNanos = vsyncNanos > 0L
                ? Math.max(0L, callbackStartNanos - vsyncNanos)
                : 0L;
        long sequenceGap = lastConsumedVsyncSequence >= 0L
                ? Math.max(0L, vsyncSequence - lastConsumedVsyncSequence)
                : 0L;
        int coalescedVsyncs = sequenceGap > 1L
                ? (int) Math.min(Integer.MAX_VALUE, sequenceGap - 1L)
                : 0;
        int skipped120HzSlots = skipped120HzSlots(rawVsyncElapsedNanos);
        boolean missed = rawVsyncElapsedNanos >= MISSED_120_HZ_SLOT_NANOS;

        pacingFrameCount++;
        pacingSkipped120HzSlots += skipped120HzSlots;
        pacingCoalescedVsyncs += coalescedVsyncs;
        pacingMaxRawVsyncNanos =
                Math.max(pacingMaxRawVsyncNanos, rawVsyncElapsedNanos);
        if (callbackWallNanos > pacingMaxCallbackWallNanos) {
            pacingMaxCallbackWallNanos = callbackWallNanos;
            pacingThreadCpuAtMaxCallbackWallNanos =
                    callbackThreadCpuNanos;
            pacingOffCpuAtMaxCallbackWallNanos =
                    callbackOffCpuNanos;
        }
        pacingMaxDrawStartGapNanos =
                Math.max(pacingMaxDrawStartGapNanos, drawStartGapNanos);
        pacingMaxVsyncAgeNanos =
                Math.max(pacingMaxVsyncAgeNanos, vsyncAgeNanos);
        pacingMaxSequenceGap = Math.max(pacingMaxSequenceGap, sequenceGap);
        if (missed) {
            pacingMissedCallbacks++;
            pacingMaxPreviousCpuOnMissNanos = Math.max(
                    pacingMaxPreviousCpuOnMissNanos,
                    previousCallbackCpuNanos);
            if (previousCallbackCpuNanos > TARGET_120_HZ_FRAME_NANOS) {
                pacingMissesAfterCpuOverBudget++;
            } else {
                pacingMissesAfterCpuUnderBudget++;
            }
        }

        lastConsumedVsyncSequence = vsyncSequence;
        lastDrawStartNanos = callbackStartNanos;

        if (pacingFrameCount >= FRAME_PACING_WINDOW_SIZE) {
            if (Log.isLoggable(FRAME_PACING_LOG_TAG, Log.DEBUG)) {
                Log.d(FRAME_PACING_LOG_TAG,
                        "window=" + FRAME_PACING_WINDOW_SIZE
                                + " missedCallbacks=" + pacingMissedCallbacks
                                + " skipped120Slots=" + pacingSkipped120HzSlots
                                + " coalescedVsyncs=" + pacingCoalescedVsyncs
                                + " rawVsyncMax="
                                + nanosToMillis(pacingMaxRawVsyncNanos)
                                + " callbackWallMax="
                                + nanosToMillis(pacingMaxCallbackWallNanos)
                                + " callbackThreadCpuAtWallMax="
                                + nanosToMillis(
                                        pacingThreadCpuAtMaxCallbackWallNanos)
                                + " callbackOffCpuAtWallMax="
                                + nanosToMillis(
                                        pacingOffCpuAtMaxCallbackWallNanos)
                                + " missPrevCallbackWallMax="
                                + nanosToMillis(pacingMaxPreviousCpuOnMissNanos)
                                + " missAfterCallbackWallOver="
                                + pacingMissesAfterCpuOverBudget
                                + " missAfterCallbackWallUnder="
                                + pacingMissesAfterCpuUnderBudget
                                + " drawStartGapMax="
                                + nanosToMillis(pacingMaxDrawStartGapNanos)
                                + " vsyncAgeMax="
                                + nanosToMillis(pacingMaxVsyncAgeNanos)
                                + " sequenceGapMax=" + pacingMaxSequenceGap);
            }
            resetFramePacingWindow();
        }

        // Include the occasional summary-log cost in the value correlated with the next gap.
        lastCallbackCpuNanos =
                Math.max(0L, System.nanoTime() - callbackStartNanos);
    }

    static int skipped120HzSlots(long elapsedNanos) {
        if (elapsedNanos < MISSED_120_HZ_SLOT_NANOS) {
            return 0;
        }
        long roundedIntervals = Math.max(
                1L,
                Math.round(
                        (double) elapsedNanos
                                / (double) TARGET_120_HZ_FRAME_NANOS));
        return (int) Math.min(Integer.MAX_VALUE, roundedIntervals - 1L);
    }

    private void resetFramePacingWindow() {
        pacingFrameCount = 0;
        pacingMissedCallbacks = 0;
        pacingSkipped120HzSlots = 0;
        pacingCoalescedVsyncs = 0;
        pacingMissesAfterCpuOverBudget = 0;
        pacingMissesAfterCpuUnderBudget = 0;
        pacingMaxRawVsyncNanos = 0L;
        pacingMaxCallbackWallNanos = 0L;
        pacingThreadCpuAtMaxCallbackWallNanos = 0L;
        pacingOffCpuAtMaxCallbackWallNanos = 0L;
        pacingMaxPreviousCpuOnMissNanos = 0L;
        pacingMaxDrawStartGapNanos = 0L;
        pacingMaxVsyncAgeNanos = 0L;
        pacingMaxSequenceGap = 0L;
    }

    private static float nanosToMillis(long nanos) {
        return nanos / 1_000_000f;
    }

    static boolean shouldLogSlowFrameWarning(
            long lastLogNanos, long nowNanos) {
        return lastLogNanos == Long.MIN_VALUE
                || nowNanos < lastLogNanos
                || nowNanos - lastLogNanos
                >= SLOW_FRAME_LOG_INTERVAL_NANOS;
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        surfaceW = width;
        surfaceH = height;
        ScreenInfo.setScreenSize(width, height);

        // Surface size changes are not application lifecycle transitions. In particular,
        // changing insets or orientation must not toggle a running stage into a paused state.
        // Activity.onPause/onResume owns lifecycle pause state.
        Stage currentStage = getCurrentStage();
        if (currentStage == null) {
            return;
        }
        if (!currentStage.isInitialized()) {
            currentStage.setInitialized();
            currentStage.init(androidContext, width, height);
            currentStage.activate(Stage.ActivationReason.FRESH_ENTER);
        }
    }

    private void applyPendingTransition() {
        PendingStageTransition transition = pendingTransition;
        if (transition == null) {
            return;
        }
        pendingTransition = null;

        boolean tracing = isAppTracingEnabled();
        if (tracing) {
            Trace.beginSection("G3D:stageTransition");
        }
        try {
            switch (transition.type) {
                case PUSH:
                    applyPush(transition.stage);
                    break;
                case REPLACE_TOP:
                    applyReplaceTop(transition.stage);
                    break;
                case RESET_STACK:
                    applyResetStack(transition.stage);
                    break;
                case POP:
                    applyPop();
                    break;
            }
        } finally {
            if (tracing) {
                Trace.endSection();
            }
        }
    }

    private static boolean isAppTracingEnabled() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && Trace.isEnabled();
    }

    private void applyPush(Stage nextStage) {
        if (nextStage == null) {
            return;
        }
        Stage currentStage = getCurrentStage();
        if (currentStage != null) {
            currentStage.deactivate(Stage.DeactivationReason.COVERED);
        }
        stageStack.push(nextStage);
        activateStage(nextStage);
    }

    private void applyReplaceTop(Stage nextStage) {
        if (!stageStack.isEmpty()) {
            stageStack.pop().discard();
        }
        if (nextStage == null) {
            return;
        }
        stageStack.push(nextStage);
        activateStage(nextStage);
    }

    private void applyResetStack(Stage nextStage) {
        while (!stageStack.isEmpty()) {
            stageStack.pop().discard();
        }
        if (nextStage == null) {
            return;
        }
        stageStack.push(nextStage);
        activateStage(nextStage);
    }

    private void applyPop() {
        if (stageStack.size() <= 1) {
            return;
        }
        stageStack.pop().discard();
        Stage currentStage = getCurrentStage();
        if (currentStage != null) {
            currentStage.activate(Stage.ActivationReason.REVEALED);
        }
    }

    private void activateStage(Stage stage) {
        if (stage == null) {
            return;
        }
        if (!stage.isInitialized()) {
            stage.setInitialized();
            stage.init(androidContext, surfaceW, surfaceH);
            stage.activate(Stage.ActivationReason.FRESH_ENTER);
        } else {
            stage.activate(Stage.ActivationReason.REVEALED);
        }
    }

    private static final class PendingStageTransition {
        enum Type {
            PUSH,
            REPLACE_TOP,
            RESET_STACK,
            POP
        }

        final Type type;
        final Stage stage;

        private PendingStageTransition(Type type, Stage stage) {
            this.type = type;
            this.stage = stage;
        }
    }
}
