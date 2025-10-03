package com.example.game3d_opengl;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.example.game3d_opengl.game.stage.stages.main.GameplayStage;
import com.example.game3d_opengl.game.stage.stages.main.MenuStage;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.stage.stages.test.IconTestStage;

public class MyGLRenderer implements GLSurfaceView.Renderer {

    private static final long TARGET_FRAME_NS = 9_000_000L;
    private static final float SLOW_FRAME_THRESHOLD_MS = 12.0f; // log if frame slower than this

    private final Context androidContext;
    private int surfaceW = 0, surfaceH = 0;
    private long lastFrameTime = -1;
    // Last UI vsync timestamp provided by Choreographer (set from UI thread)
    private volatile long lastVsyncNanos = -1;
    private final StageManager stageManager;
    // Stage requested by UI thread, applied next frame on GL thread
    private volatile Stage pendingStage = null;
    private Stage currStage;
    private final MenuStage menuStage;
    private final GameplayStage gameplayStage;
    private volatile boolean useFrameCap = true;

    public void setUseFrameCap(boolean useFrameCap) {
        this.useFrameCap = useFrameCap;
    }

    // Called from UI thread's Choreographer callback to provide vsync time
    public void onVsync(long frameTimeNanos) {
        this.lastVsyncNanos = frameTimeNanos;
    }


    // Simple API that enables stages to order the renderer to switch stages
    public class StageManager {
        public void toMenu() {
            switchTo(menuStage);
        }

        public void toGameplay() {
            switchTo(gameplayStage);
        }

        public void toSettings() {
            throw new IllegalStateException("Not implemented");
        }

        private void switchTo(Stage to) {
            // Request the change; actual switch occurs on GL thread
            pendingStage = to;
        }
    }

    public MyGLRenderer(Context androidContext) {
        this.androidContext = androidContext;
        this.stageManager = new StageManager();
        this.gameplayStage = new GameplayStage(stageManager);
        this.menuStage = new MenuStage(stageManager);
        this.currStage =
                          //new TestGridRowsStructuresStage(stageManager);
                          //new TestGridRowsStage(stageManager);
                          //new AddonPlacementTestStage(stageManager);
                          new GameplayStage(stageManager);
                          //new TestWireframeStage(stageManager);
                          //new IconTestStage(stageManager);
    }

    public Stage getCurrentStage() {
        return currStage;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        lastFrameTime = System.nanoTime();
        if(getCurrentStage().isInitialized()){
            getCurrentStage().reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Apply pending stage switch (GL thread)
        if (pendingStage != null) {
            if (currStage != null) {
                currStage.onSwitch();
            }
            currStage = pendingStage;
            pendingStage = null;
            if (!currStage.isInitialized()) {
                currStage.setInitialized();
                currStage.init(androidContext, surfaceW, surfaceH);
            } else {
                currStage.onReturn();
            }
        }
        if (lastFrameTime == -1) {
            lastFrameTime = System.nanoTime();
        }

        // Frame pacing and dt:
        // If Choreographer provides a vsync timestamp, prefer that for stable dt.
        // Otherwise, fall back to System.nanoTime() with optional sleep-based cap.
        long vsync = lastVsyncNanos;
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

        // Slow frame logging
        if (deltaTime > SLOW_FRAME_THRESHOLD_MS) {
            Log.w("Perf", "perf: SLOW FRAME " + (int) deltaTime + " ms" +
                   "   |    was terrain generating: "+ GameplayStage.__DEBUG_IS_TERRAIN_GENERATING);
        }
        GameplayStage.__DEBUG_IS_TERRAIN_GENERATING = false;

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (!currStage.isPaused()) {
            currStage.updateThenDraw(deltaTime);
        }

    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        surfaceW = width;
        surfaceH = height;

        // Initialise or re-enter current stage appropriately
        if(!getCurrentStage().isInitialized()){
            getCurrentStage().setInitialized();
            getCurrentStage().init(androidContext, width, height);
        }else if(getCurrentStage().isPaused()){
            getCurrentStage().resume();
        }else{
            getCurrentStage().pause();
        }
    }
}