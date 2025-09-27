package com.example.game3d_opengl.game.stage.stages.test;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3;

import android.content.Context;
import android.opengl.Matrix;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.terrain_api.main.AdvancedTerrainStructure;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Terrain;
import com.example.game3d_opengl.game.terrain.terrain_structures.TerrainLine;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.rendering.Camera;

public class AddonPlacementTestStage extends Stage {

    private Camera camera;

    private Terrain terrain;

    // Camera position and movement
    private float camX = 0f;
    private float camY = 10f;    // height above ground
    private float camZ = -6.5f;   // initial distance from origin
    private float moveSpeed = 0.00f; // movement per frame
    private float worldRoll = 0f;    // radians
    private static final float ROLL_SENSITIVITY = 0.005f;      // radians per pixel
    private static final float HEIGHT_SENSITIVITY = 0.02f;    // world units per pixel
    private static final float MIN_CAM_Y = 2.0f;
    private static final float MAX_CAM_Y = 80.0f;

    // Gesture handling: lock dominant axis per swipe
    private enum SwipeAxis { NONE, HORIZONTAL, VERTICAL }
    private SwipeAxis activeSwipeAxis = SwipeAxis.NONE;
    private float touchStartX = 0f, touchStartY = 0f;
    private float lastTouchX = 0f, lastTouchY = 0f;

    public AddonPlacementTestStage(MyGLRenderer.StageManager stageManager) {
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
        // initial camera setup (no rotation); roll will be applied via VP matrix during draw
        camera.set(
                camX, camY, camZ,
                camX, 0f, camZ,
                0f, 0f, -1f
        );
        camera.setProjectionAsScreen();
        float segWidth = 3.2f, segLength = 1.4f;
        this.terrain = new Terrain(2000, 6,
                V3(0, -0.5f, -3f),
                segWidth,
                segLength,
                1f
        );
        terrain.enqueueStructure(new AdvancedTerrainStructure(100) {
            @Override
            protected void generateTiles(Terrain.TileBrush brush) {
                for(int i=0;i<tilesToMake;++i){
                    brush.addSegment();
                }
            }

            @Override
            protected void generateAddons(Terrain.AdvancedGridBrush brush, int nRows, int nCols) {
                for(int i=1;i<=nRows;++i){
                    for(int j=1;j<=nCols;++j){
                        Addon[] addons = new Addon[1];
                        for (int k = 0; k < addons.length; ++k) {
                            addons[k] = DeathSpike.createDeathSpike();
                        }
                        brush.reserveVertical(i,j,1,addons);
                    }
                }
            }
        });
        terrain.enqueueStructure(new TerrainLine(100));
        terrain.generateChunks(-1);

    }

    @Override
    public void updateThenDraw(float dt) {
        camZ -= moveSpeed;
        // apply world roll around Z by modifying the VP matrix
        float[] vp = camera.getViewProjectionMatrix();
        float[] rot = new float[16];
        float[] vpRot = new float[16];
        Matrix.setRotateM(rot, 0, (float) Math.toDegrees(worldRoll), 0f, 0f, -1f);
        Matrix.multiplyMM(vpRot, 0, vp, 0, rot, 0); // P*V*Rz

        for (int i = 0; i < terrain.getAddonCount(); ++i) {
            terrain.getAddon(i).updateBeforeDraw(dt);
            terrain.getAddon(i).draw(vpRot);
            terrain.getAddon(i).updateAfterDraw(dt);
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
    public void reloadGPUResourcesRecursivelyOnContextLoss() {

    }
    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {}
    @Override
    protected void onPause() {

    }

    @Override
    protected void onResume() {

    }
}
