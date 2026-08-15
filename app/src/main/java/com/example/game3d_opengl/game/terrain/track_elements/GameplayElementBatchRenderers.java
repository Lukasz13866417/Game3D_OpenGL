package com.example.game3d_opengl.game.terrain.track_elements;

import android.content.res.AssetManager;

import com.example.game3d_opengl.game.terrain.track_elements.potion.PotionBatchInstance;
import com.example.game3d_opengl.game.terrain.track_elements.potion.PotionBatchRenderer;
import com.example.game3d_opengl.game.terrain.track_elements.potion.PotionRenderResources;
import com.example.game3d_opengl.game.terrain.track_elements.spike.SpikeBatchInstance;
import com.example.game3d_opengl.game.terrain.track_elements.spike.SpikeBatchRenderer;
import com.example.game3d_opengl.rendering.GPUResourceOwner;

public final class GameplayElementBatchRenderers implements GPUResourceOwner {
    private final PotionBatchRenderer potionRenderer;
    private final SpikeBatchRenderer spikeRenderer;

    private GameplayElementBatchRenderers(PotionBatchRenderer potionRenderer,
                                          SpikeBatchRenderer spikeRenderer) {
        this.potionRenderer = potionRenderer;
        this.spikeRenderer = spikeRenderer;
    }

    /** Builds renderer resources for the caller's current GL context. */
    public static GameplayElementBatchRenderers load(AssetManager assetManager) {
        PotionBatchRenderer potionRenderer = PotionRenderResources.buildRenderer(assetManager);
        SpikeBatchRenderer spikeRenderer = new SpikeBatchRenderer();
        return new GameplayElementBatchRenderers(potionRenderer, spikeRenderer);
    }

    public void beginFrame() {
        potionRenderer.beginFrame();
        spikeRenderer.beginFrame();
    }

    public void submit(PotionBatchInstance collectible) {
        potionRenderer.add(collectible);
    }

    public void submit(SpikeBatchInstance spike) {
        spikeRenderer.add(spike);
    }

    public void flush(float[] vpMatrix) {
        potionRenderer.render(vpMatrix);
        spikeRenderer.render(vpMatrix);
    }

    public int getPotionBatchDrawCalls() {
        return potionRenderer.getLastBatchDrawCalls();
    }

    public int getPotionBatchInstanceCount() {
        return potionRenderer.getLastBatchInstanceCount();
    }

    public int getSpikeBatchDrawCalls() {
        return spikeRenderer.getLastBatchDrawCalls();
    }

    public int getSpikeBatchInstanceCount() {
        return spikeRenderer.getLastBatchInstanceCount();
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        potionRenderer.reloadGPUResourcesRecursivelyOnContextLoss();
        spikeRenderer.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        potionRenderer.cleanupGPUResourcesRecursively();
        spikeRenderer.cleanupGPUResourcesRecursively();
    }
}
