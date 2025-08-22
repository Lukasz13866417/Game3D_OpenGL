package com.example.game3d_opengl;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;

public class MyGLSurfaceView extends GLSurfaceView {

    private final MyGLRenderer renderer;

    public MyGLSurfaceView(Context context){
        super(context);

        setEGLContextClientVersion(2);

        setEGLConfigChooser((egl, display) -> {
            int[] attribList = {
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 16,
                    EGL10.EGL_RENDERABLE_TYPE, 4,
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
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setKeepScreenOn(true);
    }

    public MyGLRenderer getRenderer() {
        return renderer;
    }

    float lastX=0, lastY=0;
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                if (x != lastX || y != lastY && renderer.getCurrentStage().isInitialized()) {
                    renderer.getCurrentStage().onTouchMove(lastX, lastY, x, y);
                }
                break;
            case MotionEvent.ACTION_DOWN:
                if(renderer.getCurrentStage().isInitialized()) {
                    renderer.getCurrentStage().onTouchDown(x, y);
                }
                break;
            case MotionEvent.ACTION_UP:
                if(renderer.getCurrentStage().isInitialized()) {
                    renderer.getCurrentStage().onTouchUp(x, y);
                }
                break;
        }
        lastX = x;
        lastY = y;
        return true;
    }
}
