package com.example.game3d_opengl.game.stage.stage_api;


import android.content.Context;
import android.content.res.AssetManager;

import com.example.game3d_opengl.MyGLRenderer.StageManager;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.ScreenInfo;
import com.example.game3d_opengl.rendering.infill.FlatLitShaderPair;
import com.example.game3d_opengl.rendering.infill.InfillShaderPair;
import com.example.game3d_opengl.rendering.wireframe.WireframeShaderPair;


public abstract class Stage implements GPUResourceOwner {

    private static final int TOUCH_QUEUE_CAPACITY = 128;
    private final TouchEventQueue touchQueue = new TouchEventQueue(TOUCH_QUEUE_CAPACITY);

    public Stage(StageManager stageManager){
        this.stageManager = stageManager;
    }

    // ---- Called from UI thread (producer) ----

    /** Enqueue a touch-down event. Safe to call from the UI thread. */
    public final void enqueueTouchDown(float x, float y) {
        touchQueue.enqueueDownOrUp(TouchEventQueue.TYPE_DOWN, x, y);
    }

    /** Enqueue a touch-up event. Safe to call from the UI thread. */
    public final void enqueueTouchUp(float x, float y) {
        touchQueue.enqueueDownOrUp(TouchEventQueue.TYPE_UP, x, y);
    }

    /** Enqueue a touch-move event. Safe to call from the UI thread. */
    public final void enqueueTouchMove(float x1, float y1, float x2, float y2) {
        touchQueue.enqueueMove(x1, y1, x2, y2);
    }

    // ---- Called from GL thread (consumer) ----

    /** Drain all pending touch events and dispatch to subclass handlers. */
    protected final void processTouchEvents() {
        TouchEventQueue.Event e;
        while ((e = touchQueue.dequeue()) != null) {
            switch (e.type) {
                case TouchEventQueue.TYPE_DOWN:
                    onTouchDown(e.x1, e.y1);
                    break;
                case TouchEventQueue.TYPE_UP:
                    onTouchUp(e.x1, e.y1);
                    break;
                case TouchEventQueue.TYPE_MOVE:
                    onTouchMove(e.x1, e.y1, e.x2, e.y2);
                    break;
            }
        }
    }

    protected abstract void onTouchDown(float x, float y);

    protected abstract void onTouchUp(float x, float y);

    protected abstract void onTouchMove(float x1, float y1, float x2, float y2);

    protected abstract void setupAssets(AssetManager assetManager);

    protected abstract void initScene(Context context, int screenWidth, int screenHeight);

    public final void init(Context context, int screenWidth, int screenHeight){
        AssetManager assetManager = context.getAssets();
        InfillShaderPair.LOAD_SHADER_CODE(assetManager);
        FlatLitShaderPair.LOAD_SHADER_CODE();
        WireframeShaderPair.LOAD_SHADER_CODE(assetManager);
        ScreenInfo.setScreenSize(screenWidth, screenHeight);
        setupAssets(assetManager);
        initScene(context, screenWidth, screenHeight);
    }

    public abstract void updateThenDraw(float dt);

    public abstract void onClose();

    public abstract void onSwitch();

    public abstract void onReturn();

    private boolean is_paused = false;

    /**
     * Called when the application is paused. The default implementation marks the stage as paused.
     * Subclasses can override this to save state or pause expensive operations like sound.
     */
    public void pause() {
        onPause();
        this.is_paused = true;
    }

    /**
     * Called when the application is resumed.
     */
    public void resume() {
        onResume();
        this.is_paused = false;
    }


    /**
     * @return true if the stage is currently paused.
     */
    public boolean isPaused() {
        return is_paused;
    }

    public boolean isInitialized() {
        return is_initialized;
    }

    public void setInitialized() {
        is_initialized = true;
    }

    public abstract void reloadGPUResourcesRecursivelyOnContextLoss();

    public abstract void cleanupGPUResourcesRecursively();

    protected abstract void onPause();

    protected abstract void onResume();

    protected StageManager stageManager;

    private boolean is_initialized = false;


}