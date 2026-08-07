package com.example.game3d_opengl.game.terrain.track_elements;

import android.content.res.AssetManager;

import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;
import com.example.game3d_opengl.game.terrain.track_elements.potion.PotionBatchInstance;
import com.example.game3d_opengl.game.terrain.track_elements.potion.PotionBatchRenderer;
import com.example.game3d_opengl.game.terrain.track_elements.spike.DeathSpike;
import com.example.game3d_opengl.game.terrain.track_elements.spike.SpikeBatchInstance;
import com.example.game3d_opengl.game.terrain.track_elements.spike.SpikeBatchRenderer;
import com.example.game3d_opengl.rendering.GPUResourceOwner;

public final class GameplayElementBatchRenderers implements GPUResourceOwner {
    private static GameplayElementBatchRenderers defaultRenderers;
    private static boolean defaultGpuResourcesDirty = false;

    private final PotionBatchRenderer potionRenderer;
    private final SpikeBatchRenderer spikeRenderer;

    private GameplayElementBatchRenderers(PotionBatchRenderer potionRenderer,
                                          SpikeBatchRenderer spikeRenderer) {
        this.potionRenderer = potionRenderer;
        this.spikeRenderer = spikeRenderer;
    }

    public static synchronized GameplayElementBatchRenderers ensureDefaultLoaded(AssetManager assetManager) {
        if (defaultRenderers != null) {
            if (defaultGpuResourcesDirty) {
                defaultRenderers.reloadGPUResourcesRecursivelyOnContextLoss();
                defaultGpuResourcesDirty = false;
            }
            return defaultRenderers;
        }
        PotionBatchRenderer potionRenderer = Potion.buildBatchRenderer(assetManager);
        SpikeBatchRenderer spikeRenderer = new SpikeBatchRenderer();
        defaultRenderers = new GameplayElementBatchRenderers(potionRenderer, spikeRenderer);
        Potion.installDefaultBatchRenderer(potionRenderer);
        DeathSpike.installDefaultBatchRenderer(spikeRenderer);
        defaultGpuResourcesDirty = false;
        return defaultRenderers;
    }

    public static synchronized GameplayElementBatchRenderers getDefaultOrNull() {
        return defaultRenderers;
    }

    public static synchronized boolean isDefaultGpuReady() {
        return defaultRenderers != null && !defaultGpuResourcesDirty;
    }

    public static synchronized void reloadDefaultGPUResourcesOnContextLoss() {
        if (defaultRenderers == null) {
            return;
        }
        defaultRenderers.reloadGPUResourcesRecursivelyOnContextLoss();
        defaultGpuResourcesDirty = false;
    }

    public static synchronized void cleanupDefaultGPUResources() {
        if (defaultRenderers == null || defaultGpuResourcesDirty) {
            return;
        }
        defaultRenderers.cleanupGPUResourcesRecursively();
        defaultGpuResourcesDirty = true;
    }

    public static synchronized void markDefaultGpuResourcesDirty() {
        if (defaultRenderers != null) {
            defaultGpuResourcesDirty = true;
        }
    }

    public void beginFrame() {
        potionRenderer.beginFrame();
        spikeRenderer.beginFrame();
    }

    public boolean submit(Addon addon) {
        if (addon instanceof Potion) {
            Potion potion = (Potion) addon;
            if (potion.usesBatchRenderer(potionRenderer)) {
                potionRenderer.add(potion);
                return true;
            }
        }
        if (addon instanceof DeathSpike) {
            DeathSpike spike = (DeathSpike) addon;
            if (spike.usesBatchRenderer(spikeRenderer)) {
                spikeRenderer.add(spike);
                return true;
            }
        }
        return false;
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
