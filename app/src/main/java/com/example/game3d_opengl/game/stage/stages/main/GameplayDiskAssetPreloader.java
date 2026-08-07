package com.example.game3d_opengl.game.stage.stages.main;

import android.content.res.AssetManager;

import com.example.game3d_opengl.game.player.player_character.PlayerAssets;
import com.example.game3d_opengl.game.terrain.track_elements.GameplayElementBatchRenderers;
import com.example.game3d_opengl.game.terrain.track_elements.portal.CanonicalPortalVisual;
import com.example.game3d_opengl.game.terrain.track_elements.potion.Potion;

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
                Potion.preparePotionAssetsFromDisk(assetManager);
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
                && Potion.hasPreparedOrLoadedAssets()
                && failure == null;
    }

    public static synchronized boolean isGpuWarmupComplete() {
        return PlayerAssets.isGpuReady()
                && GameplayElementBatchRenderers.isDefaultGpuReady()
                && CanonicalPortalVisual.areSharedGpuAssetsReady();
    }

    public static synchronized boolean warmUpOneGpuAsset(AssetManager assetManager) {
        if (!isDiskReady()) {
            return false;
        }
        if (!PlayerAssets.isGpuReady()) {
            PlayerAssets.LOAD_PLAYER_ASSETS(assetManager);
            return isGpuWarmupComplete();
        }
        if (!GameplayElementBatchRenderers.isDefaultGpuReady()) {
            GameplayElementBatchRenderers.ensureDefaultLoaded(assetManager);
            return isGpuWarmupComplete();
        }
        if (!CanonicalPortalVisual.areSharedGpuAssetsReady()) {
            CanonicalPortalVisual.warmUpSharedGpuAssets();
            return isGpuWarmupComplete();
        }
        return true;
    }

    public static synchronized boolean isReady() {
        return isDiskReady() && isGpuWarmupComplete();
    }

    public static synchronized void throwIfFailed() {
        if (failure == null) {
            return;
        }
        throw new IllegalStateException("Gameplay disk asset preload failed", failure);
    }
}
