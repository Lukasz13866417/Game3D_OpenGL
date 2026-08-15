package com.example.game3d_opengl.game.stage.stages.main;

import android.content.res.AssetManager;
import android.util.Log;

import com.example.game3d.authoring.GameplayLevelCatalog;
import com.example.game3d.terrain.io.publish.PublishedCatalogException;
import com.example.game3d.terrain.io.publish.PublishedGameplayCatalogLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/** Loads one immutable published catalog for a gameplay-session family. */
final class PublishedTerrainCatalogAssets {
    static final String ASSET_PATH = "terrain/runtime-catalog.json";
    private static final String LOG_TAG = "TerrainCatalog";

    private PublishedTerrainCatalogAssets() {
    }

    static GameplayLevelCatalog loadOrBuiltIns(AssetManager assets) {
        if (assets == null) {
            throw new IllegalArgumentException("assets == null");
        }
        try (InputStream input = assets.open(ASSET_PATH);
             Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return new PublishedGameplayCatalogLoader().load(reader);
        } catch (IOException | PublishedCatalogException invalid) {
            Log.w(LOG_TAG,
                    "Published terrain catalog is unavailable or invalid; using built-ins",
                    invalid);
            return GameplayLevelCatalog.builtIns();
        }
    }
}
