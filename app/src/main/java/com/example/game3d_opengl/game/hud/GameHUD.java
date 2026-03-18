package com.example.game3d_opengl.game.hud;

import android.content.Context;

import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.progress_bar.ProgressBar;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.text.BitmapFont;
import com.example.game3d_opengl.rendering.text.Button;
import com.example.game3d_opengl.rendering.text.TextRenderer;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.rect.Rect;

public class GameHUD implements GPUResourceOwner {

    public class InfoFromPlayer {
        public float jumpSwipeValue;
        public float jumpSwipeMin;
        public float jumpSwipeMax;
        public float[] jumpSwipeMilestones;
    }

    private final InfoFromPlayer infoFromPlayer;

    private final ProgressBar playerSwipeBar;
    private final ProgressBar levelProgressBar;
    private final Button pauseBarLeftDefault;
    private final Button pauseBarRightDefault;
    private Button pauseBarLeftThemed;
    private Button pauseBarRightThemed;
    private final Rect pauseTouchRectPx;
    private final Rect pauseLeftRectPx;
    private final Rect pauseRightRectPx;
    private final float pauseEdgePx;
    private final TextRenderer textRenderer;
    private final float uiScale;
    private FColor currentThemeColor;
    private boolean paused;
    private static final int FONT_PX = 48;

    private GameHUD(ProgressBar playerSwipeBar,
                    ProgressBar levelProgressBar,
                    Button pauseBarLeftDefault,
                    Button pauseBarRightDefault,
                    Button pauseBarLeftThemed,
                    Button pauseBarRightThemed,
                    Rect pauseTouchRectPx,
                    Rect pauseLeftRectPx,
                    Rect pauseRightRectPx,
                    float pauseEdgePx,
                    TextRenderer textRenderer,
                    float uiScale) {
        this.playerSwipeBar = playerSwipeBar;
        this.levelProgressBar = levelProgressBar;
        this.pauseBarLeftDefault = pauseBarLeftDefault;
        this.pauseBarRightDefault = pauseBarRightDefault;
        this.pauseBarLeftThemed = pauseBarLeftThemed;
        this.pauseBarRightThemed = pauseBarRightThemed;
        this.pauseTouchRectPx = pauseTouchRectPx;
        this.pauseLeftRectPx = pauseLeftRectPx;
        this.pauseRightRectPx = pauseRightRectPx;
        this.pauseEdgePx = pauseEdgePx;
        this.textRenderer = textRenderer;
        this.uiScale = uiScale;
        this.infoFromPlayer = new InfoFromPlayer();
        this.currentThemeColor = FColor.CLR(0f, 0f, 0f, 1f);
        this.paused = false;
    }

