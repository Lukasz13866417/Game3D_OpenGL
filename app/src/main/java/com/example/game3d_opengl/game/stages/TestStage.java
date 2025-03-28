package com.example.game3d_opengl.game.stages;

import static com.example.game3d_opengl.engine.util3d.vector.Vector3D.V3;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.engine.object3d.Camera;
import com.example.game3d_opengl.game.track_elements.DeathSpike;

public class TestStage implements Stage{

    DeathSpike deathSpike;
    private Camera camera;

    public TestStage(MyGLRenderer.StageManager stageManager) {
    }


    @Override
    public void onTouchDown(float x, float y) {
    }

    @Override
    public void onTouchUp(float x, float y) {
    }

    @Override
    public void onTouchMove(float x1, float y1, float x2, float y2) {
    }

    @Override
    public void initScene(Context context, int screenWidth, int screenHeight) {
        this.camera = new Camera();
        Camera.setGlobalScreenSize(screenWidth, screenHeight);
        this.camera.set(0f, 0f, 3f, // eye pos
                0f, 0f, 0f, // look at
                0f, 1f, 0f ); // which way is up
        camera.setProjectionAsScreen();
        deathSpike = new DeathSpike();
        deathSpike.place(
                V3(-0.25f,0.25f,-1.25f),
                V3(0.25f,0.25f,-1.25f),
                V3(-0.25f,0.25f,-1.50f),
                V3(0.25f,0.25f,-1.50f)

        );
    }

    @Override
    public void updateThenDraw(float dt) {
        deathSpike.draw(camera.getViewProjectionMatrix());
    }
}
