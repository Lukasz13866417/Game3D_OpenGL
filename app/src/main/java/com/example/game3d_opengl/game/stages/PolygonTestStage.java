package com.example.game3d_opengl.game.stages;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.stage_api.Stage;
import com.example.game3d_opengl.rendering.object3d.BasicPolygon3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.Camera;

import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

public class PolygonTestStage extends Stage {

    private Camera camera;
    private BasicPolygon3D poly;

    public PolygonTestStage(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
    }

    @Override
    public void onTouchDown(float x, float y) { }

    @Override
    public void onTouchUp(float x, float y) { }

    @Override
    public void onTouchMove(float x1, float y1, float x2, float y2) { }

    @Override
    public void initScene(Context context, int screenWidth, int screenHeight) {
        this.camera = new Camera();
        Camera.setGlobalScreenSize(screenWidth, screenHeight);
        this.camera.set(0f, 0f, 5f,
                0f, 0f, 0f,
                0f, 1f, 0f);
        camera.setProjectionAsScreen();

        float[] square = new float[]{
                -0.5f,  -0.5f, 0f,
                 0.5f,  -0.5f, 0f,
                 0.5f,  0.5f, 0f,
                -0.5f,   0.5f, 0f,
        };

        poly = new BasicPolygon3D.Builder()
                .fillColor(FColor.CLR(0.2f,0.8f,0.2f,1f))
                .edgeColor(FColor.CLR(1,1,1,1))
                .fromVertexData(square, false)
                .build();
    }

    @Override
    public void updateThenDraw(float dt) {
        poly.draw(camera.getViewProjectionMatrix());
    }

    @Override
    public void onClose() { }

    @Override
    public void onSwitch() { }

    @Override
    public void onReturn() { }

    @Override
    public void onPause() { }

    @Override
    public void onResume() { }

    @Override
    public void resetGPUResources() { }
}


