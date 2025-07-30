package com.example.game3d_opengl.game.track_elements;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.GameMath.getNormal;

import android.content.res.AssetManager;
import android.opengl.Matrix;

import com.example.game3d_opengl.rendering.object3d.ModelCreator;
import com.example.game3d_opengl.rendering.object3d.AbsoluteObject3D;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;

import java.io.IOException;

public class Potion extends Addon {

    private static final float POTION_WIDTH = 0.2f, POTION_HEIGHT = 0.62f;
    
    // Shared geometry for all potions
    private static AbsoluteObject3D sharedPotionMesh;
    private static boolean assetsLoaded = false;
    
    // Instance-specific transform
    private float objX, objY, objZ, objYaw = 0f;
    private final float[] modelMatrix = new float[16];
    
    public static void LOAD_POTION_ASSETS(AssetManager assetManager){
        if (assetsLoaded) return; // prevent loading multiple times
        
        ModelCreator modelCreator = new ModelCreator(assetManager);
        try {
            modelCreator.load("potion.obj");
            modelCreator.centerVerts();
            modelCreator.scaleX(POTION_WIDTH);
            modelCreator.scaleY(POTION_HEIGHT);
            modelCreator.scaleZ(POTION_WIDTH);
            
            // Create a shared AbsoluteObject3D mesh for all potions
            sharedPotionMesh = new AbsoluteObject3D.Builder()
                    .edgeColor(CLR(1.0f, 1.0f, 1.0f, 1.0f))
                    .fillColor(CLR(1.0f, 0.0f, 1.0f, 1.0f))
                    .verts(modelCreator.getVerts())
                    .faces(modelCreator.getFaces())
                    .buildObject();
            
            assetsLoaded = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public Potion(){
        super();
    }
    
    @Override
    protected void onPlace(Vector3D fieldNearLeft, Vector3D fieldNearRight,
                           Vector3D fieldFarLeft, Vector3D fieldFarRight) {
        Vector3D fieldMid = fieldFarLeft.add(fieldFarRight)
                .add(fieldNearRight).add(fieldNearLeft).div(4);
        Vector3D out = getNormal(fieldNearLeft,fieldFarLeft,fieldFarRight).mult(-1);
        Vector3D myMid = fieldMid.add(out.withLen(0.1f)).addY(POTION_HEIGHT/2);
        
        // Store position for this instance
        objX = myMid.x;
        objY = myMid.y;
        objZ = myMid.z;
    }

    private void applyTransformations(float[] mMatrix) {
        Matrix.setIdentityM(mMatrix, 0);
        Matrix.translateM(mMatrix, 0, objX, objY, objZ);
        Matrix.rotateM(mMatrix, 0, objYaw, 0, 1, 0);
    }

    @Override
    public void draw(float[] vpMatrix) {
        if (!assetsLoaded || sharedPotionMesh == null) return;
        
        applyTransformations(modelMatrix);
        
        // Draw the shared mesh with this instance's transform
        sharedPotionMesh.draw(modelMatrix, vpMatrix);
    }

    @Override
    public void updateBeforeDraw(float dt) {
        objYaw += dt * 0.16f;
    }

    @Override
    public void updateAfterDraw(float dt) {
    }

    @Override
    public void cleanupGPUResources() {
        // Don't cleanup shared resources here - they're shared among all potions
    }

    @Override
    public void resetGPUResources() {
    }


    /**
     * Call this when the application is shutting down to cleanup shared resources
     */
    public static void cleanupSharedGPUResources() {
        if (sharedPotionMesh != null) {
            sharedPotionMesh.cleanup();
            sharedPotionMesh = null;
        }
    }

    public static void resetSharedResources(){
        sharedPotionMesh.reload();
    }
}
