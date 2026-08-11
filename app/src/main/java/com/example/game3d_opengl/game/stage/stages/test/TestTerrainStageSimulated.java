package com.example.game3d_opengl.game.stage.stages.test;

import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.stage.stages.test.util.FourPoints3D;
import com.example.game3d_opengl.game.stage.stages.test.util.LineSet3D;
import com.example.game3d_opengl.game.terrain.terrain_api.main.tilemanager.TileManager;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TerrainGridField;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class TestTerrainStageSimulated extends Stage {

    public TestTerrainStageSimulated(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
    }

    private TileManager tileManager;
    private Camera camera;
    private LightSource lightSource;


    private static final Vector3D cameraPos = V3(0,6f,2f);

    private static final Vector3D cameraLookAt = cameraPos.addY(-10);

    private static final Vector3D cameraUp = cameraPos.addY(100);

    private static final Vector3D lightSourcePos = cameraPos;

    private static final Vector3D firstTileStartCenter = cameraLookAt;


    private LineSet3D startSegDemo;

    private LineSet3D howItShouldLook;

    private FourPoints3D[] grid;


    @Override
    protected void setupAssets(android.content.res.AssetManager assetManager) {
        // No-op.
    }

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {

        final int nCols = 2; // matches TileManager creation above
        this.tileManager = new TileManager(
                2000,
                nCols,
                firstTileStartCenter,
                0.4f,
                0.2f,
                0.15f
        );
        this.camera = new Camera(cameraPos,cameraLookAt,cameraUp);
        camera.setProjectionAsScreen();
        camera.rotate180AroundForward();

        this.lightSource = new LightSource(FColor.CLR(1,1,1));

        for(int i=0;i<11;++i){
            tileManager.addSegment(false);
            tileManager.addSegment(true);
        }
        /*
        tileManager.setUpcomingAlphas(0.2f,0.2f);
        tileManager.addHorizontalAngle(0.1308997f);
        tileManager.addVerticalAngle(0.0f);
        tileManager.liftUp(0.5f);
        tileManager.addSegment(false);
        tileManager.addHorizontalAngle(0.1308997f);
        tileManager.addVerticalAngle(0.0f);
        tileManager.setUpcomingAlphas(0.5f,0.5f);
        tileManager.addSegment(false);
        tileManager.addHorizontalAngle(0.1308997f);
        tileManager.addVerticalAngle(0.0f);
        tileManager.setUpcomingAlphas(0.8f,0.8f);
        tileManager.addSegment(false);
        tileManager.addHorizontalAngle(0.1308997f);
        tileManager.addVerticalAngle(0.0f);
        tileManager.addSegment(false);
        tileManager.addVerticalAngle(-0.0f);
        tileManager.addHorizontalAngle(-0.5235988f);

        tileManager.addSegment(true);
        tileManager.liftUp(0.5f);
        tileManager.addHorizontalAngle(0.1308997f);
        tileManager.addVerticalAngle(0.0f);
        tileManager.addSegment(false);
        tileManager.addHorizontalAngle(0.1308997f);
        tileManager.addVerticalAngle(0.0f);
        tileManager.addSegment(false);
        tileManager.addHorizontalAngle(0.1308997f);
        tileManager.addVerticalAngle(0.0f);
        tileManager.addSegment(false);
        tileManager.addHorizontalAngle(0.1308997f);
        tileManager.addVerticalAngle(0.0f);
        tileManager.addSegment(false);
        tileManager.addVerticalAngle(-0.0f);
        tileManager.addHorizontalAngle(-0.5235988f);
        tileManager.liftUp(0.5f);
        */


        tileManager.printTiles();
        System.out.println("=================================");


        howItShouldLook = tileManager.getTileLineSet();

        int rows = Math.max(0, tileManager.getCurrRowCount());
        grid = new FourPoints3D[rows * nCols];
        int idx = 0;
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= nCols; c++) {
                TerrainGridField field = tileManager.getField(r, c);
                // clockwise: nearLeft, nearRight, farRight, farLeft
                Vector3D[] cw = new Vector3D[]{field.nearLeft, field.nearRight, field.farRight, field.farLeft};
                grid[idx++] = new FourPoints3D(cw);
                System.out.println("+_+ " + "r: "+r+" c: "+c+ " "
                        + field.nearLeft + " | " + field.nearRight + " | " + field.farRight + " | " + field.farLeft);
            }
            System.out.println("+_+ " +"=================================");
        }


        startSegDemo = new LineSet3D(new Vector3D[]{
                firstTileStartCenter.addX(-0.5f),
                firstTileStartCenter.addX(0.5f)
        }, new int[][]{{0,1}}, FColor.CLR(0,1,0), FColor.CLR(0,0,1));
    }

    @Override
    public void updateThenDraw(float dt) {
        processTouchEvents();
        lightSource.setPosition(lightSourcePos);
        tileManager.draw(FColor.CLR(1,0,0), camera.getViewProjectionMatrix(), lightSource);
        //startSegDemo.draw(camera.getViewProjectionMatrix());
        //howItShouldLook.draw(camera.getViewProjectionMatrix());

        for (FourPoints3D fp : grid) {
            fp.draw(camera.getViewProjectionMatrix()); // enable when grid is drawn
        }
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
    protected void onDeactivated(DeactivationReason reason) {

    }
    @Override
    protected void onActivated(ActivationReason reason) {

    }
    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {

    }
    @Override
    public void cleanupGPUResourcesRecursively() {

    }
    @Override
    protected void onPause() {

    }
    @Override
    protected void onResume() {

    }
}
