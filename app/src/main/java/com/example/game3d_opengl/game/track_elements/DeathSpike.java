package com.example.game3d_opengl.game.track_elements;

import static com.example.game3d_opengl.engine.util3d.FColor.CLR;
import static com.example.game3d_opengl.engine.util3d.GameMath.getNormal;
import static com.example.game3d_opengl.engine.util3d.vector.Vector3D.V3S;

import com.example.game3d_opengl.engine.object3d.Object3D;
import com.example.game3d_opengl.engine.util3d.GameRandom;
import com.example.game3d_opengl.engine.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.WorldActor;
import com.example.game3d_opengl.game.terrain.addon.Addon;

public class DeathSpike extends Addon {

    private final float height;
    private Object3D object3D;
    public DeathSpike(){
        super();
        height = GameRandom.randFloat(0.075f,0.14f,5);
    }
    @Override
    protected void init(Vector3D fieldNearLeft, Vector3D fieldNearRight,
                        Vector3D fieldFarLeft, Vector3D fieldFarRight) {
        Vector3D fieldMid = fieldFarLeft.add(fieldFarRight)
                .add(fieldNearRight).add(fieldNearLeft).div(4);
        Vector3D[] verts = V3S(
                fieldNearLeft, fieldNearRight,
                fieldFarLeft, fieldFarRight
                ,fieldMid.add(getNormal(fieldNearLeft,fieldFarLeft,fieldFarRight).withLen(-height))
        );
        object3D = new Object3D.Builder()
                .angles(0,0,0)
                .position(0,0,0)
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
                .edgeColor(CLR(1.0f,1.0f,1.0f,1.0f))
                .fillColor(CLR(0,0,0,0)).buildObject();
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
}
