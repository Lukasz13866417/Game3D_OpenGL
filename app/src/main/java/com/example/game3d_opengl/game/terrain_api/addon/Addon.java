package com.example.game3d_opengl.game.terrain_api.addon;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;
import com.example.game3d_opengl.game.WorldActor;

public abstract class Addon implements WorldActor {
    private boolean ready;
    public Addon(){
        this.ready = false;
    }
    protected abstract void init(Vector3D fieldNearLeft, Vector3D fieldNearRight,
                                 Vector3D fieldFarLeft, Vector3D fieldFarRight);
    public void place(Vector3D fieldNearLeft, Vector3D fieldNearRight,
                      Vector3D fieldFarLeft, Vector3D fieldFarRight){
        init(fieldNearLeft,  fieldNearRight, fieldFarLeft, fieldFarRight);
        this.ready = true;
    }

    public abstract void draw(float[] vpMatrix);
}
