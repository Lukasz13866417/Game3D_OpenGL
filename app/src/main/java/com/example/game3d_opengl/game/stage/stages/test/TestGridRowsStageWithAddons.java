package com.example.game3d_opengl.game.stage.stages.test;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;
import android.opengl.Matrix;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TileManager;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TerrainGridField;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.game.stage.stages.test.util.FourPoints3D;
import com.example.game3d_opengl.game.stage.stages.test.util.LineSet3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class TestGridRowsStageWithAddons extends Stage {

    private Camera camera;
    private TileManager tileManager;
    private FourPoints3D[] grid;
    private LineSet3D left, right;

    private LightSource lightSource;

    private static final Vector3D cameraPos = V3(0,6f,2f);

    private static final Vector3D cameraLookAt = cameraPos.addY(-16);

    private static final Vector3D cameraUp = cameraPos.addY(100);

    private static final Vector3D lightSourcePos = cameraPos;

    private static final Vector3D firstTileStartCenter = cameraLookAt.addX(-2);

    private LineSet3D tileLineSet;



    public TestGridRowsStageWithAddons(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
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

    FourPoints3D xd;

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        this.camera = new Camera(cameraPos,cameraLookAt,cameraUp);
        camera.setProjectionAsScreen();
        camera.rotate180AroundForward();

        // build terrain
        /*tileManager = new TileManager(
                200, 2,
                V3(0, -0.5f, -3f),
                2f, 0.5f, 0.75f
        );*/

        this.tileManager = new TileManager(
                200,
                2,
                firstTileStartCenter,
                1f,
                0.5f,
                0.3f
        );


        //for (int i = 0; i < 6 ; ++i) {tileManager.addSegment(false); }
        //tileManager.addSegment(true);
        //tileManager.addSegment(true);
        for (int i = 0; i < 13 ; ++i) {
            tileManager.addSegment(false); tileManager.addHorizontalAngle(PI/12);}


        tileManager.printTiles();

        //tileManager.addHorizontalAngle(PI/20);
        //for (int i = 0; i < 3; ++i) tileManager.addSegment(false);

        // grid rectangles as FourPoints3D
        int rows = Math.max(0, tileManager.getCurrRowCount());
        final int nCols = 2; // matches TileManager creation above
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

        lightSource = new LightSource(FColor.CLR(1,1,1));

        tileLineSet = tileManager.getTileLineSet();

    }

    @Override
    public void updateThenDraw(float dt) {
        lightSource.setPosition(lightSourcePos);

        for (FourPoints3D fp : grid) {
            fp.draw(camera.getViewProjectionMatrix()); // enable when grid is drawn
        }
        tileManager.draw(FColor.CLR(0,1,0), camera.getViewProjectionMatrix(), lightSource);
        tileLineSet.draw(camera.getViewProjectionMatrix());

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
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {}
}
