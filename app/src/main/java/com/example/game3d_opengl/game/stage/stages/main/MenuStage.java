package com.example.game3d_opengl.game.stage.stages.main;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.text.BitmapFont;
import com.example.game3d_opengl.rendering.text.TextRenderer;

/**
 * Extremely lightweight start screen. Shows nothing visually (black screen)
 * and waits for the user to tap anywhere. The first tap switches to
 * {@link LoadingStage}, which then transitions into {@link GameplayStage}.
 * This is enough to let developers attach the
 * Android Studio Profiler before heavy rendering starts.
 */
public class MenuStage extends Stage {

    private boolean firstFrame = true;
    private TextRenderer textRenderer;
    private TextRenderer.TextLabel tapToPlayLabel;
    private float uiScale = 1f;
    private static final int FONT_PX = 48;

    public MenuStage(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
    }

    @Override
    protected void onTouchDown(float x, float y) {
        // Start the game immediately on first touch
        // TODO make an actual menu
        stageManager.toLoadingThenGameplay();
    }

    @Override
    protected void onTouchUp(float x, float y) {
        // No-op
    }

    @Override
    protected void onTouchMove(float x1, float y1, float x2, float y2) {
        // No-op
    }

    @Override
    protected void setupAssets(android.content.res.AssetManager assetManager) {
        // No-op.
    }

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        // Nothing to initialise for the blank menu.
        System.out.println("MENU INIT");
        uiScale = screenHeight / 1080f;
        BitmapFont font = BitmapFont.loadShared(context.getAssets(), FONT_PX);
        textRenderer = new TextRenderer(font);
        float x = screenWidth * 0.5f;
        float y = screenHeight - 70f * uiScale;
        tapToPlayLabel = textRenderer
                .createLabel("Tap to Play", x, y, FColor.CLR(1f, 1f, 1f, 1f))
                .setAnchor(TextRenderer.Anchor.BOTTOM_CENTER)
                .setScale(uiScale);
    }

    @Override
    public void updateThenDraw(float dt) {
        processTouchEvents();
        if (firstFrame) {
            firstFrame = false;
            // Optionally log so dev knows menu loaded
            android.util.Log.d("MenuStage", "Ready - tap to play");
        }
        if (textRenderer != null) {
            textRenderer.draw();
        }
    }

    @Override
    public void onClose() {
    }

    @Override
    public void onSwitch() {
        System.out.println("SWITCHING FROM MENU");
    }

    @Override
    public void onReturn() {
        System.out.println("RETURNING TO MENU");
    }

    @Override
    public void onPause() {

    }

    @Override
    public void onResume() {

    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (textRenderer != null) {
            textRenderer.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (textRenderer != null) {
            textRenderer.cleanupGPUResourcesRecursively();
        }
    }
}
