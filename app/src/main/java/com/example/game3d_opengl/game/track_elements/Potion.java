package com.example.game3d_opengl.game.track_elements;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.GameMath.getNormal;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3S;

import android.content.res.AssetManager;
import android.opengl.Matrix;

import com.example.game3d_opengl.rendering.object3d.ModelCreator;
import com.example.game3d_opengl.rendering.object3d.Object3D;
import com.example.game3d_opengl.rendering.object3d.Polygon3D;
import com.example.game3d_opengl.rendering.util3d.GameRandom;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;

import java.io.IOException;

public class Potion extends Addon {

    private static final float POTION_WIDTH = 0.2f, POTION_HEIGHT = 0.62f;
    
    // Shared geometry for all potions
    private static Polygon3D[] sharedPolygons;
    private static boolean assetsLoaded = false;
    
    // Instance-specific transform
    private float objX, objY, objZ, objYaw = 0f;
    private final float[] mvpMatrix = new float[16];
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
            
            // Create a single Object3D to extract shared polygons
            Object3D tempObject = new Object3D.Builder()
                    .angles(0, 0, 0)
                    .edgeColor(CLR(1.0f, 1.0f, 1.0f, 1.0f))
                    .fillColor(CLR(1.0f, 0.0f, 1.0f, 1.0f))
                    .verts(modelCreator.getVerts())
                    .faces(modelCreator.getFaces())
                    .buildObject();
            
            // Extract the polygons for sharing (we need to access the polygons)
            // For now, create shared polygons using createWithVertexData
            Vector3D[] verts = modelCreator.getVerts();
            int[][] faces = modelCreator.getFaces();
            sharedPolygons = new Polygon3D[faces.length];
            
            for (int i = 0; i < faces.length; i++) {
                int[] face = faces[i];
                float[] coords = new float[face.length * 3];
                for (int j = 0; j < face.length; j++) {
                    coords[3 * j] = verts[face[j]].x;
                    coords[3 * j + 1] = verts[face[j]].y;
                    coords[3 * j + 2] = verts[face[j]].z;
                }
                sharedPolygons[i] = Polygon3D.createWithVertexData(
                    coords, 
                    false, // compute center
                    CLR(1.0f, 0.0f, 1.0f, 1.0f), // magenta fill
                    CLR(1.0f, 1.0f, 1.0f, 1.0f)  // white edges
                );
            }
            
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

    private void applyTransformations(float[] mMatrix, float[] vpMatrix) {
        Matrix.setIdentityM(mMatrix, 0);
        Matrix.translateM(mMatrix, 0, objX, objY, objZ);
        Matrix.rotateM(mMatrix, 0, objYaw, 0, 1, 0);
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, mMatrix, 0);
    }

    @Override
    public void draw(float[] vpMatrix) {
        if (!assetsLoaded || sharedPolygons == null) return;
        
        applyTransformations(modelMatrix, vpMatrix);
        
        // Draw all shared polygons with this instance's transform
        for (Polygon3D poly : sharedPolygons) {
            poly.draw(mvpMatrix);
        }
    }

    @Override
    public void updateBeforeDraw(float dt) {
        objYaw += dt * 0.16f;
    }

    @Override
    public void updateAfterDraw(float dt) {
    }

    @Override
    public void cleanupOnDeath() {
        // Don't cleanup shared resources here - they're shared among all potions
    }

    @Override
    public void resetGPUResources() {
        for (Polygon3D poly : sharedPolygons) {
            poly.reload();
        }
    }

    /**
     * Call this when the application is shutting down to cleanup shared resources
     */
    public static void cleanupSharedResources() {
        if (sharedPolygons != null) {
            for (Polygon3D poly : sharedPolygons) {
                poly.cleanup();
            }
            sharedPolygons = null;
            assetsLoaded = false;
        }
    }
}
