package com.example.game3d_opengl.game;

import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class LightSource {
    public Vector3D position;
    public FColor color;

    public LightSource(FColor color) {
        this.color = color;
    }

    public void setPosition(Vector3D position) {
        this.position = position;
    }
}
