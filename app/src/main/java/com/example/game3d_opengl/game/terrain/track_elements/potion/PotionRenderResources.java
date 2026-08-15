package com.example.game3d_opengl.game.terrain.track_elements.potion;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import android.content.res.AssetManager;

import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.ModelCreator;
import com.example.game3d_opengl.rendering.util3d.PreparedModelData;

import java.io.IOException;

/**
 * Renderer-owned potion model data and visual constants.
 *
 * <p>This deliberately has no dependency on the retired mutable terrain-addon API. Disk loading
 * can therefore run for the canonical {@code TerrainPresentation} without initializing a gameplay
 * object or installing a renderer into one.</p>
 */
public final class PotionRenderResources {
    public static final float MODEL_WIDTH = 0.2f;
    public static final float MODEL_HEIGHT = 0.62f;
    public static final float MODEL_LINE_THICKNESS = 0.78f;

    public static final FColor FILL_COLOR = CLR(0.8f, 0f, 0.8f, 1f);
    public static final FColor EDGE_COLOR = CLR(1f, 1f, 1f, 1f);

    private static PreparedModelData preparedModel;

    private PotionRenderResources() {
    }

    public static synchronized void prepareFromDisk(AssetManager assetManager) {
        if (preparedModel != null) {
            return;
        }
        if (assetManager == null) {
            throw new IllegalArgumentException("AssetManager cannot be null");
        }

        ModelCreator modelCreator = new ModelCreator(assetManager);
        try {
            modelCreator.load("potion.obj");
            modelCreator.centerVerts();
            modelCreator.scaleX(MODEL_WIDTH);
            modelCreator.scaleY(MODEL_HEIGHT);
            modelCreator.scaleZ(MODEL_WIDTH);
            preparedModel = new PreparedModelData(
                    modelCreator.getVerts(),
                    modelCreator.getFaces(),
                    modelCreator.hasNormals() ? modelCreator.getNormals() : null
            );
        } catch (IOException e) {
            throw new IllegalStateException("Could not load potion.obj", e);
        }
    }

    public static synchronized boolean isPrepared() {
        return preparedModel != null;
    }

    public static synchronized PotionBatchRenderer buildRenderer(AssetManager assetManager) {
        if (preparedModel == null) {
            prepareFromDisk(assetManager);
        }
        // PreparedModelData is immutable input to the geometry builder. Keep it so separate GL
        // contexts can each build their own renderer registry without rereading potion.obj.
        return new PotionBatchRenderer(preparedModel);
    }
}
