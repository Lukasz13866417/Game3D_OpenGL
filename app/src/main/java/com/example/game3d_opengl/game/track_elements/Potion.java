package com.example.game3d_opengl.game.track_elements;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.GameMath.getNormal;

import android.content.res.AssetManager;

import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.util3d.ModelCreator;
import com.example.game3d_opengl.rendering.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.wireframe.Mesh3DWireframe;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;

import java.io.IOException;

public class Potion extends Addon {

    private static final float POTION_WIDTH = 0.2f,
                               POTION_HEIGHT = 0.62f,
                               POTION_LINE_THICKNESS = 0.78f;
    
    // Shared meshes for all potions
    private static Mesh3DInfill sharedFill;
    private static Mesh3DWireframe sharedWire;
    private static boolean assetsLoaded = false;
    
    // Instance-specific transform wrapper
    private UnbatchedObject3DWithOutline object3D;
    
    public static void LOAD_POTION_ASSETS(AssetManager assetManager){
        if (assetsLoaded) return; // prevent loading multiple times
        
        ModelCreator modelCreator = new ModelCreator(assetManager);
        try {
            modelCreator.load("potion.obj");
            modelCreator.centerVerts();
            modelCreator.scaleX(POTION_WIDTH);
            modelCreator.scaleY(POTION_HEIGHT);
            modelCreator.scaleZ(POTION_WIDTH);
            
            // Build shared meshes once
            sharedFill = new Mesh3DInfill.Builder()
                    .verts(modelCreator.getVerts())
                    .faces(modelCreator.getFaces())
                    .fillColor(CLR(0.8f,0,0.8f,1))
                    .buildObject();

            sharedWire = new Mesh3DWireframe.Builder()
                    .verts(modelCreator.getVerts())
                    .faces(modelCreator.getFaces())
                    .edgeColor(CLR(1,1,1,1))
                    .pixelWidth(POTION_LINE_THICKNESS)
                    .buildObject();
            
            assetsLoaded = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public Potion(){
        super();
        if (assetsLoaded && sharedFill != null && sharedWire != null) {
            this.object3D = UnbatchedObject3DWithOutline.wrap(sharedFill, sharedWire);
        }
    }
    
    @Override
    protected void onPlace(Vector3D fieldNearLeft, Vector3D fieldNearRight,
                           Vector3D fieldFarLeft, Vector3D fieldFarRight) {
        Vector3D fieldMid = fieldFarLeft.add(fieldFarRight)
                .add(fieldNearRight).add(fieldNearLeft).div(4);
        Vector3D out = getNormal(fieldNearLeft,fieldFarLeft,fieldFarRight).mult(-1);
        Vector3D myMid = fieldMid.add(out.withLen(0.1f)).addY(POTION_HEIGHT/2);
        
        if (object3D != null) {
            object3D.objX = myMid.x;
            object3D.objY = myMid.y;
            object3D.objZ = myMid.z;
        }
    }

    @Override
    public void draw(float[] vpMatrix) {
        object3D.draw(vpMatrix);
    }

    @Override
    public void updateBeforeDraw(float dtMillis) {
        if (object3D != null) object3D.objYaw += dtMillis * 0.16f;
    }

    @Override
    public void updateAfterDraw(float dtMillis) {
    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {
        sharedFill.cleanupGPUResourcesRecursivelyOnContextLoss();
        sharedWire.cleanupGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        sharedFill.reloadGPUResourcesRecursivelyOnContextLoss();
        sharedWire.reloadGPUResourcesRecursivelyOnContextLoss();
    }


}