    public static GameHUD makeGameHUD(Context context, int screenWidth, int screenHeight) {
        float uiScale = screenHeight / 1080f;
        float barX = 24f * uiScale, barY = 24f * uiScale;
        float fullBarW = Math.max(0f, screenWidth - 2f * barX);
        float barH = 60f * uiScale;
        float gap = 6f * uiScale;
        float dentSize = 10f * uiScale;
        float outlinePx = 1f * uiScale;
        float pauseGap = 14f * uiScale;
        float pauseButtonW = Math.min(barH, fullBarW * 0.24f);
        pauseButtonW = Math.max(32f * uiScale, pauseButtonW);
        float pauseButtonX = screenWidth - barX - pauseButtonW;
        float swipeBarW = Math.max(32f * uiScale, pauseButtonX - pauseGap - barX);

        ProgressBar playerSwipeBar = new ProgressBar.Builder()
                .range(0f, 1f)
                .bboxPx(barX, barY, swipeBarW, barH)
                .fillColor(FColor.CLR(0.2f, 0.7f, 0.2f, 1f))
                .outlineColor(FColor.CLR(1f, 1f, 1f, 1f))
                .milestones(new float[]{0.5f})
                .dentSizePx(dentSize, dentSize)
                .outlinePixels(outlinePx)
                .build();

        ProgressBar levelProgressBar = new ProgressBar.Builder()
                .range(0f, 100f)
                .bboxPx(barX, barY + barH + gap, fullBarW, barH)
                .fillColor(FColor.CLR(0.2f, 0.7f, 0.2f, 1f))
                .outlineColor(FColor.CLR(1f, 1f, 1f, 1f))
                .milestones(new float[]{25f, 50f, 75f})
                .dentSizePx(dentSize, dentSize)
                .outlinePixels(outlinePx)
                .build();

        BitmapFont font = BitmapFont.loadShared(context.getAssets(), FONT_PX);
        TextRenderer textRenderer = new TextRenderer(font);

        float iconPadX = pauseButtonW * 0.18f;
        float iconPadY = barH * 0.18f;
        float iconGap = pauseButtonW * 0.14f;
        float barInnerW = Math.max(4f * uiScale, (pauseButtonW - 2f * iconPadX - iconGap) * 0.5f);
        float barInnerH = Math.max(4f * uiScale, barH - 2f * iconPadY);
        float leftBarX = pauseButtonX + iconPadX;
        float leftBarY = barY + iconPadY;
        float rightBarX = leftBarX + barInnerW + iconGap;

        float pauseEdgePx = Math.max(1f, outlinePx);
        Rect pauseLeftRectPx = new Rect(leftBarX, leftBarY, leftBarX + barInnerW, leftBarY + barInnerH);
        Rect pauseRightRectPx = new Rect(rightBarX, leftBarY, rightBarX + barInnerW, leftBarY + barInnerH);
        Button pauseBarLeftDefault = buildPauseBar(textRenderer, pauseLeftRectPx, pauseEdgePx, FColor.CLR(0f, 0f, 0f, 1f));
        Button pauseBarRightDefault = buildPauseBar(textRenderer, pauseRightRectPx, pauseEdgePx, FColor.CLR(0f, 0f, 0f, 1f));
        // Themed version is initially black; GameplayStage updates theme each frame.
        Button pauseBarLeftThemed = buildPauseBar(textRenderer, pauseLeftRectPx, pauseEdgePx, FColor.CLR(0f, 0f, 0f, 1f));
        Button pauseBarRightThemed = buildPauseBar(textRenderer, pauseRightRectPx, pauseEdgePx, FColor.CLR(0f, 0f, 0f, 1f));

        Rect pauseTouchRectPx = new Rect(
                pauseButtonX,
                barY,
                pauseButtonX + pauseButtonW,
                barY + barH
        );

        return new GameHUD(
                playerSwipeBar,
                levelProgressBar,
                pauseBarLeftDefault,
                pauseBarRightDefault,
                pauseBarLeftThemed,
                pauseBarRightThemed,
                pauseTouchRectPx,
                pauseLeftRectPx,
                pauseRightRectPx,
                pauseEdgePx,
                textRenderer,
                uiScale
        );

    }

    public void collectInfo(Player.PlayerHUDAPI playerHUDAPI) {
        // all other APIs will be added here as args too
        playerHUDAPI.giveInfoToHUD(infoFromPlayer);
    }

    public void setThemeColor(FColor themeColor) {
        if (themeColor == null) {
            return;
        }
        if (sameColor(currentThemeColor, themeColor)) {
            return;
        }
        currentThemeColor = FColor.CLR(themeColor.r(), themeColor.g(), themeColor.b(), themeColor.a());
        rebuildThemedPauseBars();
    }

