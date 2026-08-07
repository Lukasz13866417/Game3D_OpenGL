package com.example.game3d_opengl.game.stage.stages.test;


import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.terrain_api.main.tilemanager.TileManager;
import com.example.game3d_opengl.game.terrain.terrain_structures.Terrain2DCurve;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLineWithSpikeRect;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainStairs;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.game.stage.stages.test.util.FourPoints3D;
import com.example.game3d_opengl.game.stage.stages.test.util.LineSet3D;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TerrainGridField;
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

    private static final Vector3D firstTileStartCenter = cameraLookAt.addY(-20).addX(-2).addZ(160);

    // Pan state
    private static final float PAN_SENSITIVITY = 0.01f; // world units per pixel
    private Vector3D currEye = cameraPos;
    private Vector3D currLook = cameraLookAt;
    private boolean isPanning = false;

    public TestTerrainStage(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
    }

    @Override
    protected void onTouchDown(float x, float y) {
        isPanning = true;
    }

    @Override
    protected void onTouchUp(float x, float y) {
        isPanning = false;
    }

    @Override
    protected void onTouchMove(float x1, float y1, float x2, float y2) {
        if (!isPanning) return;
        float dx = x2 - x1;
        float dy = y2 - y1;
        float panX = dx * PAN_SENSITIVITY;
        float panZ = dy * PAN_SENSITIVITY; // swipe up (negative dy) -> move forward (-z)
        currEye = currEye.addX(panX).addZ(panZ);
        currLook = currLook.addX(panX).addZ(panZ);
        camera.updateEyePos(currEye);
        camera.updateLookPos(currLook);
    }

    FourPoints3D xd;

    Terrain terrain;

    @Override
    protected void setupAssets(android.content.res.AssetManager assetManager) {
        // No-op.
    }

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        this.camera = new Camera(cameraPos,cameraLookAt,cameraUp);
        camera.setProjectionAsScreen();
        camera.rotate180AroundForward();
        // initialize pan state vectors
        currEye = cameraPos;
        currLook = cameraLookAt;

        com.example.game3d_opengl.game.terrain.track_elements.GameplayElementBatchRenderers
                .ensureDefaultLoaded(context.getAssets());

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

        lightSource = new LightSource(FColor.CLR(1,1,1));

        this.terrain = new Terrain(
                2000,
                6,
                firstTileStartCenter,
                1f,
                3.2f * 0.33f,
                1.4f * 0.33f,
                lightSource
        );



        terrain.enqueueStructure(new TerrainLineWithSpikeRect(30));
        terrain.enqueueStructure(new TerrainLineWithSpikeRect(30));
        terrain.enqueueStructure(
                TerrainStairs.builder()
                        .tilesPerStair(100)
                        .stairCount(4)
                        .emptyBetween(1)
                        .horizontalAngleDelta(PI / 6f)
                        .jump(-1f)
                        .build()
        );
        terrain.enqueueStructure(new TerrainLineWithSpikeRect(30));
        terrain.enqueueStructure(
                Terrain2DCurve.builder()
                        .tilesToMake(50)
                        .horizontalAngleDelta(0f)
                        .verticalAngleDelta(PI / 8f)
                        .verticalAngleFadeoutTiles(5)
                        .build()
        );

        terrain.generateChunks(-1);

        //tileManager.addHorizontalAngle(PI/20);
        //for (int i = 0; i < 3; ++i) tileManager.addSegment(false);

        // grid rectangles as FourPoints3D
        /*int rows = Math.max(0, terrain.tileManager.getCurrRowCount());
        final int nCols = 2; // matches TileManager creation above
        grid = new FourPoints3D[rows * nCols];
        int idx = 0;
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= nCols; c++) {
                TerrainGridField field = terrain.tileManager.getField(r, c);
                // clockwise: nearLeft, nearRight, farRight, farLeft
                Vector3D[] cw = new Vector3D[]{field.nearLeft, field.nearRight, field.farRight, field.farLeft};
                grid[idx++] = new FourPoints3D(cw);
                System.out.println("+_+ " + "r: "+r+" c: "+c+ " "
                        + field.nearLeft + " | " + field.nearRight + " | " + field.farRight + " | " + field.farLeft);
            }
            System.out.println("+_+ " +"=================================");
        }
        left = new LineSet3D(terrain.tileManager.leftSideToArrayDebug(), new int[][]{}, FColor.CLR(1, 1, 1), FColor.CLR(1, 0, 1));
        right = new LineSet3D(terrain.tileManager.rightSideToArrayDebug(), new int[][]{}, FColor.CLR(1, 1, 1), FColor.CLR(0, 0, 1));*/


    }

    @Override
    public void updateThenDraw(float dt) {
        processTouchEvents();
        lightSource.setPosition(lightSourcePos);

        /*for (FourPoints3D fp : grid) {
            fp.draw(camera.getViewProjectionMatrix()); // enable when grid is drawn
        }*/
        terrain.setColorTheme(FColor.CLR(0,1,0));
        terrain.draw(camera.getViewProjectionMatrix());
        //terrain.tileManager.getTileLineSet().draw(camera.getViewProjectionMatrix());

        //left.draw(camera.getViewProjectionMatrix());
        //right.draw(camera.getViewProjectionMatrix());

    }

    @Override
    protected void onDeactivated(DeactivationReason reason) {

    }

    @Override
    protected void onActivated(ActivationReason reason) {

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
