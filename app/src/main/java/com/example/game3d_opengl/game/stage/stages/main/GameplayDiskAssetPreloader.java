package com.example.game3d_opengl.game.stage.stages.main;

import android.content.res.AssetManager;

import com.example.game3d_opengl.game.player.player_character.PlayerAssets;
import com.example.game3d_opengl.game.terrain.presentation.TerrainRendererRegistry;
import com.example.game3d_opengl.game.terrain.track_elements.potion.PotionRenderResources;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class GameplayDiskAssetPreloader {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "gameplay-disk-preloader");
        t.setDaemon(true);
        return t;
    });

    private static Future<?> inFlightTask;
    private static Throwable failure;

    private GameplayDiskAssetPreloader() {}

    public static synchronized void startIfNeeded(AssetManager assetManager) {
        if (isDiskReady()) {
            return;
        }
        if (inFlightTask != null && !inFlightTask.isDone()) {
            return;
        }
        failure = null;
        inFlightTask = EXECUTOR.submit(() -> {
            try {
                PlayerAssets.preparePlayerAssetsFromDisk(assetManager);
                PotionRenderResources.prepareFromDisk(assetManager);
            } catch (Throwable t) {
                synchronized (GameplayDiskAssetPreloader.class) {
                    failure = t;
                }
                throw t;
            }
        });
    }

    public static synchronized boolean isDiskReady() {
        return PlayerAssets.hasPreparedOrLoadedAssets()
                && PotionRenderResources.isPrepared()
                && failure == null;
    }

    public static synchronized boolean isGpuWarmupComplete(
            TerrainRendererRegistry terrainRenderers) {
        return PlayerAssets.isGpuReady()
                && terrainRenderers != null
                && terrainRenderers.isReady();
    }

    public static synchronized boolean warmUpOneGpuAsset(
            AssetManager assetManager,
            TerrainRendererRegistry terrainRenderers) {
        if (!isDiskReady()) {
            return false;
        }
        if (!PlayerAssets.isGpuReady()) {
            PlayerAssets.LOAD_PLAYER_ASSETS(assetManager);
            return isGpuWarmupComplete(terrainRenderers);
        }
        if (terrainRenderers == null) {
            throw new IllegalArgumentException("terrainRenderers == null");
        }
        if (!terrainRenderers.isReady()) {
            terrainRenderers.ensureLoaded(assetManager);
            return isGpuWarmupComplete(terrainRenderers);
        }
        return true;
    }

    public static synchronized boolean isReady(
            TerrainRendererRegistry terrainRenderers) {
        return isDiskReady() && isGpuWarmupComplete(terrainRenderers);
    }

    public static synchronized void throwIfFailed() {
        if (failure == null) {
            return;
        }
        throw new IllegalStateException("Gameplay disk asset preload failed", failure);
    }
}
