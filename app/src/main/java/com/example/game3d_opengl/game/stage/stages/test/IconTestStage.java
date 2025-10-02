package com.example.game3d_opengl.game.stage.stages.test;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;
import com.example.game3d_opengl.game.ui.icons.PotionIcon;
import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.icon.RectOverlay;
import com.example.game3d_opengl.rendering.icon.SpinningIcon;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.rect.Rect;

public class IconTestStage extends Stage {

    private PotionIcon icon;
    private SpinningIcon spinningIcon;
    private RectOverlay rectOverlay;

    public IconTestStage(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
    }

    @Override
    public void onTouchDown(float x, float y) {}

    @Override
    public void onTouchUp(float x, float y) {}

    @Override
    public void onTouchMove(float x1, float y1, float x2, float y2) {}

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        // Mesh3DWireframe needs the global viewport to compute pixel thickness in the shader.
        Camera.setGlobalScreenSize(screenWidth, screenHeight);
        // Load once per app run; throws if called twice
        PotionIcon.LOAD_POTION_ICON_ASSETS(context.getAssets());

        // Place icon at bottom-left in clip space (e.g., -0.8..-0.2 x, -0.9..-0.3 y)
        Rect placement = new Rect(0.2f, -0.9f, 0.8f, -0.3f);

        rectOverlay = new RectOverlay.Builder()
                .placementRect(placement)
                .edgeColor(FColor.CLR(1f, 0f, 0f, 1f))
                .edgePixels(2f)
                .build();

        // Build a spinning icon with same geometry/colors as the potion
        // Reuse the already loaded potion verts/faces/colors via PotionIconBuilder
        spinningIcon = new SpinningIcon.SpinningBuilder()
                .verts(PotionIcon.getLoadedVerts())
                .faces(PotionIcon.getLoadedFaces())
                .fillColor(Potion.POTION_FILL_COLOR)
                .edgeColor(Potion.POTION_EDGE_COLOR)
                .edgePixels(Potion.POTION_MODEL_LINE_THICKNESS)
                .marginPixels(2f, 2f)
                .spinRateDegPerSec(60f)
                .placementRect(placement)
                .build();
    }

    @Override
    public void updateThenDraw(float dt) {
        rectOverlay.draw();
        spinningIcon.draw();
    }

    @Override
    public void onClose() {}

    @Override
    public void onSwitch() {}

    @Override
    public void onReturn() {}

    @Override
    public void onPause() {}

    @Override
    public void onResume() {}

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        rectOverlay.reloadGPUResourcesRecursivelyOnContextLoss();
        icon.reloadGPUResourcesRecursivelyOnContextLoss();
        if (spinningIcon != null) spinningIcon.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {
          rectOverlay.cleanupGPUResourcesRecursivelyOnContextLoss();
          icon.cleanupGPUResourcesRecursivelyOnContextLoss();
          if (spinningIcon != null) spinningIcon.cleanupGPUResourcesRecursivelyOnContextLoss();
    }
}


