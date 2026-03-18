package com.example.game3d_opengl.game.stage.stages.test;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import static java.lang.Math.sqrt;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainFunction;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.rendering.util3d.FColor;

public class TestFunctionTerrainStage extends Stage {

    private Terrain terrain;
    private Camera camera;
    public TestFunctionTerrainStage(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
    }


    @Override
    protected void onTouchDown(float x, float y) {
    }

    @Override
    protected void onTouchUp(float x, float y) {
    }

    @Override
    protected void onTouchMove(float x1, float y1, float x2, float y2) {
    }


    @Override
    protected void setupAssets(android.content.res.AssetManager assetManager) {
        // No-op.
    }

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        this.camera = new Camera();
        this.camera.set(0f, 0f, 3f, // eye pos
                0f, 0f, 0f, // look at
                0f, 1f, 0f); // which way is up
        camera.setProjectionAsScreen();
        terrain = new Terrain(
                200, 4,
                V3(-1f, -0.5f, -5.5f),
                2.5f, 1.5f,
                0.5f,
                new LightSource(new FColor(1.0f,0,0))
        );

        terrain.enqueueStructure(new TerrainFunction(20,x -> 0.1f*(float)sqrt(x),0,2));

        /*System.out.println("TILE COUNT: "+terrain.getTileCount());
        for (int i = 0; i < terrain.getTileCount(); ++i) {
            System.out.println(terrain.getTile(i));
        }*/

    }

    @Override
    public void updateThenDraw(float dt) {
        processTouchEvents();
        terrain.generateChunks(1);
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        for(int i=0;i<terrain.getAddonCount();++i){
            terrain.getAddon(i).draw(camera.getViewProjectionMatrix());
        }
    }

    @Override
    public void onClose() {

    }

    @Override
    public void onSwitch() {

    }

    @Override
    public void onReturn() {

    }

    @Override
    public void onPause() {

    }

    @Override
    public void onResume() {

    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {

    }

    @Override
    public void cleanupGPUResourcesRecursively() {}
}
