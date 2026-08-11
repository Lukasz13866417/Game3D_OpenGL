package com.example.game3d_opengl.game.stage.stages.main;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.stage.stage_api.ReleaseButtonGesture;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.text.BitmapFont;
import com.example.game3d_opengl.rendering.text.Button;
import com.example.game3d_opengl.rendering.text.TextRenderer;
import com.example.game3d_opengl.rendering.util3d.rect.Rect;

/**
 * Lightweight start screen. Offers a simple title and a play button that switches to
 * {@link AssetLoadingStage}, then {@link LoadingStage}, then gameplay.
 * This is enough to let developers attach the
 * Android Studio Profiler before heavy rendering starts.
 */
public class MenuStage extends Stage {
    private static final String TITLE = "My Game";
    private static final String SUBTITLE = "Press Play to Start";
    private static final int FONT_PX = 48;

    private boolean firstFrame = true;
    private TextRenderer textRenderer;
    private TextRenderer.TextLabel titleLabel;
    private TextRenderer.TextLabel subtitleLabel;
    private Button playButton;
    private Rect playButtonRectPx;
    private final ReleaseButtonGesture playButtonGesture =
            new ReleaseButtonGesture();
    private float uiScale = 1f;

    public MenuStage(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
    }

    @Override
    protected void onTouchDown(float x, float y) {
        playButtonGesture.begin(playButtonRectPx, x, y);
    }

    @Override
    protected void onTouchUp(float x, float y) {
        if (playButtonGesture.release(x, y)) {
            stageManager.toLoadingThenGameplay();
        }
    }

    @Override
    protected void onTouchMove(float x1, float y1, float x2, float y2) {
        playButtonGesture.move(x2, y2);
    }

    @Override
    protected void onTouchCancelTimed(
            float x, float y, long timeNanos, long sequence) {
        playButtonGesture.cancel();
    }

    @Override
    protected void setupAssets(android.content.res.AssetManager assetManager) {
        // No-op.
    }

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        System.out.println("MENU INIT");
        uiScale = screenHeight / 1080f;
        BitmapFont font = BitmapFont.loadShared(context.getAssets(), FONT_PX);
        textRenderer = new TextRenderer(font);

        float centerX = screenWidth * 0.5f;
        float titleY = screenHeight * 0.24f;
        titleLabel = textRenderer
                .createLabel(TITLE, centerX, titleY, FColor.CLR(1f, 1f, 1f, 1f))
                .setAnchor(TextRenderer.Anchor.CENTER)
                .setScale(1.70f * uiScale);
        subtitleLabel = textRenderer
                .createLabel(
                        SUBTITLE,
                        centerX,
                        titleY + 84f * uiScale,
                        FColor.CLR(0.82f, 0.82f, 0.82f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.CENTER)
                .setScale(0.85f * uiScale);

        float buttonW = Math.min(screenWidth - 120f * uiScale, 360f * uiScale);
        float buttonH = 72f * uiScale;
        float buttonX = 0.5f * (screenWidth - buttonW);
        float buttonY = screenHeight * 0.56f;
        playButtonRectPx = new Rect(buttonX, buttonY, buttonX + buttonW, buttonY + buttonH);
        playButton = new Button.Builder()
                .bboxPx(playButtonRectPx)
                .text("Play")
                .textScale(1.02f * uiScale)
                .autoFitText(20f * uiScale, 10f * uiScale, 0.55f)
                .textRenderer(textRenderer)
                .fillColor(FColor.CLR(0.14f, 0.14f, 0.14f, 1f))
                .outlineColor(FColor.CLR(1f, 1f, 1f, 1f))
                .textColor(FColor.CLR(1f, 1f, 1f, 1f))
                .edgePixels(Math.max(1f, 1.4f * uiScale))
                .build();
    }

    @Override
    public void updateThenDraw(float dt) {
        processTouchEvents();
        if (firstFrame) {
            firstFrame = false;
            android.util.Log.d("MenuStage", "Ready - play button visible");
        }
        if (playButton != null) {
            playButton.draw();
        }
        if (textRenderer != null) {
            textRenderer.draw();
        }
    }

    @Override
    protected void onDeactivated(DeactivationReason reason) {
        playButtonGesture.cancel();
        if (reason == DeactivationReason.COVERED) {
            System.out.println("SWITCHING FROM MENU");
        }
    }

    @Override
    protected void onActivated(ActivationReason reason) {
        if (reason == ActivationReason.REVEALED) {
            System.out.println("RETURNING TO MENU");
        }
    }

    @Override
    public void onPause() {
        playButtonGesture.cancel();
    }

    @Override
    public void onResume() {

    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (playButton != null) {
            playButton.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (textRenderer != null) {
            textRenderer.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (playButton != null) {
            playButton.cleanupGPUResourcesRecursively();
        }
        if (textRenderer != null) {
            textRenderer.cleanupGPUResourcesRecursively();
        }
    }

    @Override
    protected void releaseOwnedResourcesOnDiscard() {
        playButtonGesture.cancel();
        firstFrame = true;
        textRenderer = null;
        titleLabel = null;
        subtitleLabel = null;
        playButton = null;
        playButtonRectPx = null;
        uiScale = 1f;
    }
}
