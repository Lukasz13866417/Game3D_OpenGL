package com.example.game3d_opengl;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.example.game3d_opengl.game.stages.GameplayStage;
import com.example.game3d_opengl.game.stages.Stage;
import com.example.game3d_opengl.game.stages.TestStage;

public class MyGLRenderer implements GLSurfaceView.Renderer {

    private final Context androidContext;

    private long lastFrameTime = System.nanoTime();

    private final StageManager stageManager = new StageManager();
    private Stage currStage = new GameplayStage(stageManager);// new TestStage(stageManager);//

    public class StageManager {
        public void switchTo(Stage to) {
            currStage = to;
        }
    }

    public MyGLRenderer(Context androidContext) {
        this.androidContext = androidContext;
    }

    public Stage getCurrentStage() {
        return currStage;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float deltaTime = (now - lastFrameTime) / 1000000f;
        lastFrameTime = now;
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        currStage.updateThenDraw(deltaTime);
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        getCurrentStage().initScene(androidContext, width, height);
    }
}