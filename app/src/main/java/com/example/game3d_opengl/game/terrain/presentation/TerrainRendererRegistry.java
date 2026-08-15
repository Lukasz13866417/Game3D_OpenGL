package com.example.game3d_opengl.game.terrain.presentation;

import android.content.res.AssetManager;

import com.example.game3d_opengl.game.terrain.track_elements.GameplayElementBatchRenderers;
import com.example.game3d_opengl.game.terrain.track_elements.portal.PortalRenderResources;
import com.example.game3d_opengl.rendering.GPUResourceOwner;

/**
 * All terrain-addon GPU resources owned by one {@code MyGLRenderer}/EGL context.
 *
 * <p>The registry has no process-wide default. Presentations borrow it, while the renderer that
 * owns the GL context creates, reloads, and eventually cleans it.</p>
 */
public final class TerrainRendererRegistry implements GPUResourceOwner {
    interface LoadedResources extends GPUResourceOwner {
        GameplayElementBatchRenderers batches();
        PortalRenderResources portals();
        TerrainRibbonShaderPair terrainShader();
    }

    interface ResourceFactory {
        LoadedResources load(AssetManager assetManager);
    }

    private static final class DefaultLoadedResources implements LoadedResources {
        private final GameplayElementBatchRenderers batches;
        private final PortalRenderResources portals;
        private final TerrainRibbonShaderPair terrainShader;

        DefaultLoadedResources(AssetManager assetManager) {
            GameplayElementBatchRenderers createdBatches =
                    GameplayElementBatchRenderers.load(assetManager);
            PortalRenderResources createdPortals = null;
            TerrainRibbonShaderPair createdTerrainShader = null;
            try {
                createdPortals = new PortalRenderResources();
                createdTerrainShader = TerrainRibbonShaderPair.createDefault();
            } catch (RuntimeException | Error failure) {
                if (createdTerrainShader != null) {
                    createdTerrainShader.cleanupGPUResourcesRecursively();
                }
                if (createdPortals != null) {
                    createdPortals.cleanupGPUResourcesRecursively();
                }
                createdBatches.cleanupGPUResourcesRecursively();
                throw failure;
            }
            batches = createdBatches;
            portals = createdPortals;
            terrainShader = createdTerrainShader;
        }

        @Override public GameplayElementBatchRenderers batches() {
            return batches;
        }

        @Override public PortalRenderResources portals() {
            return portals;
        }

        @Override public TerrainRibbonShaderPair terrainShader() {
            return terrainShader;
        }

        @Override public void reloadGPUResourcesRecursivelyOnContextLoss() {
            batches.reloadGPUResourcesRecursivelyOnContextLoss();
            portals.reloadGPUResourcesRecursivelyOnContextLoss();
            terrainShader.reloadGPUResourcesRecursivelyOnContextLoss();
        }

        @Override public void cleanupGPUResourcesRecursively() {
            terrainShader.cleanupGPUResourcesRecursively();
            portals.cleanupGPUResourcesRecursively();
            batches.cleanupGPUResourcesRecursively();
        }
    }

    private final ResourceFactory factory;
    private LoadedResources loaded;

    public TerrainRendererRegistry() {
        this(DefaultLoadedResources::new);
    }

    TerrainRendererRegistry(ResourceFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("factory == null");
        }
        this.factory = factory;
    }

    TerrainRendererRegistry(LoadedResources loaded) {
        if (loaded == null) {
            throw new IllegalArgumentException("loaded == null");
        }
        this.factory = null;
        this.loaded = loaded;
    }

    /** Must be called on the owning GL thread. */
    public synchronized void ensureLoaded(AssetManager assetManager) {
        if (loaded != null) {
            return;
        }
        if (assetManager == null) {
            throw new IllegalArgumentException("assetManager == null");
        }
        LoadedResources created = factory.load(assetManager);
        if (created == null) {
            throw new IllegalStateException("Terrain renderer factory returned null resources");
        }
        loaded = created;
    }

    public synchronized boolean isReady() {
        return loaded != null;
    }

    public synchronized GameplayElementBatchRenderers batchRenderersOrNull() {
        return loaded == null ? null : loaded.batches();
    }

    public synchronized PortalRenderResources requirePortalResources() {
        if (loaded == null || loaded.portals() == null) {
            throw new IllegalStateException("Terrain renderer registry is not loaded");
        }
        return loaded.portals();
    }

    synchronized TerrainRibbonShaderPair terrainShaderOrNull() {
        return loaded == null ? null : loaded.terrainShader();
    }

    /** Rebuilds only this context's resources; safe to call before the registry is first loaded. */
    @Override public synchronized void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (loaded != null) {
            loaded.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override public synchronized void cleanupGPUResourcesRecursively() {
        if (loaded != null) {
            loaded.cleanupGPUResourcesRecursively();
            loaded = null;
        }
    }
}
