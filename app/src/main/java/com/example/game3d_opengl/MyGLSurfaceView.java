package com.example.game3d_opengl;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.Display;
import android.view.MotionEvent;

import com.example.game3d_opengl.game.util.AndroidGameClock;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;

public class MyGLSurfaceView extends GLSurfaceView {
    private static final int EGL_OPENGL_ES3_BIT_KHR = 0x40;

    private final MyGLRenderer renderer;
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
        post(new Runnable() {
            final android.view.Choreographer choreographer = android.view.Choreographer.getInstance();
            final android.view.Choreographer.FrameCallback callback = new android.view.Choreographer.FrameCallback() {
                @Override public void doFrame(long frameTimeNanos) {
                    renderer.onVsync(frameTimeNanos);
                    int div = vsyncDivisor;
                    if (div <= 1) {
                        requestRender();
                    } else {
                        vsyncCounter++;
                        if (vsyncCounter >= div) {
                            vsyncCounter = 0;
                            requestRender();
                        }
                    }
                    choreographer.postFrameCallback(this);
                }
            };
            @Override public void run() {
                choreographer.postFrameCallback(callback);
                renderer.setUseFrameCap(false);
            }
        });
        setKeepScreenOn(true);
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
                float moveX = event.getX(moveIndex);
                float moveY = event.getY(moveIndex);
                if (!renderer.getCurrentStage().isInitialized()) break;
                if (moveX == lastX && moveY == lastY) break;
                renderer.getCurrentStage().enqueueTouchMove(
                        lastX, lastY, moveX, moveY, eventTimeNanos);
                lastX = moveX;
                lastY = moveY;
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
            case MotionEvent.ACTION_POINTER_UP:
                int pointerUpIndex = event.getActionIndex();
                if (event.getPointerId(pointerUpIndex) == activePointerId) {
                    float pointerUpX = event.getX(pointerUpIndex);
                    float pointerUpY = event.getY(pointerUpIndex);
                    if (activePointerWasDispatched
                            && renderer.getCurrentStage().isInitialized()) {
                        renderer.getCurrentStage().enqueueTouchUp(
                                pointerUpX, pointerUpY, eventTimeNanos);
                    }
                    clearActivePointer();
                }
                break;
            case MotionEvent.ACTION_UP:
                int upIndex = event.getActionIndex();
                if (event.getPointerId(upIndex) == activePointerId
                        && activePointerWasDispatched
                        && renderer.getCurrentStage().isInitialized()) {
                    renderer.getCurrentStage().enqueueTouchUp(
                            event.getX(upIndex),
                            event.getY(upIndex),
                            eventTimeNanos);
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

    private void clearActivePointer() {
        activePointerId = INVALID_POINTER_ID;
        activePointerWasDispatched = false;
    }
}
