package com.example.game3d_opengl.game.stages;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.rendering.object3d.Camera;
import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain_api.structures.Terrain2DCurve;
import com.example.game3d_opengl.game.terrain_api.structures.TerrainCurve;
import com.example.game3d_opengl.game.terrain_api.structures.TerrainLine;

public class TestStage2 implements Stage {

    private Terrain terrain;
    private Camera camera;
    public TestStage2(MyGLRenderer.StageManager stageManager) {
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
                0f, 1f, 0f); // which way is up
        camera.setProjectionAsScreen();
        terrain = new Terrain(
                200, 4,
                V3(-1f, -0.5f, -15.5f),
                2.5f, 1.5f
        );

        terrain.enqueueStructure(new TerrainLine(20));
        terrain.enqueueStructure(new TerrainCurve(20, PI/8));
        terrain.enqueueStructure(new Terrain2DCurve(30, 0, 0.5f * PI/4));
        terrain.enqueueStructure(new Terrain2DCurve(30, PI/12, -0.5f * PI/4));
        terrain.enqueueStructure(new TerrainLine(20));
        terrain.enqueueStructure(new TerrainLine(20));
        terrain.enqueueStructure(new TerrainCurve(20, -PI/2));
        //terrain.generateChunks(-1);

        /*System.out.println("TILE COUNT: "+terrain.getTileCount());
        for (int i = 0; i < terrain.getTileCount(); ++i) {
            System.out.println(terrain.getTile(i));
        }*/

    }

    @Override
    public void updateThenDraw(float dt) {
        terrain.generateChunks(1);
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        for (int i = 0; i < terrain.getTileCount(); ++i) {
            terrain.getTile(i).setTileColor(CLR((float)(i) / (float)(terrain.getTileCount()) , 0.2f, 0, 1));
            terrain.getTile(i).draw(camera.getViewProjectionMatrix());
        }
        for(int i=0;i<terrain.getAddonCount();++i){
            terrain.getAddon(i).draw(camera.getViewProjectionMatrix());
        }
    }
}
