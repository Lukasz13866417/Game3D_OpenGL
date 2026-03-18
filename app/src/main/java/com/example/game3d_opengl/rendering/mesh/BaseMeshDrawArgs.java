package com.example.game3d_opengl.rendering.mesh;

public abstract class BaseMeshDrawArgs {
    public float[] vp; // can also be set to an MVP
    public float[] model; // separate model matrix for world-space lighting (optional)
}
