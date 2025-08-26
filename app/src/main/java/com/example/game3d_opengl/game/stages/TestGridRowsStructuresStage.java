package com.example.game3d_opengl.game.stages;

import static com.example.game3d_opengl.rendering.util3d.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;
import android.opengl.Matrix;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.stage_api.Stage;
import com.example.game3d_opengl.game.terrain_api.main.Tile;
import com.example.game3d_opengl.game.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain_structures.Terrain2DCurve;
import com.example.game3d_opengl.game.terrain_structures.TerrainEmptySegments;
import com.example.game3d_opengl.game.terrain_structures.TerrainLine;
import com.example.game3d_opengl.game.terrain_structures.TerrainSpiral;
import com.example.game3d_opengl.game.terrain_structures.TerrainStairs;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.FourPoints3D;
import com.example.game3d_opengl.rendering.LineSet3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Mirrors TestGridRowsStage visually but uses Terrain + TerrainStructures
 * instead of direct TileBuilder usage. It renders tiles and can be rotated
 * with the same gesture logic.
 */
public class TestGridRowsStructuresStage extends Stage {

    private Camera camera;
    private Terrain terrain;
    private FourPoints3D[] grid;
    private LineSet3D left, right;

    // Camera position and movement (copied from TestGridRowsStage)
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

    public TestGridRowsStructuresStage(MyGLRenderer.StageManager stageManager) {
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
    public void initScene(Context context, int screenWidth, int screenHeight) {
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
        this.terrain = new Terrain(2000,6,
                V3(0, -0.5f, -3f),
                segWidth,
                segLength,
                0.25f
        );
        terrain.enqueueStructure(new TerrainSpiral(50,PI/2,PI/20));
        terrain.enqueueStructure(new TerrainSpiral(50,-PI/2,PI/20));

        terrain.generateChunks(-1);

        // Build grid and debug side lines
        int rows = Math.max(0, terrain.tileBuilder.getCurrRowCount());
        final int nCols = 6; // matches Terrain creation above
        grid = new FourPoints3D[rows * nCols];
        int idx = 0;
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= nCols; c++) {
                Vector3D[] field = terrain.tileBuilder.getField(r, c); // [TL, TR, BL, BR]
                Vector3D[] cw = new Vector3D[]{field[0], field[1], field[3], field[2]};
                grid[idx++] = new FourPoints3D(cw);
            }
        }
        left = new LineSet3D(terrain.tileBuilder.leftSideToArrayDebug(), new int[][]{}, FColor.CLR(1, 1, 1), FColor.CLR(1, 0, 1));
        right = new LineSet3D(terrain.tileBuilder.rightSideToArrayDebug(), new int[][]{}, FColor.CLR(1, 1, 1), FColor.CLR(0, 0, 1));
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

        for (int i = 0; i < terrain.getTileCount(); ++i) {
            Tile tile = terrain.getTile(i);
            tile.setTileColor(new FColor(1, 0, 0));
            tile.draw(vpRot);
        }
        for (int i = 0; i < terrain.getAddonCount(); ++i) {
            terrain.getAddon(i).draw(vpRot);
        }

        if (grid != null) {
            for (FourPoints3D fp : grid) {
                fp.draw(vpRot);
            }
        }
        if (left != null) left.draw(vpRot);
        if (right != null) right.draw(vpRot);
    }

    @Override
    public void onClose() { }

    @Override
    public void onSwitch() { }

    @Override
    public void onReturn() { }

    @Override
    public void resetGPUResources() { }

    @Override
    protected void onPause() { }

    @Override
    protected void onResume() { }
}


