package com.example.game3d_opengl.game.stage.stages.main;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.rendering.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.mesh.MVPDrawArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Minimal transition stage shown before gameplay starts/restarts.
 * The bar covers the full screen height and shrinks in width by:
 *   w(t+1) = 0.95 * w(t) - 3 px
 */
public final class LoadingStage extends Stage {
    private static final float WIDTH_MULTIPLIER_PER_STEP = 0.95f;
    private static final float WIDTH_DECAY_PX_PER_STEP = 3f;
    private static final float STEP_MS = 16.6667f;
    private static final float GAMEPLAY_START_CUTOFF_WIDTH_FRACTION = 0.24f;
    private static final float GAMEPLAY_START_CUTOFF_MIN_PX = 96f;
    private static final FColor BAR_COLOR = FColor.CLR(1f, 1f, 1f, 1f);

    private static final Vector3D[] UNIT_QUAD = new Vector3D[]{
            new Vector3D(0f, 0f, 0f),
            new Vector3D(1f, 0f, 0f),
            new Vector3D(1f, 1f, 0f),
            new Vector3D(0f, 1f, 0f)
    };
    private static final int[][] UNIT_FACE = new int[][]{
            new int[]{0, 1, 2, 3}
    };

    private Mesh3DInfill barMesh;
    private MVPDrawArgs barArgs;
    private int screenW = 1;
    private float barWidthPx = 0f;
    private float gameplayStartCutoffPx = 0f;
    private float stepAccumulatorMs = 0f;
    private boolean requestedGameplayStart = false;

    public LoadingStage(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
    }

    @Override
    protected void onTouchDown(float x, float y) {
        // No-op.
    }

    @Override
    protected void onTouchUp(float x, float y) {
        // No-op.
    }

    @Override
    protected void onTouchMove(float x1, float y1, float x2, float y2) {
        // No-op.
    }

    @Override
    protected void setupAssets(android.content.res.AssetManager assetManager) {
        // No-op.
    }

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        screenW = Math.max(1, screenWidth);
        barWidthPx = (float) screenW;
        gameplayStartCutoffPx = Math.min(
                0.90f * screenW,
                Math.max(GAMEPLAY_START_CUTOFF_MIN_PX, GAMEPLAY_START_CUTOFF_WIDTH_FRACTION * screenW)
        );
        stepAccumulatorMs = 0f;
        requestedGameplayStart = false;

        barMesh = new Mesh3DInfill.Builder()
                .verts(UNIT_QUAD)
                .faces(UNIT_FACE)
                .fillColor(BAR_COLOR)
                .buildObject();

        barArgs = new MVPDrawArgs(new float[16]);
        updateBarTransform();
    }

    @Override
    public void updateThenDraw(float dt) {
        processTouchEvents();
        if (barMesh == null || barArgs == null) {
            return;
        }

        stepAccumulatorMs += Math.max(0f, dt);
        while (stepAccumulatorMs >= STEP_MS && barWidthPx > 0f) {
            barWidthPx = WIDTH_MULTIPLIER_PER_STEP * barWidthPx - WIDTH_DECAY_PX_PER_STEP;
            if (barWidthPx < 0f) {
                barWidthPx = 0f;
            }
            stepAccumulatorMs -= STEP_MS;
        }
        updateBarTransform();

        if (barWidthPx > 0f) {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            barMesh.draw(barArgs);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        }

        if (!requestedGameplayStart && barWidthPx <= gameplayStartCutoffPx) {
            requestedGameplayStart = true;
            stageManager.toGameplay();
        }
    }

    @Override
    public void onClose() {
        if (barMesh != null) {
            barMesh.cleanupGPUResourcesRecursively();
        }
    }

    @Override
    public void onSwitch() {
        onClose();
    }

    @Override
    public void onReturn() {
        // No-op.
    }

    @Override
    protected void onPause() {
        // No-op.
    }

    @Override
    protected void onResume() {
        // No-op.
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (barMesh != null) {
            barMesh.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (barMesh != null) {
            barMesh.cleanupGPUResourcesRecursively();
        }
    }

    private void updateBarTransform() {
        float x1 = -1f;
        float x2 = -1f + 2f * (Math.max(0f, barWidthPx) / (float) screenW);
        float y1 = -1f;
        float y2 = 1f;
        barArgs.setMvp(buildPlacementMatrix(x1, y1, x2, y2));
    }

    private static float[] buildPlacementMatrix(float x1, float y1, float x2, float y2) {
        float sx = Math.max(1e-6f, x2 - x1);
        float sy = Math.max(1e-6f, y2 - y1);
        float[] m = new float[16];
        Matrix.setIdentityM(m, 0);
        Matrix.translateM(m, 0, x1, y1, 0f);
        Matrix.scaleM(m, 0, sx, sy, 0f);
        return m;
    }
}
