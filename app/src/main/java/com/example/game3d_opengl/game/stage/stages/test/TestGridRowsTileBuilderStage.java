package com.example.game3d_opengl.game.stage.stages.test;

import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;
import android.opengl.Matrix;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TileManager;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.game.stage.stages.test.util.FourPoints3D;
import com.example.game3d_opengl.game.stage.stages.test.util.ColoredFourPoints3D;
import com.example.game3d_opengl.game.stage.stages.test.util.LineSet3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Mirrors TestGridRowsStructuresStage visually but uses TileManager directly
 * (no lazy command buffer / Terrain). Skips addons. Prints row counts after
 * each "stair" build.
 */
public class TestGridRowsTileBuilderStage extends Stage {

    private Camera camera;
    private TileManager tileManager;
    private FourPoints3D[] grid;
    private LineSet3D left, right;
    // overlay (3-stair) visualization
    private ColoredFourPoints3D[] overlayGrid;
    private LineSet3D overlayLeft, overlayRight;

    // Camera position and movement (copied from TestGridRowsStructuresStage)
    private float camX = -2f;
    private float camY = 15f;    // height above ground
    private float camZ = -7.5f;  // initial distance from origin
    private float moveSpeed = 0.00f; // movement per frame
    private float worldRoll = 0f;    // radians, rotate world around Z-axis
    private static final float ROLL_SENSITIVITY = 0.005f;      // radians per pixel
    private static final float HEIGHT_SENSITIVITY = 0.02f;    // world units per pixel
    private static final float MIN_CAM_Y = 2.0f;
    private static final float MAX_CAM_Y = 280.0f;

    // Gesture handling: lock dominant axis per swipe
    private enum SwipeAxis { NONE, HORIZONTAL, VERTICAL }
    private SwipeAxis activeSwipeAxis = SwipeAxis.NONE;
    private float touchStartX = 0f, touchStartY = 0f;
    private float lastTouchX = 0f, lastTouchY = 0f;

    public TestGridRowsTileBuilderStage(MyGLRenderer.StageManager stageManager) {
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

        float segWidth = 0.8f, segLength = 0.4f;
        final int nCols = 6;
        this.tileManager = new TileManager(2000, nCols,
                V3(0, -0.5f, -3f),
                segWidth,
                segLength,
                0.25f
        );

        // Replicate the exact TileManager calls produced by Terrain2DCurve(6, PI/8, 0) x4
        final float totalHor = (float) (PI / 8.0);
        final float perTileHor = totalHor / 6.0f;
        final float perTileVer = 0.0f;

        for (int stair = 0; stair < 4; ++stair) {
            for (int i = 0; i < 6; ++i) {
                tileManager.addHorizontalAngle(perTileHor);
                tileManager.addVerticalAngle(perTileVer);
                tileManager.addSegment(false);
                System.out.println("<> r: " + tileManager.getCurrRowCount());
            }
            // Reset angles to initial (as Terrain2DCurve does)
            tileManager.addVerticalAngle(-perTileVer * 6.0f); // -0.0f
            tileManager.addHorizontalAngle(-totalHor);
            // Lift after each stair
            tileManager.liftUp(0.5f);

            // Print row count after each stair
            System.out.println("<> ROW COUNT AFTER STAIR " + stair + ": " + tileManager.getCurrRowCount());
        }

        // Build grid and debug side lines
        int rows = Math.max(0, tileManager.getCurrRowCount());
        grid = new FourPoints3D[rows * nCols];
        int idx = 0;
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= nCols; c++) {
                Vector3D[] field = tileManager.getField(r, c); // [TL, TR, BL, BR]
                Vector3D[] cw = new Vector3D[]{field[0], field[1], field[3], field[2]};
                grid[idx++] = new FourPoints3D(cw);
            }
        }
        left = new LineSet3D(tileManager.leftSideToArrayDebug(), new int[][]{}, FColor.CLR(1, 0, 1), FColor.CLR(1, 0, 0));
        right = new LineSet3D(tileManager.rightSideToArrayDebug(), new int[][]{}, FColor.CLR(1, 0, 1), FColor.CLR(1, 0, 0));

        // Build OVERLAY using a separate TileManager with 3 stairs, colored yellow
        TileManager overlayTM = new TileManager(2000, nCols,
                V3(0, -0.35f, -3f),
                segWidth,
                segLength,
                0.25f
        );
        for (int stair = 0; stair < 3; ++stair) {
            for (int i = 0; i < 6; ++i) {
                overlayTM.addHorizontalAngle(perTileHor);
                overlayTM.addVerticalAngle(perTileVer);
                overlayTM.addSegment(false);
            }
            overlayTM.addVerticalAngle(-perTileVer * 6.0f);
            overlayTM.addHorizontalAngle(-totalHor);
            overlayTM.liftUp(0.5f);
        }
        int rowsOv = Math.max(0, overlayTM.getCurrRowCount());
        overlayGrid = new ColoredFourPoints3D[rowsOv * nCols];
        int idxOv = 0;
        FColor overlayLine = FColor.CLR(1f, 1f, 0f); // yellow lines
        FColor overlayPoint = FColor.CLR(1f, 1f, 0f); // yellow points
        for (int r = 1; r <= rowsOv; r++) {
            for (int c = 1; c <= nCols; c++) {
                Vector3D[] field = overlayTM.getField(r, c); // [TL, TR, BL, BR]
                Vector3D[] cw = new Vector3D[]{field[0], field[1], field[3], field[2]};
                overlayGrid[idxOv++] = new ColoredFourPoints3D(cw, overlayLine, overlayPoint);
            }
        }
        overlayLeft = new LineSet3D(overlayTM.leftSideToArrayDebug(), new int[][]{}, overlayLine, overlayPoint);
        overlayRight = new LineSet3D(overlayTM.rightSideToArrayDebug(), new int[][]{}, overlayLine, overlayPoint);
    }

    @Override
    public void updateThenDraw(float dt) {
        camZ -= moveSpeed;
        // Update camera each frame to reflect camY zoom and maintain top-down look
        camera.set(
                camX, camY, camZ,
                camX, 0f, camZ,
                0f, 0f, -1f
        );
        float[] vp = camera.getViewProjectionMatrix();
        float[] rot = new float[16];
        float[] vpRot = new float[16];
        Matrix.setRotateM(rot, 0, (float) Math.toDegrees(worldRoll), 0f, 0f, -1f);
        Matrix.multiplyMM(vpRot, 0, vp, 0, rot, 0); // P*V*Rz

        if (grid != null) {
            for (FourPoints3D fp : grid) {
                fp.draw(vpRot);
            }
        }
        if (left != null) left.draw(vpRot);
        if (right != null) right.draw(vpRot);

        // Draw overlay on top
        if (overlayGrid != null) {
            for (ColoredFourPoints3D fp : overlayGrid) {
                fp.draw(vpRot);
            }
        }
        if (overlayLeft != null) overlayLeft.draw(vpRot);
        if (overlayRight != null) overlayRight.draw(vpRot);
    }

    @Override
    public void onClose() { }

    @Override
    public void onSwitch() { }

    @Override
    public void onReturn() { }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() { }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {}

    @Override
    protected void onPause() { }

    @Override
    protected void onResume() { }
}


