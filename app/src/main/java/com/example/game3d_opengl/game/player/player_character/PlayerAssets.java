package com.example.game3d_opengl.game.player.player_character;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import android.content.res.AssetManager;
import android.util.Log;

import com.example.game3d_opengl.rendering.object3d.UnbatchedObject3DWithOutline;
import com.example.game3d_opengl.rendering.util3d.ModelCreator;

import java.io.IOException;
import static com.example.game3d_opengl.game.player.player_character.PlayerConfig.*;

public class PlayerAssets {

    static UnbatchedObject3DWithOutline PLAYER_OBJECT;


    /**
     * Loads the player's 3D model and creates the UnbatchedObject3D builder.
     * This method must be called before creating any Player instances.
     *
     * @param assetManager the Android asset manager for loading model files
     * @throws RuntimeException if asset loading fails
     */
    public static void LOAD_PLAYER_ASSETS(AssetManager assetManager) {
        if (assetManager == null) {
            throw new IllegalArgumentException("AssetManager cannot be null");
        }

        ModelCreator playerCreator = new ModelCreator(assetManager);
        try {
            // Load and process the 3D model
            playerCreator.load(PLAYER_MODEL_FILENAME);
            playerCreator.centerVerts();
            playerCreator.rotateX(MODEL_ROTATION_X);
            playerCreator.rotateY(MODEL_ROTATION_Y);
            playerCreator.scaleX(PLAYER_WIDTH);
            playerCreator.scaleY(PLAYER_HEIGHT);
            playerCreator.scaleZ(PLAYER_HEIGHT);

            UnbatchedObject3DWithOutline.Builder builder =
                    new UnbatchedObject3DWithOutline.Builder()
                    .verts(playerCreator.getVerts())
                    .faces(playerCreator.getFaces())
                    .fillColor(CLR(49f/255f, 37f/255f, 34f/255f,1.0f))
                    .ambient(0.3f)
                    .diffuse(0.9f)
                    .specular(0.15f)
                    .shininess(5f);
            if (playerCreator.hasNormals()) {
                builder.normals(playerCreator.getNormals());
            }
            UnbatchedObject3DWithOutline obj = builder.build();
            obj.objX = INITIAL_POSITION_X;
            obj.objY = INITIAL_POSITION_Y;
            obj.objZ = INITIAL_POSITION_Z;
            PLAYER_OBJECT = obj;

            Log.d(TAG, "Player assets loaded successfully");
        } catch (IOException e) {
            Log.e(TAG, ERROR_ASSET_LOADING + e.getMessage(), e);
            throw new RuntimeException(ERROR_ASSET_LOADING + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error loading player assets: " + e.getMessage(), e);
            throw new RuntimeException("Unexpected error loading player assets: " + e.getMessage(), e);
        }
    }
}
