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
    public enum ActivationReason {
        FRESH_ENTER,
        REVEALED
    }

    public enum DeactivationReason {
        COVERED,
        DISCARDED
    }

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

    public final void enqueueTouchDown(float x, float y, long timeNanos) {
        touchQueue.enqueueDownOrUp(TouchEventQueue.TYPE_DOWN, x, y, timeNanos);
    }

    /** Enqueue a touch-up event. Safe to call from the UI thread. */
    public final void enqueueTouchUp(float x, float y) {
        touchQueue.enqueueDownOrUp(TouchEventQueue.TYPE_UP, x, y);
    }

    public final void enqueueTouchUp(float x, float y, long timeNanos) {
        touchQueue.enqueueDownOrUp(TouchEventQueue.TYPE_UP, x, y, timeNanos);
    }

    /** Enqueue an interrupted gesture without turning it into a gameplay release. */
    public final void enqueueTouchCancel(float x, float y, long timeNanos) {
        touchQueue.enqueueDownOrUp(TouchEventQueue.TYPE_CANCEL, x, y, timeNanos);
    }

    /** Enqueue a touch-move event. Safe to call from the UI thread. */
    public final void enqueueTouchMove(float x1, float y1, float x2, float y2) {
        touchQueue.enqueueMove(x1, y1, x2, y2);
    }

    public final void enqueueTouchMove(float x1, float y1, float x2, float y2, long timeNanos) {
        touchQueue.enqueueMove(x1, y1, x2, y2, timeNanos);
    }

    // ---- Called from GL thread (consumer) ----

    /** Drain all pending touch events and dispatch to subclass handlers. */
    protected final void processTouchEvents() {
        TouchEventQueue.Event e;
        while ((e = touchQueue.dequeue()) != null) {
            switch (e.type) {
                case TouchEventQueue.TYPE_DOWN:
                    onTouchDownTimed(e.x1, e.y1, e.timeNanos, e.sequence);
                    break;
                case TouchEventQueue.TYPE_UP:
                    onTouchUpTimed(e.x1, e.y1, e.timeNanos, e.sequence);
                    break;
                case TouchEventQueue.TYPE_MOVE:
                    onTouchMoveTimed(e.x1, e.y1, e.x2, e.y2,
                            e.timeNanos, e.sequence);
                    break;
                case TouchEventQueue.TYPE_CANCEL:
                    onTouchCancelTimed(e.x1, e.y1, e.timeNanos, e.sequence);
                    break;
            }
        }
    }

    /** Drops input queued for a stage that is being reused for a new gameplay session. */
    protected final void clearPendingTouchEvents() {
        touchQueue.clear();
    }

    protected abstract void onTouchDown(float x, float y);

    protected abstract void onTouchUp(float x, float y);

    protected abstract void onTouchMove(float x1, float y1, float x2, float y2);

    protected void onTouchDownTimed(float x, float y, long timeNanos, long sequence) {
        onTouchDown(x, y);
    }

    protected void onTouchUpTimed(float x, float y, long timeNanos, long sequence) {
        onTouchUp(x, y);
    }

    protected void onTouchMoveTimed(float x1, float y1, float x2, float y2,
                                    long timeNanos, long sequence) {
        onTouchMove(x1, y1, x2, y2);
    }

    /**
     * Legacy stages treat cancellation like release. Authoritative gameplay overrides this
     * callback so the shared engine can discard the gesture without executing a jump.
     */
    protected void onTouchCancelTimed(
            float x, float y, long timeNanos, long sequence) {
        onTouchUpTimed(x, y, timeNanos, sequence);
    }

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

    /**
     * Whether the renderer must clear the default framebuffer before this stage draws.
     *
     * Stages that fully overwrite the screen from an offscreen render target can opt out. This
     * lets them do useful offscreen work before acquiring a default-framebuffer buffer.
     */
    public boolean requiresDefaultFramebufferClear() {
        return true;
    }

    /**
     * Extended frame callback. Presentation code receives the capped millisecond delta while
     * fixed-step simulations may consume the uncapped monotonic elapsed time.
     */
    public void updateThenDraw(float presentationDtMillis,
                               long frameTimeNanos,
                               long rawElapsedNanos) {
        updateThenDraw(presentationDtMillis);
    }

    public final void activate(ActivationReason reason) {
        onActivated(reason);
    }

    public final void deactivate(DeactivationReason reason) {
        onDeactivated(reason);
    }

    public final void discard() {
        deactivate(DeactivationReason.DISCARDED);
        cleanupGPUResourcesRecursively();
        releaseOwnedResourcesOnDiscard();
        touchQueue.clear();
        is_initialized = false;
        is_paused = false;
    }

    protected void onActivated(ActivationReason reason) {
        // default no-op
    }

    protected void onDeactivated(DeactivationReason reason) {
        // default no-op
    }

    protected void releaseOwnedResourcesOnDiscard() {
        // default no-op
    }

    // Activity lifecycle callbacks write this on the UI thread; the GL thread reads it.
    private volatile boolean is_paused = false;

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
