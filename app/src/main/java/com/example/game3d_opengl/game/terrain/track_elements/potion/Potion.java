package com.example.game3d_opengl.game.terrain.track_elements.potion;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.game.util.GameMath.getNormal;

import android.content.res.AssetManager;
import android.opengl.Matrix;

import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.ModelCreator;
import com.example.game3d_opengl.rendering.util3d.PreparedModelData;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;

import java.io.IOException;

public class Potion extends Addon
        implements PotionBatchInstance {

    public static final float POTION_MODEL_WIDTH = 0.2f,
                               POTION_MODEL_HEIGHT = 0.62f,
                               POTION_MODEL_LINE_THICKNESS = 0.78f;

    public static FColor POTION_FILL_COLOR = CLR(0.8f,0,0.8f,1);
    public static FColor POTION_EDGE_COLOR = CLR(1,1,1,1);
    
    private static PotionBatchRenderer defaultBatchRenderer;
    private static PreparedModelData preparedPotionModel;

    private PotionBatchRenderer batchRenderer;
    private float objX;
    private float objY;
    private float objZ;
    private float objYaw;

    public static synchronized void preparePotionAssetsFromDisk(AssetManager assetManager) {
        if (defaultBatchRenderer != null || preparedPotionModel != null) {
            return;
        }
        if (assetManager == null) {
            throw new IllegalArgumentException("AssetManager cannot be null");
        }
        
        ModelCreator modelCreator = new ModelCreator(assetManager);
        try {
            modelCreator.load("potion.obj");
            modelCreator.centerVerts();
            modelCreator.scaleX(POTION_MODEL_WIDTH);
            modelCreator.scaleY(POTION_MODEL_HEIGHT);
            modelCreator.scaleZ(POTION_MODEL_WIDTH);
            preparedPotionModel = new PreparedModelData(
                    modelCreator.getVerts(),
                    modelCreator.getFaces(),
                    modelCreator.hasNormals() ? modelCreator.getNormals() : null
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static synchronized boolean hasPreparedOrLoadedAssets() {
        return defaultBatchRenderer != null || preparedPotionModel != null;
    }

    public static synchronized PotionBatchRenderer buildBatchRenderer(AssetManager assetManager) {
        if (preparedPotionModel == null) {
            preparePotionAssetsFromDisk(assetManager);
        }
        PotionBatchRenderer renderer = new PotionBatchRenderer(preparedPotionModel);
        preparedPotionModel = null;
        return renderer;
    }

    public static synchronized void installDefaultBatchRenderer(PotionBatchRenderer renderer) {
        defaultBatchRenderer = renderer;
    }

    public static Potion createPotion(){
        return new Potion();
    }
    
    private Potion(){
        this(defaultBatchRenderer);
    }

    public Potion(PotionBatchRenderer batchRenderer) {
        super();
        this.batchRenderer = batchRenderer;
    }
    
    @Override
    protected void onPlace(float nearLeftX, float nearLeftY, float nearLeftZ,
                           float nearRightX, float nearRightY, float nearRightZ,
                           float farLeftX, float farLeftY, float farLeftZ,
                           float farRightX, float farRightY, float farRightZ) {
        float fieldMidX = 0.25f * (farLeftX + farRightX + nearRightX + nearLeftX);
        float fieldMidY = 0.25f * (farLeftY + farRightY + nearRightY + nearLeftY);
        float fieldMidZ = 0.25f * (farLeftZ + farRightZ + nearRightZ + nearLeftZ);

        float edge1X = farLeftX - nearLeftX;
        float edge1Y = farLeftY - nearLeftY;
        float edge1Z = farLeftZ - nearLeftZ;
        float edge2X = farRightX - nearLeftX;
        float edge2Y = farRightY - nearLeftY;
        float edge2Z = farRightZ - nearLeftZ;
        float normalX = edge1Y * edge2Z - edge1Z * edge2Y;
        float normalY = edge1Z * edge2X - edge1X * edge2Z;
        float normalZ = edge1X * edge2Y - edge1Y * edge2X;
        float normalLen = (float) Math.sqrt(
                normalX * normalX + normalY * normalY + normalZ * normalZ
        );
        if (normalLen > 1e-8f) {
            float scale = -0.1f / normalLen;
            objX = fieldMidX + normalX * scale;
            objY = fieldMidY + normalY * scale + POTION_MODEL_HEIGHT / 2f;
            objZ = fieldMidZ + normalZ * scale;
            return;
        }

        objX = fieldMidX;
        objY = fieldMidY + POTION_MODEL_HEIGHT / 2f;
        objZ = fieldMidZ;
    }

    @Override
    public void draw(float[] vpMatrix) {
        PotionBatchRenderer renderer = resolveBatchRenderer();
        if (renderer != null) {
            renderer.drawSingle(vpMatrix, this);
        }
    }

    @Override
    public void updateBeforeDraw(float dtMillis) {
        objYaw += dtMillis * 0.16f;
    }

    @Override
    public void updateAfterDraw(float dtMillis) {
    }

    @Override
    public void rebasePosition(Vector3D delta) {
        if (delta == null) return;
        objX += delta.x;
        objY += delta.y;
        objZ += delta.z;
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        // Renderer ownership lives outside the addon instance.
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        // Renderer ownership lives outside the addon instance.
    }


    @Override
    public void accept(Player player) {
        player.interactWith(this);
    }

    public boolean usesBatchRenderer(PotionBatchRenderer renderer) {
        if (renderer == null) {
            return false;
        }
        if (batchRenderer == null) {
            batchRenderer = defaultBatchRenderer;
        }
        return batchRenderer == renderer;
    }

    void writeModelMatrix(float[] outModel) {
        Matrix.setIdentityM(outModel, 0);
        Matrix.translateM(outModel, 0, objX, objY, objZ);
        Matrix.rotateM(outModel, 0, objYaw, 0f, 1f, 0f);
    }

    @Override
    public void writePotionModelMatrix(float[] outModel) {
        writeModelMatrix(outModel);
    }

    private PotionBatchRenderer resolveBatchRenderer() {
        if (batchRenderer == null) {
            batchRenderer = defaultBatchRenderer;
        }
        return batchRenderer;
    }
}
