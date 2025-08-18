package com.example.game3d_opengl;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.example.game3d_opengl.game.stages.AddonPlacementTestStage;
import com.example.game3d_opengl.game.stages.EmptySegmentTestStage;
import com.example.game3d_opengl.game.stages.GameplayStage;
import com.example.game3d_opengl.game.stages.MenuStage;
import com.example.game3d_opengl.game.stage_api.Stage;
import com.example.game3d_opengl.game.stages.TestGridRowsStage;

public class MyGLRenderer implements GLSurfaceView.Renderer {

    private final Context androidContext;
    private int surfaceW = 0, surfaceH = 0;
    private long lastFrameTime = -1;
    private final StageManager stageManager;
    // Stage requested by UI thread, applied next frame on GL thread
    private volatile Stage pendingStage = null;
    private Stage currStage;
    private MenuStage menuStage;
    private GameplayStage gameplayStage;


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
        this.currStage =  /*new TestGridRowsStage(stageManager);*/ /* new AddonPlacementTestStage(stageManager);*/ new GameplayStage(stageManager);
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
            getCurrentStage().resetGPUResources();
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
                currStage.initScene(androidContext, surfaceW, surfaceH);
            } else {
                currStage.onReturn();
            }
        }
        if (lastFrameTime == -1) {
            lastFrameTime = System.nanoTime();
        }
        long now = System.nanoTime();
        float deltaTime = (now - lastFrameTime) / 1000000f;
        lastFrameTime = now;
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
            getCurrentStage().initScene(androidContext, width, height);
        }else if(getCurrentStage().isPaused()){
            getCurrentStage().resume();
        }else{
            getCurrentStage().pause();
        }
    }
}