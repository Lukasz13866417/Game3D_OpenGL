package com.example.game3d_opengl.game.track_elements;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;
import static com.example.game3d_opengl.rendering.util3d.GameMath.getNormal;
import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.V3S;

import com.example.game3d_opengl.rendering.object3d.Object3D;
import com.example.game3d_opengl.rendering.util3d.GameRandom;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.terrain_api.addon.Addon;

public class DeathSpike extends Addon {

    private final float height;
    private Object3D object3D;

    public DeathSpike() {
        super();
        height = GameRandom.randFloat(0.225f, 0.5f, 5);
    }

    @Override
    protected void onPlace(Vector3D fieldNearLeft, Vector3D fieldNearRight,
                           Vector3D fieldFarLeft, Vector3D fieldFarRight) {
        Vector3D fieldMid = fieldFarLeft.add(fieldFarRight)
                .add(fieldNearRight).add(fieldNearLeft).div(4);
        Vector3D out = getNormal(fieldNearLeft, fieldFarLeft, fieldFarRight).mult(-1);
        Vector3D myNL = fieldMid.add(fieldNearLeft.sub(fieldMid).mult(0.8f));
        Vector3D myNR = fieldMid.add(fieldFarLeft.sub(fieldMid).mult(0.8f));
        Vector3D myFL = fieldMid.add(fieldNearRight.sub(fieldMid).mult(0.8f));
        Vector3D myFR = fieldMid.add(fieldFarRight.sub(fieldMid).mult(0.8f));
        Vector3D[] verts = V3S(
                myNL.add(out.withLen(0.025f)), myNR.add(out.withLen(0.025f)),
                myFL.add(out.withLen(0.025f)), myFR.add(out.withLen(0.025f))
                , fieldMid.add(out.withLen(height))
        );
        object3D = new Object3D.Builder()
                .angles(0, 0, 0)
                .position(0, 0, 0)
                .verts(verts)
                .faces(
                        new int[][]{
                                new int[]{0, 1, 3, 2},
                                new int[]{0, 1, 4},
                                new int[]{1, 3, 4},
                                new int[]{3, 2, 4},
                                new int[]{2, 0, 4}
                        }
                )
                .edgeColor(CLR(1.0f, 1.0f, 1.0f, 1.0f))
                .fillColor(CLR(0, 0, 0, 0)).buildObject();
    }

    @Override
    public void draw(float[] vpMatrix) {
        object3D.draw(vpMatrix);
    }

    @Override
    public void updateBeforeDraw(float dt) {

    }

    @Override
    public void updateAfterDraw(float dt) {

    }

    @Override
    public void cleanupOnDeath() {
        object3D.cleanup();
    }

    @Override
    public void resetGPUResources() {
        object3D.reload();
    }


}
