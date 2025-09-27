package com.example.game3d_opengl.game.stage.stages.test;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;
import android.opengl.Matrix;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TileManager;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.game.stage.stages.test.util.FourPoints3D;
import com.example.game3d_opengl.game.stage.stages.test.util.LineSet3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class TestGridRowsStage extends Stage {

    private Camera camera;
    private TileManager tileManager;
    private FourPoints3D[] grid;
    private LineSet3D left, right;

    // Camera position and movement
    private float camX = 0f;
    private float camY = 15f;    // height above ground
    private float camZ = -7.5f;   // initial distance from origin
    private float moveSpeed = 0.00f; // movement per frame
    private float worldRoll = 0f;    // radians, rotate world around Z-axis
    private static final float ROLL_SENSITIVITY = 0.005f;      // radians per pixel
    private static final float HEIGHT_SENSITIVITY = 0.02f;    // world units per pixel
    private static final float MIN_CAM_Y = 2.0f;
    private static final float MAX_CAM_Y = 80.0f;

    // Gesture handling: lock dominant axis per swipe
    private enum SwipeAxis { NONE, HORIZONTAL, VERTICAL }
    private SwipeAxis activeSwipeAxis = SwipeAxis.NONE;
    private float touchStartX = 0f, touchStartY = 0f;
    private float lastTouchX = 0f, lastTouchY = 0f;

    public TestGridRowsStage(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
    }

    @Override
    public void onTouchDown(float x, float y) {
        touchStartX = x;
        touchStartY = y;
        lastTouchX = x;
        lastTouchY = y;
        activeSwipeAxis = SwipeAxis.NONE;
    }

    @Override
    public void onTouchUp(float x, float y) {
        activeSwipeAxis = SwipeAxis.NONE;
    }

    @Override
    public void onTouchMove(float x1, float y1, float x2, float y2) {
        float incDx = x2 - lastTouchX;
        float incDy = y2 - lastTouchY;

        if (activeSwipeAxis == SwipeAxis.NONE) {
            float totalDx = x2 - touchStartX;
            float totalDy = y2 - touchStartY;
            activeSwipeAxis = Math.abs(totalDx) > Math.abs(totalDy) ? SwipeAxis.HORIZONTAL : SwipeAxis.VERTICAL;
        }

        if (activeSwipeAxis == SwipeAxis.HORIZONTAL) {
            worldRoll += incDx * ROLL_SENSITIVITY;
        } else if (activeSwipeAxis == SwipeAxis.VERTICAL) {
            camY -= incDy * HEIGHT_SENSITIVITY; // swipe up -> increase height
            if (camY < MIN_CAM_Y) camY = MIN_CAM_Y;
            if (camY > MAX_CAM_Y) camY = MAX_CAM_Y;
        }

        lastTouchX = x2;
        lastTouchY = y2;
    }

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        this.camera = new Camera();
        Camera.setGlobalScreenSize(screenWidth, screenHeight);
        // initial camera setup: looking straight down
        camera.set(
                camX, camY, camZ,   // eye position
                camX, 0f, camZ,     // look straight down to ground below
                0f, 0f, -1f         // up vector to keep orientation stable
        );
        camera.setProjectionAsScreen();

        // build terrain
        tileManager = new TileManager(
                200, 2,
                V3(0, -0.5f, -3f),
                2f, 0.5f, 0.75f
        );
        //for (int i = 0; i < 6 ; ++i) {tileManager.addSegment(false); }
        //tileManager.addSegment(true);
        //tileManager.addSegment(true);
        for (int i = 0; i < 6 ; ++i) {
            tileManager.addSegment(false); tileManager.addHorizontalAngle(PI/60);}

        //tileManager.addHorizontalAngle(PI/20);
        //for (int i = 0; i < 3; ++i) tileManager.addSegment(false);

        // grid rectangles as FourPoints3D
        int rows = Math.max(0, tileManager.getCurrRowCount());
        final int nCols = 2; // matches TileManager creation above
        grid = new FourPoints3D[rows * nCols];
        int idx = 0;
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= nCols; c++) {
                Vector3D[] field = tileManager.getField(r, c); // [TL, TR, BL, BR]
                // reorder to clockwise: TL, TR, BR, BL
                Vector3D[] cw = new Vector3D[]{field[0], field[1], field[3], field[2]};
                grid[idx++] = new FourPoints3D(cw);
            }
        }
        left = new LineSet3D(tileManager.leftSideToArrayDebug(), new int[][]{}, FColor.CLR(1, 1, 1), FColor.CLR(1, 0, 1));
        right = new LineSet3D(tileManager.rightSideToArrayDebug(), new int[][]{}, FColor.CLR(1, 1, 1), FColor.CLR(0, 0, 1));



    }

    @Override
    public void updateThenDraw(float dt) {
        // move camera forward each frame
        camZ -= moveSpeed;
        // static camera looking straight down; world roll is applied via VP matrix
        camera.set(
                camX, camY, camZ,
                camX, 0f, camZ,
                0f, 0f, -1f
        );

        // Build rotated VP once per frame
        float[] vp = camera.getViewProjectionMatrix();
        float[] rot = new float[16];
        float[] vpRot = new float[16];
        Matrix.setRotateM(rot, 0, (float) Math.toDegrees(worldRoll), 0f, 0f, -1f);
        Matrix.multiplyMM(vpRot, 0, vp, 0, rot, 0); // P*V*Rz

        if (grid != null) {
            for (FourPoints3D fp : grid) {
               fp.draw(vpRot); // enable when grid is drawn
            }
        }
        left.draw(vpRot);
        right.draw(vpRot);

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
