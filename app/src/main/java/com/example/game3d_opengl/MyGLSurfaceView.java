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
                    EGL10.EGL_DEPTH_SIZE, 24,
                    EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
                    EGL10.EGL_SAMPLE_BUFFERS, 1,   // 💥 Enable MSAA
                    EGL10.EGL_SAMPLES, 4,          // 💥 4x MSAA
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


    float lastX=0, lastY=0;
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE:
                if (x != lastX || y != lastY) {
                    renderer.getCurrentStage().onTouchMove(lastX, lastY, x, y);
                }
                break;
            case MotionEvent.ACTION_DOWN:
                renderer.getCurrentStage().onTouchDown(x,y);
                break;
            case MotionEvent.ACTION_UP:
                renderer.getCurrentStage().onTouchUp(x,y);
                break;
        }
        lastX = x;
        lastY = y;
        return true;
    }
}
