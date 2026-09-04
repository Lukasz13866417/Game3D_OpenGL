package com.example.game3d_opengl;

import android.annotation.TargetApi;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.view.Display;
import android.view.Choreographer;
import android.view.MotionEvent;

import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.util.AndroidGameClock;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;

public class MyGLSurfaceView extends GLSurfaceView {
    private static final int EGL_OPENGL_ES3_BIT_KHR = 0x40;

    private final MyGLRenderer renderer;
    private final Choreographer choreographer;
    private final PresentationCallbackScheduler presentationCallbackScheduler;
    private boolean vsyncCallbacksRunning;
    private volatile int vsyncDivisor = 1; // 1 = render every vsync
    private int vsyncCounter = 0;

    public MyGLSurfaceView(Context context){
        super(context);

        setEGLContextClientVersion(3);

        setEGLConfigChooser((egl, display) -> {
            int[] attribList = {
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 24,
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                    EGL10.EGL_SAMPLE_BUFFERS, 1,
                    EGL10.EGL_SAMPLES, 4,
                    EGL10.EGL_NONE
            };

            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];

            if (!egl.eglChooseConfig(display, attribList, configs, 1, numConfigs)) {
                throw new IllegalArgumentException("eglChooseConfig failed");
            }

            return configs[0];
        });

        setPreserveEGLContextOnPause(true);

