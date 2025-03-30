package com.example.game3d_opengl.game.track_elements;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.GameMath.PI;
import static com.example.game3d_opengl.rendering.util3d.GameMath.getNormal;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3S;

import android.content.res.AssetManager;

import com.example.game3d_opengl.rendering.object3d.ModelCreator;
import com.example.game3d_opengl.rendering.object3d.Object3D;
import com.example.game3d_opengl.rendering.util3d.GameRandom;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;

import java.io.IOException;

public class Potion extends Addon {

    private static final float POTION_WIDTH = 0.2f, POTION_HEIGHT = 0.62f;
    private Object3D object3D;
    private static Object3D.Builder potionBuilder;
    public static void LOAD_POTION_ASSETS(AssetManager assetManager){
        ModelCreator modelCreator = new ModelCreator(assetManager);
        try {
            modelCreator.load("potion.obj");
            modelCreator.centerVerts();
            modelCreator.scaleX(POTION_WIDTH);
            modelCreator.scaleY(POTION_HEIGHT);
            modelCreator.scaleZ(POTION_WIDTH);
            potionBuilder = new Object3D.Builder()
                    .angles(0, 0, 0)
                    .edgeColor(CLR(1.0f, 1.0f, 1.0f, 1.0f))
                    .fillColor(CLR(1.0f, 0.0f, 1.0f, 1.0f))
                    .verts(modelCreator.getVerts())
                    .faces(modelCreator.getFaces());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public Potion(){
        super();
    }
    @Override
    protected void init(Vector3D fieldNearLeft, Vector3D fieldNearRight,
                        Vector3D fieldFarLeft, Vector3D fieldFarRight) {
        Vector3D fieldMid = fieldFarLeft.add(fieldFarRight)
                .add(fieldNearRight).add(fieldNearLeft).div(4);
        Vector3D out = getNormal(fieldNearLeft,fieldFarLeft,fieldFarRight).mult(-1);
        Vector3D myMid = fieldMid.add(out.withLen(0.1f)).addY(POTION_HEIGHT/2);
        object3D = potionBuilder.position(myMid.x, myMid.y, myMid.z).buildObject();
    }

    @Override
    public void draw(float[] vpMatrix) {
        object3D.draw(vpMatrix);
    }

    @Override
    public void updateBeforeDraw(float dt) {
        object3D.objYaw += dt * 0.16f;
    }

    @Override
    public void updateAfterDraw(float dt) {

    }
}
