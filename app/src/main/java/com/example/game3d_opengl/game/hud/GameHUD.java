package com.example.game3d_opengl.game.hud;

import android.content.Context;

import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.simulation.SimulationFrameSnapshot;
import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.game.progress_bar.ProgressBar;
import com.example.game3d_opengl.game.stage.stage_api.ReleaseButtonGesture;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.icon.GearIcon;
import com.example.game3d_opengl.rendering.text.BitmapFont;
import com.example.game3d_opengl.rendering.text.Button;
import com.example.game3d_opengl.rendering.text.TextRenderer;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.rect.Rect;

public class GameHUD implements GPUResourceOwner {
    public enum HudAction {
        NONE,
        TOGGLE_PAUSE,
        OPEN_SETTINGS
    }

    public class InfoFromPlayer {
        public float jumpSwipeValue;
        public float jumpSwipeMin;
        public float jumpSwipeMax;
        public float[] jumpSwipeMilestones;
        public int airJumpCharges;
    }

    private final InfoFromPlayer infoFromPlayer;

    private final ProgressBar playerSwipeBar;
    private final ProgressBar levelProgressBar;
    private final Button pauseBarLeftDefault;
    private final Button pauseBarRightDefault;
    private Button pauseBarLeftThemed;
    private Button pauseBarRightThemed;
    private final GearIcon settingsIcon;
    private final Rect pauseTouchRectPx;
    private final Rect settingsTouchRectPx;
    private final Rect pauseLeftRectPx;
    private final Rect pauseRightRectPx;
    private final float pauseEdgePx;
    private final TextRenderer textRenderer;
    private final TextRenderer.TextLabel airJumpChargeLabel;
    private final float uiScale;
    private final FColor currentThemeColor;
    private boolean themeInitialized;
    private boolean paused;
    private int displayedAirJumpCharges = Integer.MIN_VALUE;
    private final float[] canonicalJumpMilestones = new float[1];
    private final ReleaseButtonGesture buttonGesture =
            new ReleaseButtonGesture();
    private HudAction pendingButtonAction = HudAction.NONE;
    private static final int FONT_PX = 48;
    private static final float REFERENCE_SCREEN_HEIGHT_PX = 1080f;
    private static final float BASE_BAR_HEIGHT_PX = 40f;
    private static final float HUD_ELEMENT_SIZE_MULTIPLIER = 1.07f;

    private GameHUD(ProgressBar playerSwipeBar,
                    ProgressBar levelProgressBar,
                    Button pauseBarLeftDefault,
                    Button pauseBarRightDefault,
                    Button pauseBarLeftThemed,
                    Button pauseBarRightThemed,
                    GearIcon settingsIcon,
                    Rect pauseTouchRectPx,
                    Rect settingsTouchRectPx,
                    Rect pauseLeftRectPx,
                    Rect pauseRightRectPx,
                    float pauseEdgePx,
                    TextRenderer textRenderer,
                    TextRenderer.TextLabel airJumpChargeLabel,
                    float uiScale) {
        this.playerSwipeBar = playerSwipeBar;
        this.levelProgressBar = levelProgressBar;
        this.pauseBarLeftDefault = pauseBarLeftDefault;
        this.pauseBarRightDefault = pauseBarRightDefault;
        this.pauseBarLeftThemed = pauseBarLeftThemed;
        this.pauseBarRightThemed = pauseBarRightThemed;
        this.settingsIcon = settingsIcon;
        this.pauseTouchRectPx = pauseTouchRectPx;
        this.settingsTouchRectPx = settingsTouchRectPx;
        this.pauseLeftRectPx = pauseLeftRectPx;
        this.pauseRightRectPx = pauseRightRectPx;
        this.pauseEdgePx = pauseEdgePx;
        this.textRenderer = textRenderer;
        this.airJumpChargeLabel = airJumpChargeLabel;
        this.uiScale = uiScale;
        this.infoFromPlayer = new InfoFromPlayer();
        this.currentThemeColor = FColor.CLR(0f, 0f, 0f, 1f);
        this.themeInitialized = false;
        this.paused = false;
    }

