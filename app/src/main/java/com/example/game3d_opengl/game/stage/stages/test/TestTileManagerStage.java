package com.example.game3d_opengl.game.stage.stages.test;

import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TileManager;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class TestTileManagerStage extends Stage {


    public TestTileManagerStage(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
    }


    private TileManager tileManager;
    private Camera camera;

    private LightSource lightSource;


    private static final Vector3D cameraPos = V3(0,6f,2f);

    private static final Vector3D cameraLookAt = cameraPos.addY(-10);

    private static final Vector3D cameraUp = cameraPos.addY(100);

    private static final Vector3D lightSourcePos = cameraPos;

    private static final Vector3D firstTileStartCenter = cameraLookAt.addZ(1.5f);


    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {

        this.tileManager = new TileManager(
            2000,
                5,
                firstTileStartCenter,
                1f,
                0.5f,
                0.3f
        );
        this.camera = new Camera(cameraPos,cameraLookAt,cameraUp);
        camera.setProjectionAsScreen();
        camera.rotate180AroundForward();

        this.lightSource = new LightSource(FColor.CLR(1,1,1));

        for(int i=0;i<3;++i) this.tileManager.addSegment(false);


        tileManager.printTiles();
        tileManager.printLR();
    }

    @Override
    public void updateThenDraw(float dt) {
        lightSource.setPosition(lightSourcePos);
        tileManager.draw(FColor.CLR(1,0,0), camera.getViewProjectionMatrix(), lightSource);
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
    public void onClose() {

    }
    @Override
    public void onSwitch() {

    }
    @Override
    public void onReturn() {

    }
    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {

    }
    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {

    }
    @Override
    protected void onPause() {

    }
    @Override
    protected void onResume() {

    }
}
