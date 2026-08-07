package com.example.game3d_opengl.rendering.mesh;

public abstract class BaseMeshDrawArgs {
    public float[] vp; // can also be set to an MVP
    public float[] model; // separate model matrix for world-space lighting (optional)

    /**
     * Optional object-local spin sampling. Ordinary draws keep these defaults and issue one
     * unmodified instance; the player blur path supplies several angular samples in one draw.
     */
    public int instanceCount = 1;
    public float spinAngleStartRadians = 0f;
    public float spinAngleStepRadians = 0f;
    public float opacityMultiplier = 1f;
}