    public boolean handleTouchDown(float xPx, float yPx) {
        if (pauseTouchRectPx != null && pauseTouchRectPx.containsPoint(xPx, yPx)) {
            paused = !paused;
            return true;
        }
        return false;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isUnpaused() {
        return !paused;
    }

    public void draw() {
        if (infoFromPlayer.jumpSwipeMax > infoFromPlayer.jumpSwipeMin) {
            playerSwipeBar.setRange(infoFromPlayer.jumpSwipeMin, infoFromPlayer.jumpSwipeMax);
        }
        if (infoFromPlayer.jumpSwipeMilestones != null) {
            playerSwipeBar.setMilestones(infoFromPlayer.jumpSwipeMilestones);
        }
        playerSwipeBar.draw(infoFromPlayer.jumpSwipeValue);
        levelProgressBar.draw(0);
        if (paused) {
            if (pauseBarLeftThemed != null) {
                pauseBarLeftThemed.draw();
            }
            if (pauseBarRightThemed != null) {
                pauseBarRightThemed.draw();
            }
        } else {
            if (pauseBarLeftDefault != null) {
                pauseBarLeftDefault.draw();
            }
            if (pauseBarRightDefault != null) {
                pauseBarRightDefault.draw();
            }
        }
        if (textRenderer != null) {
            textRenderer.draw();
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        playerSwipeBar.reloadGPUResourcesRecursivelyOnContextLoss();
        levelProgressBar.reloadGPUResourcesRecursivelyOnContextLoss();
        if (pauseBarLeftDefault != null) {
            pauseBarLeftDefault.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (pauseBarRightDefault != null) {
            pauseBarRightDefault.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (pauseBarLeftThemed != null) {
            pauseBarLeftThemed.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (pauseBarRightThemed != null) {
            pauseBarRightThemed.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (textRenderer != null) {
            textRenderer.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        playerSwipeBar.cleanupGPUResourcesRecursively();
        levelProgressBar.cleanupGPUResourcesRecursively();
        if (pauseBarLeftDefault != null) {
            pauseBarLeftDefault.cleanupGPUResourcesRecursively();
        }
        if (pauseBarRightDefault != null) {
            pauseBarRightDefault.cleanupGPUResourcesRecursively();
        }
        if (pauseBarLeftThemed != null) {
            pauseBarLeftThemed.cleanupGPUResourcesRecursively();
        }
        if (pauseBarRightThemed != null) {
            pauseBarRightThemed.cleanupGPUResourcesRecursively();
        }
        if (textRenderer != null) {
            textRenderer.cleanupGPUResourcesRecursively();
        }
    }

    private void rebuildThemedPauseBars() {
        if (textRenderer == null || pauseLeftRectPx == null || pauseRightRectPx == null) {
            return;
        }
        if (pauseBarLeftThemed != null) {
            pauseBarLeftThemed.cleanupGPUResourcesRecursively();
        }
        if (pauseBarRightThemed != null) {
            pauseBarRightThemed.cleanupGPUResourcesRecursively();
        }
        pauseBarLeftThemed = buildPauseBar(textRenderer, pauseLeftRectPx, pauseEdgePx, currentThemeColor);
        pauseBarRightThemed = buildPauseBar(textRenderer, pauseRightRectPx, pauseEdgePx, currentThemeColor);
    }

    private static Button buildPauseBar(TextRenderer textRenderer, Rect rectPx, float edgePx, FColor fillColor) {
        return new Button.Builder()
                .bboxPx(rectPx)
                .text("")
                .textScale(0f)
                .textRenderer(textRenderer)
                .fillColor(fillColor)
                .outlineColor(FColor.CLR(1f, 1f, 1f, 1f))
                .textColor(FColor.CLR(1f, 1f, 1f, 1f))
                .edgePixels(edgePx)
                .build();
    }

    private static boolean sameColor(FColor a, FColor b) {
        if (a == null || b == null) {
            return false;
        }
        return Math.abs(a.r() - b.r()) < 1e-6f
                && Math.abs(a.g() - b.g()) < 1e-6f
                && Math.abs(a.b() - b.b()) < 1e-6f
                && Math.abs(a.a() - b.a()) < 1e-6f;
    }

}