        renderer = new MyGLRenderer(context);
        setRenderer(renderer);
        // Use display vsync via Choreographer; one draw per vsync
        setRenderMode(RENDERMODE_WHEN_DIRTY);
        choreographer = Choreographer.getInstance();
        presentationCallbackScheduler =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ? new Api33PresentationCallbackScheduler(
                                choreographer, this::dispatchVsync)
                        : new LegacyPresentationCallbackScheduler(
                                choreographer, this::dispatchVsync);
        setKeepScreenOn(true);
    }

    private void dispatchVsync(long presentationTimeNanos) {
        if (!vsyncCallbacksRunning) {
            return;
        }
        int divisor = vsyncDivisor;
        boolean renderThisVsync;
        if (divisor <= 1) {
            renderThisVsync = true;
        } else {
            vsyncCounter++;
            if (vsyncCounter >= divisor) {
                vsyncCounter = 0;
                renderThisVsync = true;
            } else {
                renderThisVsync = false;
            }
        }
        // Publish and request from one ordered GL-thread event. GLSurfaceView drains queued events
        // before selecting its next dirty render, so a callback that arrives during an in-flight
        // draw remains unavailable to that draw. Multiple callbacks waiting behind the same draw
        // still coalesce in the renderer's latest-only mailbox before one render is selected.
        queueEvent(() -> {
            renderer.onVsync(presentationTimeNanos);
            if (renderThisVsync) {
                requestRender();
            }
        });
        postNextVsyncCallback();
    }

    private void startVsyncCallbacks() {
        if (vsyncCallbacksRunning) {
            return;
        }
        vsyncCallbacksRunning = true;
        vsyncCounter = 0;
        queueEvent(renderer::resetFrameTimeline);
        renderer.setUseFrameCap(false);
        postNextVsyncCallback();
    }

    private void postNextVsyncCallback() {
        if (!vsyncCallbacksRunning) {
            return;
        }
        presentationCallbackScheduler.post();
    }

    private void stopVsyncCallbacks() {
        if (!vsyncCallbacksRunning) {
            return;
        }
        vsyncCallbacksRunning = false;
        presentationCallbackScheduler.remove();
        queueEvent(renderer::resetFrameTimeline);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startVsyncCallbacks();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopVsyncCallbacks();
        super.onDetachedFromWindow();
    }

    @Override
    public void onPause() {
        stopVsyncCallbacks();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isAttachedToWindow()) {
            startVsyncCallbacks();
        }
    }

    private interface PresentationTimeConsumer {
        void accept(long presentationTimeNanos);
    }

    private interface PresentationCallbackScheduler {
        void post();

        void remove();
    }

    private static final class LegacyPresentationCallbackScheduler
            implements PresentationCallbackScheduler {
        private final Choreographer choreographer;
        private final Choreographer.FrameCallback callback;

        LegacyPresentationCallbackScheduler(
                Choreographer choreographer,
                PresentationTimeConsumer consumer) {
            this.choreographer = choreographer;
            callback = consumer::accept;
        }

        @Override
        public void post() {
            choreographer.postFrameCallback(callback);
        }

        @Override
        public void remove() {
            choreographer.removeFrameCallback(callback);
        }
    }

    /** Keeps API-33-only Choreographer types out of the class verified on older devices. */
    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private static final class Api33PresentationCallbackScheduler
            implements PresentationCallbackScheduler {
        private final Choreographer choreographer;
        private final Choreographer.VsyncCallback callback;

        Api33PresentationCallbackScheduler(
                Choreographer choreographer,
                PresentationTimeConsumer consumer) {
            this.choreographer = choreographer;
            callback = frameData -> consumer.accept(
                    frameData.getPreferredFrameTimeline()
                            .getExpectedPresentationTimeNanos());
        }

        @Override
        public void post() {
            choreographer.postVsyncCallback(callback);
        }

        @Override
        public void remove() {
            choreographer.removeVsyncCallback(callback);
        }
    }

    public MyGLRenderer getRenderer() {
        return renderer;
    }

    /**
     * Render every Nth vsync. Set to 1 to render each vsync (no below-refresh cap).
     */
    public void setVsyncDivisor(int divisor) {
        if (divisor < 1) divisor = 1;
        if (this.vsyncDivisor != divisor) {
            this.vsyncDivisor = divisor;
            this.vsyncCounter = 0;
        }
    }

    /**
     * Set a target FPS below the display refresh by skipping vsyncs.
     * If targetFps >= refresh, caps are disabled (render each vsync).
     */
    public void setTargetFps(float targetFps) {
        float refresh = 60f;
        Display d = getDisplay();
        if (d != null && d.getRefreshRate() > 0f) {
            refresh = d.getRefreshRate();
        }
        int divisor = 1;
        if (targetFps > 0f && targetFps < refresh - 0.5f) {
            divisor = Math.max(1, Math.round(refresh / targetFps));
        }
        setVsyncDivisor(divisor);
    }

    private static final int INVALID_POINTER_ID = -1;
    private float lastX = 0f;
    private float lastY = 0f;
    private int activePointerId = INVALID_POINTER_ID;
    private boolean activePointerWasDispatched;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        long eventTimeNanos = AndroidGameClock.eventTimeNanos(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (activePointerId == INVALID_POINTER_ID
                        || !activePointerWasDispatched) {
                    break;
                }
                int moveIndex = event.findPointerIndex(activePointerId);
                if (moveIndex < 0) {
                    if (renderer.getCurrentStage().isInitialized()) {
                        renderer.getCurrentStage().enqueueTouchCancel(
                                lastX, lastY, eventTimeNanos);
                    }
                    clearActivePointer();
                    break;
                }
                Stage moveStage = renderer.getCurrentStage();
                if (!moveStage.isInitialized()) break;
                // Android may batch several sampled points into one ACTION_MOVE. Preserve the
                // actual path so a horizontal turn followed by an upward jump gesture is not
                // collapsed into one misleading diagonal delta.
                for (int historyPosition = 0;
                     historyPosition < event.getHistorySize();
                     historyPosition++) {
                    enqueueMoveIfChanged(
                            moveStage,
                            event.getHistoricalX(moveIndex, historyPosition),
                            event.getHistoricalY(moveIndex, historyPosition),
                            AndroidGameClock.historicalEventTimeNanos(
                                    event, historyPosition));
                }
                enqueueMoveIfChanged(
                        moveStage,
                        event.getX(moveIndex),
                        event.getY(moveIndex),
                        eventTimeNanos);
                break;
            case MotionEvent.ACTION_DOWN:
                int downIndex = event.getActionIndex();
                activePointerId = event.getPointerId(downIndex);
                float downX = event.getX(downIndex);
                float downY = event.getY(downIndex);
                lastX = downX;
                lastY = downY;
                activePointerWasDispatched =
                        renderer.getCurrentStage().isInitialized();
                if (activePointerWasDispatched) {
                    renderer.getCurrentStage().enqueueTouchDown(
                            downX, downY, eventTimeNanos);
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                // A secondary pointer event can also carry a newer sample for the primary
                // pointer. Preserve that primary movement without changing gesture ownership.
                enqueueActivePointerSample(event, eventTimeNanos);
                break;
            case MotionEvent.ACTION_POINTER_UP:
                int pointerUpIndex = event.getActionIndex();
                if (event.getPointerId(pointerUpIndex) == activePointerId) {
                    float pointerUpX = event.getX(pointerUpIndex);
                    float pointerUpY = event.getY(pointerUpIndex);
                    if (activePointerWasDispatched
                            && renderer.getCurrentStage().isInitialized()) {
                        Stage pointerUpStage = renderer.getCurrentStage();
                        enqueueMoveIfChanged(
                                pointerUpStage,
                                pointerUpX,
                                pointerUpY,
                                eventTimeNanos);
                        pointerUpStage.enqueueTouchUp(
                                pointerUpX, pointerUpY, eventTimeNanos);
                    }
                    clearActivePointer();
                } else {
                    enqueueActivePointerSample(event, eventTimeNanos);
                }
                break;
            case MotionEvent.ACTION_UP:
                int upIndex = event.getActionIndex();
                if (event.getPointerId(upIndex) == activePointerId
                        && activePointerWasDispatched
                        && renderer.getCurrentStage().isInitialized()) {
                    Stage upStage = renderer.getCurrentStage();
                    float upX = event.getX(upIndex);
                    float upY = event.getY(upIndex);
                    enqueueMoveIfChanged(upStage, upX, upY, eventTimeNanos);
                    upStage.enqueueTouchUp(upX, upY, eventTimeNanos);
                }
                clearActivePointer();
                break;
            case MotionEvent.ACTION_CANCEL:
                if (activePointerWasDispatched
                        && renderer.getCurrentStage().isInitialized()) {
                    renderer.getCurrentStage().enqueueTouchCancel(
                            lastX, lastY, eventTimeNanos);
                }
                clearActivePointer();
                break;
        }
        return true;
    }

    private void enqueueMoveIfChanged(
            Stage stage, float x, float y, long timeNanos) {
        if (x == lastX && y == lastY) {
            return;
        }
        stage.enqueueTouchMove(lastX, lastY, x, y, timeNanos);
        lastX = x;
        lastY = y;
    }

    private void enqueueActivePointerSample(MotionEvent event, long timeNanos) {
        if (activePointerId == INVALID_POINTER_ID || !activePointerWasDispatched) {
            return;
        }
        int pointerIndex = event.findPointerIndex(activePointerId);
        Stage stage = renderer.getCurrentStage();
        if (pointerIndex < 0 || !stage.isInitialized()) {
            return;
        }
        enqueueMoveIfChanged(
                stage, event.getX(pointerIndex), event.getY(pointerIndex), timeNanos);
    }

    private void clearActivePointer() {
        activePointerId = INVALID_POINTER_ID;
        activePointerWasDispatched = false;
    }
}
