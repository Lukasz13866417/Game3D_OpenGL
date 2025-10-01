package com.example.game3d_opengl.game.ui.icons;

import static com.example.game3d_opengl.game.terrain.track_elements.potion.Potion.POTION_EDGE_COLOR;
import static com.example.game3d_opengl.game.terrain.track_elements.potion.Potion.POTION_FILL_COLOR;
import static com.example.game3d_opengl.game.terrain.track_elements.potion.Potion.POTION_MODEL_HEIGHT;
import static com.example.game3d_opengl.game.terrain.track_elements.potion.Potion.POTION_MODEL_WIDTH;
import static com.example.game3d_opengl.game.ui.icons.UIConstants.ICON_DEFAULT_EDGE_WIDTH;
import static com.example.game3d_opengl.game.util.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import android.content.res.AssetManager;

import com.example.game3d_opengl.rendering.icon.Icon;
import com.example.game3d_opengl.rendering.icon.SpinningIcon;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.ModelCreator;
import com.example.game3d_opengl.rendering.util3d.rect.Rect;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.io.IOException;

public class PotionIcon extends SpinningIcon {

    protected PotionIcon(SpinningIcon.SpinningBuilder builder, float[] mvp) {
        super(builder, mvp);
    }


    private static Vector3D[] POTION_ICON_VERTS = null;
    private static int[][] POTION_ICON_FACES = null;

    private static final FColor POTION_ICON_FILL_COLOR = POTION_FILL_COLOR;
    private static final FColor POTION_ICON_EDGE_COLOR = POTION_EDGE_COLOR;

    private static boolean assetsLoaded = false;


    public static void LOAD_POTION_ICON_ASSETS(AssetManager assetManager){
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

            POTION_ICON_VERTS = modelCreator.getVerts();
            POTION_ICON_FACES = modelCreator.getFaces();

            assetsLoaded = true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // Expose loaded geometry for reuse in other icons (e.g., SpinningIcon)
    public static Vector3D[] getLoadedVerts(){
        if(!assetsLoaded) throw new IllegalStateException("Potion icon assets not loaded");
        return POTION_ICON_VERTS;
    }
    public static int[][] getLoadedFaces(){
        if(!assetsLoaded) throw new IllegalStateException("Potion icon assets not loaded");
        return POTION_ICON_FACES;
    }

    public static class PotionIconBuilder extends SpinningIcon.SpinningBuilder {

        private boolean areWeSettingFieldsInternallyNow = false;

        /* Setting verts, faces, fillColor, edgeColor, edgePixels from outside is forbidden.
           These values are already set internally.
         */

        // TODO maybe come up with a more OOP way of disabling these methods for PotionIconBuilder.
        @Override
        public PotionIconBuilder verts(Vector3D[] v){
            if(!areWeSettingFieldsInternallyNow){
                throw new IllegalStateException("An attempt to set this from outside.");
            }
            super.verts(v);
            return self();
        }

        @Override
        public PotionIconBuilder faces(int[][] f){
            if(!areWeSettingFieldsInternallyNow){
                throw new IllegalStateException("An attempt to set this from outside.");
            }
            super.faces(f);
            return self();
        }

        @Override
        public PotionIconBuilder fillColor(FColor c){
            if(!areWeSettingFieldsInternallyNow){
                throw new IllegalStateException("An attempt to set this from outside.");
            }
            super.fillColor(c);
            return self();
        }

        @Override
        public PotionIconBuilder edgeColor(FColor c){
            if(!areWeSettingFieldsInternallyNow){
                throw new IllegalStateException("An attempt to set this from outside.");
            }
            super.edgeColor(c);
            return self();
        }

        @Override
        public PotionIconBuilder edgePixels(float px){
            if(!areWeSettingFieldsInternallyNow){
                throw new IllegalStateException("An attempt to set this from outside.");
            }
            super.edgePixels(px);
            return self();
        }

        @Override
        protected void checkValid(){
            areWeSettingFieldsInternallyNow = true;
            verts(POTION_ICON_VERTS);
            faces(POTION_ICON_FACES);
            edgeColor(POTION_ICON_EDGE_COLOR);
            fillColor(POTION_ICON_FILL_COLOR);
            edgePixels(ICON_DEFAULT_EDGE_WIDTH);
            areWeSettingFieldsInternallyNow = false;
            super.checkValid();
        }

        @Override
        protected PotionIcon createWhenReady() {
            return new PotionIcon(this, getComputedMvp());
        }

        @Override
        protected PotionIconBuilder self() {
            return this;
        }
    }
}
