package com.example.game3d_opengl.game.hud;

import android.content.Context;

import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.progress_bar.ProgressBar;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.text.BitmapFont;
import com.example.game3d_opengl.rendering.text.TextRenderer;
import com.example.game3d_opengl.rendering.util3d.FColor;

public class GameHUD implements GPUResourceOwner {

    public class InfoFromPlayer {
        public float jumpSwipeValue;
        public float jumpSwipeMin;
        public float jumpSwipeMax;
        public float[] jumpSwipeMilestones;
    }

    private final InfoFromPlayer infoFromPlayer;

    private final ProgressBar playerSwipeBar, levelProgressBar;
    private final TextRenderer textRenderer;
    private final float uiScale;
    private static final int FONT_PX = 48;

    private GameHUD(ProgressBar playerSwipeBar,
                    ProgressBar levelProgressBar,
                    TextRenderer textRenderer,
                    float uiScale) {
        this.playerSwipeBar = playerSwipeBar;
        this.levelProgressBar = levelProgressBar;
        this.textRenderer = textRenderer;
        this.uiScale = uiScale;
        this.infoFromPlayer = new InfoFromPlayer();
    }

    public static GameHUD makeGameHUD(Context context, int screenWidth, int screenHeight) {
        float uiScale = screenHeight / 1080f;
        float barX = 24f * uiScale, barY = 24f * uiScale;
        float barW = Math.max(0f, screenWidth - 2f * barX);
        float barH = 60f * uiScale;
        float gap = 6f * uiScale;
        float dentSize = 10f * uiScale;
        float outlinePx = 1f * uiScale;

        ProgressBar playerSwipeBar = new ProgressBar.Builder()
                .range(0f, 1f)
                .bboxPx(barX, barY, barW, barH)
                .fillColor(FColor.CLR(0.2f, 0.7f, 0.2f, 1f))
                .outlineColor(FColor.CLR(1f, 1f, 1f, 1f))
                .milestones(new float[]{0.5f})
                .dentSizePx(dentSize, dentSize)
                .outlinePixels(outlinePx)
                .build();

        ProgressBar levelProgressBar = new ProgressBar.Builder()
                .range(0f, 100f)
                .bboxPx(barX, barY + barH + gap, barW, barH)
                .fillColor(FColor.CLR(0.2f, 0.7f, 0.2f, 1f))
                .outlineColor(FColor.CLR(1f, 1f, 1f, 1f))
                .milestones(new float[]{25f, 50f, 75f})
                .dentSizePx(dentSize, dentSize)
                .outlinePixels(outlinePx)
                .build();

        BitmapFont font = BitmapFont.loadShared(context.getAssets(), FONT_PX);
        TextRenderer textRenderer = new TextRenderer(font);

        return new GameHUD(playerSwipeBar, levelProgressBar, textRenderer, uiScale);

    }

    public void collectInfo(Player.PlayerHUDAPI playerHUDAPI) {
        // all other APIs will be added here as args too
        playerHUDAPI.giveInfoToHUD(infoFromPlayer);
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
        if (textRenderer != null) {
            textRenderer.draw();
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        playerSwipeBar.reloadGPUResourcesRecursivelyOnContextLoss();
        levelProgressBar.reloadGPUResourcesRecursivelyOnContextLoss();
        if (textRenderer != null) {
            textRenderer.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {
        playerSwipeBar.cleanupGPUResourcesRecursivelyOnContextLoss();
        levelProgressBar.cleanupGPUResourcesRecursivelyOnContextLoss();
        if (textRenderer != null) {
            textRenderer.cleanupGPUResourcesRecursivelyOnContextLoss();
        }
    }

}
