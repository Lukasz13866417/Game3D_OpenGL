package com.example.game3d_opengl.game.stage.stages.test;


import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TileManager;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainStairs;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.game.stage.stages.test.util.FourPoints3D;
import com.example.game3d_opengl.game.stage.stages.test.util.LineSet3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class TestTerrainStage extends Stage {

    private Camera camera;
   // private TileManager tileManager;
    private FourPoints3D[] grid;
    private LineSet3D left, right;

    private LightSource lightSource;

    private static final Vector3D cameraPos = V3(0,6f,2f);

    private static final Vector3D cameraLookAt = cameraPos.addY(-10);

    private static final Vector3D cameraUp = cameraPos.addY(100);

    private static final Vector3D lightSourcePos = cameraPos;

    private static final Vector3D firstTileStartCenter = cameraLookAt;


    public TestTerrainStage(MyGLRenderer.StageManager stageManager) {
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

    Terrain terrain;

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        this.camera = new Camera(cameraPos,cameraLookAt,cameraUp);
        camera.setProjectionAsScreen();
        camera.rotate180AroundForward();

        DeathSpike.LOAD_DEATHSPIKE_ASSETS();
        Potion.LOAD_POTION_ASSETS(context.getAssets());

        // build terrain
        /*tileManager = new TileManager(
                200, 2,
                V3(0, -0.5f, -3f),
                2f, 0.5f, 0.75f
        );*/

       /* this.tileManager = new TileManager(
                200,
                2,
                firstTileStartCenter,
                1f,
                0.5f,
                0.3f
        );*/

        this.terrain = new Terrain(
                200,
                2,
                firstTileStartCenter,
                1f,
                0.5f,
                0.3f
        );



        terrain.enqueueStructure(new TerrainStairs(4,2,PI/3, 0.5f));

        terrain.generateChunks(-1);

        //tileManager.addHorizontalAngle(PI/20);
        //for (int i = 0; i < 3; ++i) tileManager.addSegment(false);

        // grid rectangles as FourPoints3D
        int rows = Math.max(0, terrain.tileManager.getCurrRowCount());
        final int nCols = 2; // matches TileManager creation above
        grid = new FourPoints3D[rows * nCols];
        int idx = 0;
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= nCols; c++) {
                Vector3D[] field = terrain.tileManager.getField(r, c); // [TL, TR, BL, BR]
                // reorder to clockwise: TL, TR, BR, BL
                Vector3D[] cw = new Vector3D[]{field[0], field[1], field[3], field[2]};
                grid[idx++] = new FourPoints3D(cw);
                System.out.println("+_+ " + "r: "+r+" c: "+c+ " "+field[0] + " | "+field[1] + " | "+field[3] + " | "+field[2]);
            }
            System.out.println("+_+ " +"=================================");
        }
        left = new LineSet3D(terrain.tileManager.leftSideToArrayDebug(), new int[][]{}, FColor.CLR(1, 1, 1), FColor.CLR(1, 0, 1));
        right = new LineSet3D(terrain.tileManager.rightSideToArrayDebug(), new int[][]{}, FColor.CLR(1, 1, 1), FColor.CLR(0, 0, 1));

        lightSource = new LightSource(FColor.CLR(1,1,1));

    }

    @Override
    public void updateThenDraw(float dt) {
        lightSource.setPosition(lightSourcePos);

        /*for (FourPoints3D fp : grid) {
            fp.draw(camera.getViewProjectionMatrix()); // enable when grid is drawn
        }*/
        terrain.draw(FColor.CLR(0,1,0),camera.getViewProjectionMatrix(),lightSource);
        terrain.tileManager.getTileLineSet().draw(camera.getViewProjectionMatrix());

        left.draw(camera.getViewProjectionMatrix());
        right.draw(camera.getViewProjectionMatrix());

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
