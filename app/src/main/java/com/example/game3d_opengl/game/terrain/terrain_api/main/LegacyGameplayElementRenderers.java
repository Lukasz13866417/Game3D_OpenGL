package com.example.game3d_opengl.game.terrain.terrain_api.main;

import android.content.res.AssetManager;

import com.example.game3d_opengl.game.terrain.track_elements.GameplayElementBatchRenderers;

/**
 * Compatibility-only singleton for quarantined mutable-terrain diagnostics.
 * Production terrain receives a per-context renderer registry from {@code MyGLRenderer}.
 */
public final class LegacyGameplayElementRenderers {
    private static GameplayElementBatchRenderers renderers;

    private LegacyGameplayElementRenderers() {
    }

    public static synchronized GameplayElementBatchRenderers ensureLoaded(
            AssetManager assetManager) {
        if (renderers == null) {
            renderers = GameplayElementBatchRenderers.load(assetManager);
        }
        return renderers;
    }

    public static synchronized GameplayElementBatchRenderers getOrNull() {
        return renderers;
    }

    public static synchronized void reloadOnContextLoss() {
        if (renderers != null) {
            renderers.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }
}
