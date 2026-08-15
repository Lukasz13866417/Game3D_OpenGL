package com.example.game3d_opengl.game.terrain.presentation;

import com.example.game3d_opengl.game.terrain.track_elements.GameplayElementBatchRenderers;
import com.example.game3d_opengl.game.terrain.track_elements.portal.PortalRenderResources;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TerrainRendererRegistryTest {
    @Test
    public void contextReloadAndCleanupAffectOnlyTheOwningRegistry() {
        RecordingResources firstResources = new RecordingResources();
        RecordingResources secondResources = new RecordingResources();
        TerrainRendererRegistry first = new TerrainRendererRegistry(firstResources);
        TerrainRendererRegistry second = new TerrainRendererRegistry(secondResources);

        first.reloadGPUResourcesRecursivelyOnContextLoss();

        assertEquals(1, firstResources.reloads);
        assertEquals(0, secondResources.reloads);
        assertTrue(first.isReady());
        assertTrue(second.isReady());

        first.cleanupGPUResourcesRecursively();

        assertEquals(1, firstResources.cleanups);
        assertEquals(0, secondResources.cleanups);
        assertFalse(first.isReady());
        assertTrue(second.isReady());
    }

    private static final class RecordingResources
            implements TerrainRendererRegistry.LoadedResources {
        int reloads;
        int cleanups;

        @Override public GameplayElementBatchRenderers batches() {
            return null;
        }

        @Override public PortalRenderResources portals() {
            return null;
        }

        @Override public TerrainRibbonShaderPair terrainShader() {
            return null;
        }

        @Override public void reloadGPUResourcesRecursivelyOnContextLoss() {
            reloads++;
        }

        @Override public void cleanupGPUResourcesRecursively() {
            cleanups++;
        }
    }
}