    public static GameHUD makeGameHUD(Context context, int screenWidth, int screenHeight) {
        float uiScale = screenHeight / REFERENCE_SCREEN_HEIGHT_PX;
        float barX = 12f * uiScale, barY = 24f * uiScale;
        float barH = barHeightPx(screenHeight);
        float gap = 5f * uiScale;
        float dentSize = 10f * uiScale;
        float outlinePx = 1f;
        float pauseButtonW = iconButtonSizePx(screenHeight);
        float pauseButtonX = screenWidth - barX - pauseButtonW;
        float phaseBarY = barY + barH + gap;
        float settingsButtonW = pauseButtonW;
        float settingsButtonH = pauseButtonW;
        float settingsButtonX = pauseButtonX;
        float settingsButtonY = phaseBarY;
        BitmapFont font = BitmapFont.loadShared(context.getAssets(), FONT_PX);
        TextRenderer textRenderer = new TextRenderer(font);
        TextRenderer.TextLabel airJumpChargeLabel = textRenderer
                .createLabel(
                        "Air jumps: 0",
                        barX,
                        phaseBarY + barH + 5f * uiScale,
                        FColor.CLR(1f, 1f, 1f, 1f))
                .setScale(0.55f * uiScale);

        float iconPadX = pauseButtonW * 0.18f;
        float iconGap = pauseButtonW * 0.14f;
        float barInnerW = Math.max(4f * uiScale, (pauseButtonW - 2f * iconPadX - iconGap) * 0.5f);
        float barInnerH = barH;
        float pauseGlyphVisualSize = 2f * barInnerW + iconGap;
        float leftBarY = barY;
        float rightBarX = screenWidth - barX - barInnerW;
        float leftBarX = rightBarX - iconGap - barInnerW;
        float swipeBarW = hudBarWidthPx(screenWidth, screenHeight);
        Rect settingsTouchRectPx = new Rect(
                settingsButtonX,
                settingsButtonY,
                settingsButtonX + settingsButtonW,
                settingsButtonY + settingsButtonH
        );
        float settingsIconLeft = settingsTouchRectPx.x2 - pauseGlyphVisualSize;
        float settingsIconTop = settingsTouchRectPx.y1;
        Rect settingsIconRectPx = new Rect(
                settingsIconLeft,
                settingsIconTop,
                settingsIconLeft + pauseGlyphVisualSize,
                settingsIconTop + pauseGlyphVisualSize
        );
        float phaseBarW = hudBarWidthPx(screenWidth, screenHeight);

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
                .bboxPx(barX, phaseBarY, phaseBarW, barH)
                .fillColor(FColor.CLR(0.2f, 0.7f, 0.2f, 1f))
                .outlineColor(FColor.CLR(1f, 1f, 1f, 1f))
                .milestones(new float[]{25f, 50f, 75f})
                .dentSizePx(dentSize, dentSize)
                .outlinePixels(outlinePx)
                .build();

        float pauseEdgePx = Math.max(1f, outlinePx);
        Rect pauseLeftRectPx = new Rect(leftBarX, leftBarY, leftBarX + barInnerW, leftBarY + barInnerH);
        Rect pauseRightRectPx = new Rect(rightBarX, leftBarY, rightBarX + barInnerW, leftBarY + barInnerH);
        Button pauseBarLeftDefault = buildPauseBar(textRenderer, pauseLeftRectPx, pauseEdgePx, FColor.CLR(0f, 0f, 0f, 1f));
        Button pauseBarRightDefault = buildPauseBar(textRenderer, pauseRightRectPx, pauseEdgePx, FColor.CLR(0f, 0f, 0f, 1f));
        // Themed version is initially black; GameplayStage updates theme each frame.
        Button pauseBarLeftThemed = buildPauseBar(textRenderer, pauseLeftRectPx, pauseEdgePx, FColor.CLR(0f, 0f, 0f, 1f));
        Button pauseBarRightThemed = buildPauseBar(textRenderer, pauseRightRectPx, pauseEdgePx, FColor.CLR(0f, 0f, 0f, 1f));
        GearIcon settingsIcon = GearIcon.createPx(
                settingsIconRectPx,
                FColor.CLR(1f, 1f, 1f, 1f),
                pauseEdgePx
        );

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
                settingsIcon,
                pauseTouchRectPx,
                settingsTouchRectPx,
                pauseLeftRectPx,
                pauseRightRectPx,
                pauseEdgePx,
                textRenderer,
                airJumpChargeLabel,
                uiScale
        );

    }

    public void collectInfo(Player.PlayerHUDAPI playerHUDAPI) {
        // all other APIs will be added here as args too
        playerHUDAPI.giveInfoToHUD(infoFromPlayer);
    }

    /** Reads HUD gameplay values from the same immutable frame used by presentation. */
    public void collectInfo(
            SimulationFrameSnapshot frame, PhysicsConfig physicsConfig) {
        if (frame == null || frame.player == null || physicsConfig == null) {
            return;
        }
        canonicalJumpMilestones[0] =
                (float) physicsConfig.jumpChargeThreshold;
        infoFromPlayer.jumpSwipeMin = 0f;
        infoFromPlayer.jumpSwipeMax = 1f;
        infoFromPlayer.jumpSwipeMilestones = canonicalJumpMilestones;
        infoFromPlayer.jumpSwipeValue =
                (float) frame.player.gestureCharge;
        infoFromPlayer.airJumpCharges = frame.player.airJumpCharges;
    }

    public void setThemeColor(FColor themeColor) {
        if (themeColor == null) {
            return;
        }
        if (themeInitialized && sameColor(currentThemeColor, themeColor)) {
            return;
        }
        // Gameplay mutates its theme object in place. Keep our own stable object so the next
        // comparison detects those changes without allocating one FColor per rendered frame.
        copyColor(currentThemeColor, themeColor);
        themeInitialized = true;
        if (playerSwipeBar != null) {
            playerSwipeBar.setFillColor(currentThemeColor);
        }
        if (pauseBarLeftThemed != null) {
            pauseBarLeftThemed.setFillColor(currentThemeColor);
        }
        if (pauseBarRightThemed != null) {
            pauseBarRightThemed.setFillColor(currentThemeColor);
        }
    }

    public void setUpcomingPhasePreview(float previewWindowMs, float[] upcomingMilestonesMs) {
        setUpcomingPhasePreview(
                previewWindowMs,
                upcomingMilestonesMs,
                upcomingMilestonesMs != null ? upcomingMilestonesMs.length : 0);
    }

    public void setUpcomingPhasePreview(
            float previewWindowMs,
            float[] upcomingMilestonesMs,
            int milestoneCount) {
        float nextWindow = Math.max(1f, previewWindowMs);
        levelProgressBar.setRange(0f, nextWindow);
        levelProgressBar.setMilestones(upcomingMilestonesMs, milestoneCount);
    }

    /** Captures a HUD button gesture without activating its action. */
    public boolean handleTouchDown(float xPx, float yPx) {
        cancelTouchGesture();
        if (buttonGesture.begin(settingsTouchRectPx, xPx, yPx)) {
            pendingButtonAction = HudAction.OPEN_SETTINGS;
            return true;
        }
        if (buttonGesture.begin(pauseTouchRectPx, xPx, yPx)) {
            pendingButtonAction = HudAction.TOGGLE_PAUSE;
            return true;
        }
        return false;
    }

    /** Keeps ownership but permanently disarms a HUD press dragged outside its button. */
    public void handleTouchMove(float xPx, float yPx) {
        buttonGesture.move(xPx, yPx);
    }

    /** Activates a captured HUD button only on its matching in-bounds release. */
    public HudAction handleTouchUp(float xPx, float yPx) {
        HudAction releasedAction = pendingButtonAction;
        pendingButtonAction = HudAction.NONE;
        if (!buttonGesture.release(xPx, yPx)) {
            return HudAction.NONE;
        }
        if (releasedAction == HudAction.TOGGLE_PAUSE) {
            paused = !paused;
        }
        return releasedAction;
    }

    public void cancelTouchGesture() {
        buttonGesture.cancel();
        pendingButtonAction = HudAction.NONE;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isUnpaused() {
        return !paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
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
        if (settingsIcon != null) {
            settingsIcon.draw();
        }
        if (airJumpChargeLabel != null) {
            int airJumpCharges = Math.max(0, infoFromPlayer.airJumpCharges);
            if (displayedAirJumpCharges != airJumpCharges) {
                displayedAirJumpCharges = airJumpCharges;
                airJumpChargeLabel.setText("Air jumps: " + airJumpCharges);
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
        if (settingsIcon != null) {
            settingsIcon.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (textRenderer != null) {
            textRenderer.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        cancelTouchGesture();
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
        if (settingsIcon != null) {
            settingsIcon.cleanupGPUResourcesRecursively();
        }
        if (textRenderer != null) {
            textRenderer.cleanupGPUResourcesRecursively();
        }
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

    static float barHeightPx(int screenHeight) {
        return BASE_BAR_HEIGHT_PX * HUD_ELEMENT_SIZE_MULTIPLIER
                * screenHeight / REFERENCE_SCREEN_HEIGHT_PX;
    }

    static float iconButtonSizePx(int screenHeight) {
        return 1.5f * barHeightPx(screenHeight);
    }

    static float hudBarWidthPx(int screenWidth, int screenHeight) {
        float uiScale = screenHeight / REFERENCE_SCREEN_HEIGHT_PX;
        float barX = 12f * uiScale;
        float iconButtonSize = iconButtonSizePx(screenHeight);
        float iconVisualSize = iconButtonSize * (1f - 2f * 0.18f);
        float iconLeft = screenWidth - barX - iconVisualSize;
        return Math.max(32f * uiScale, iconLeft - 2f * barX);
    }

    FColor currentThemeColorForTest() {
        return currentThemeColor;
    }

    private static void copyColor(FColor destination, FColor source) {
        destination.rgba[0] = source.r();
        destination.rgba[1] = source.g();
        destination.rgba[2] = source.b();
        destination.rgba[3] = source.a();
    }

}
