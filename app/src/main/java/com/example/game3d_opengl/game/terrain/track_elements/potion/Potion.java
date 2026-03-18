package com.example.game3d_opengl.game.terrain.track_elements.potion;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.game.util.GameMath.getNormal;

import android.content.res.AssetManager;

import com.example.game3d_opengl.game.player.player_character.Player;
import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.ModelCreator;
import com.example.game3d_opengl.rendering.infill.Mesh3DInfill;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain.terrain_api.addon.Addon;

import java.io.IOException;

public class Potion extends Addon {

    public static final float POTION_MODEL_WIDTH = 0.2f,
                               POTION_MODEL_HEIGHT = 0.62f,
                               POTION_MODEL_LINE_THICKNESS = 0.78f;

    public static FColor POTION_FILL_COLOR = CLR(0.8f,0,0.8f,1);
    public static FColor POTION_EDGE_COLOR = CLR(1,1,1,1);
    
    // Shared mesh for all potions
    private static Mesh3DInfill sharedFill;
    private static boolean assetsLoaded = false;
    
    // Instance-specific transform wrapper
    private final UnbatchedObject3DWithOutline object3D;
    
    public static void LOAD_POTION_ASSETS(AssetManager assetManager){
        if (assetsLoaded){
            // Prevent loading multiple times. Always fail fast in this project
            throw new IllegalStateException("Attempt to load twice");
            // TODO slight improvement:  do the same in each LOAD_xxx_ASSETS.
        };
        
        ModelCreator modelCreator = new ModelCreator(assetManager);
        try {
            modelCreator.load("potion.obj");
            modelCreator.centerVerts();
            modelCreator.scaleX(POTION_MODEL_WIDTH);
            modelCreator.scaleY(POTION_MODEL_HEIGHT);
            modelCreator.scaleZ(POTION_MODEL_WIDTH);
            
            // Build shared meshes once
            sharedFill = new Mesh3DInfill.Builder()
                    .verts(modelCreator.getVerts())
                    .faces(modelCreator.getFaces())
                    .fillColor(POTION_FILL_COLOR)
                    .ambient(0.5f)
                    .diffuse(0.7f)
                    .specular(2.0f)
                    .shininess(64f)
                    .buildObject();

            assetsLoaded = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static Potion createPotion(){
        assert sharedFill != null;
        UnbatchedObject3DWithOutline obj3d =
                                          UnbatchedObject3DWithOutline.wrap(sharedFill, null);
        return new Potion(obj3d);
    }
    
    private Potion(UnbatchedObject3DWithOutline object3DWithOutline){
        super();
        this.object3D = object3DWithOutline;

    }
    
    @Override
    protected void onPlace(Vector3D fieldNearLeft, Vector3D fieldNearRight,
                           Vector3D fieldFarLeft, Vector3D fieldFarRight) {
        Vector3D fieldMid = fieldFarLeft.add(fieldFarRight)
                .add(fieldNearRight).add(fieldNearLeft).div(4);
        Vector3D out = getNormal(fieldNearLeft,fieldFarLeft,fieldFarRight).mult(-1);
        Vector3D myMid = fieldMid.add(out.withLen(0.1f)).addY(POTION_MODEL_HEIGHT/2);
        
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
    public void rebasePosition(Vector3D delta) {
        if (delta == null || object3D == null) return;
        object3D.objX += delta.x;
        object3D.objY += delta.y;
        object3D.objZ += delta.z;
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        sharedFill.cleanupGPUResourcesRecursively();
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        sharedFill.reloadGPUResourcesRecursivelyOnContextLoss();
    }


    @Override
    public void accept(Player player) {
        player.interactWith(this);
    }
}
