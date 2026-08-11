package com.example.game3d_opengl.game.stage.stages.main;

import android.content.Context;
import android.content.res.AssetManager;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.settings.SlowFrameStats;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.rendering.text.BitmapFont;
import com.example.game3d_opengl.rendering.text.TextRenderer;
import com.example.game3d_opengl.rendering.util3d.FColor;

public final class AssetLoadingStage extends Stage {
    private static final int FONT_PX = 54;
    private static final String LOADING_TEXT = "Loading Assets";
    private static final int TERRAIN_PREPARATION_CHUNK_BUDGET = 64;

    private TextRenderer textRenderer;
    private TextRenderer.TextLabel loadingLabel;
    private float uiScale = 1f;
    private boolean requestedVisualLoadingStage = false;
    private AssetManager assetManager;
    private PreparedGameplaySession preparedGameplaySession;

    public AssetLoadingStage(MyGLRenderer.StageManager stageManager) {
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
    protected void setupAssets(AssetManager assetManager) {
        // No-op.
    }

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        assetManager = context.getAssets();
        uiScale = screenHeight / 1080f;
        requestedVisualLoadingStage = false;
        preparedGameplaySession = null;
        GameplayDiskAssetPreloader.startIfNeeded(assetManager);

        BitmapFont font = BitmapFont.loadShared(assetManager, FONT_PX);
        textRenderer = new TextRenderer(font);
        loadingLabel = textRenderer.createLabel(
                        LOADING_TEXT,
                        screenWidth * 0.5f,
                        screenHeight * 0.5f,
                        FColor.CLR(1f, 1f, 1f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.CENTER)
                .setScale(uiScale);
    }

    @Override
    public void updateThenDraw(float dt) {
        processTouchEvents();
        GameplayDiskAssetPreloader.throwIfFailed();
        if (textRenderer != null) {
            textRenderer.draw();
        }
        if (requestedVisualLoadingStage) {
            return;
        }
        if (!GameplayDiskAssetPreloader.isDiskReady()) {
            return;
        }
        if (!GameplayDiskAssetPreloader.isGpuWarmupComplete()) {
            GameplayDiskAssetPreloader.warmUpOneGpuAsset(assetManager);
            return;
        }
        if (preparedGameplaySession == null) {
            preparedGameplaySession = PreparedGameplaySession.createInitialSession();
        }
        if (!preparedGameplaySession.isSpawnPlayableReady()) {
            SlowFrameStats.markTerrainGenerating();
            preparedGameplaySession.generateTerrainChunks(TERRAIN_PREPARATION_CHUNK_BUDGET);
            return;
        }
        preparedGameplaySession.beginRuntimePreparationAsync();
        if (!preparedGameplaySession.isRuntimePreparedReady()) {
            return;
        }
        requestedVisualLoadingStage = true;
        PreparedGameplaySession session = preparedGameplaySession;
        preparedGameplaySession = null;
        stageManager.toVisualLoadingThenGameplay(session);
    }

    @Override
    protected void onDeactivated(DeactivationReason reason) {
        // No-op.
    }

    @Override
    protected void onActivated(ActivationReason reason) {
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
        if (textRenderer != null) {
            textRenderer.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (preparedGameplaySession != null) {
            preparedGameplaySession.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (textRenderer != null) {
            textRenderer.cleanupGPUResourcesRecursively();
        }
        if (preparedGameplaySession != null) {
            preparedGameplaySession.cleanupGPUResourcesRecursively();
        }
    }

    @Override
    protected void releaseOwnedResourcesOnDiscard() {
        textRenderer = null;
        loadingLabel = null;
        uiScale = 1f;
        requestedVisualLoadingStage = false;
        assetManager = null;
        preparedGameplaySession = null;
    }
}
