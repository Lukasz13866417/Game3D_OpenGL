package com.example.game3d_opengl.game.track_elements.spike;

import com.example.game3d_opengl.rendering.mesh.BaseMeshDrawArgs;

public final class SpikeInfillDrawArgs extends BaseMeshDrawArgs {
    public float[] uNL;
    public float[] uNR;
    public float[] uFR;
    public float[] uFL;
    public float[] uApex;
    public float[] uNormal;
    public float uBaseOffset;

    public SpikeInfillDrawArgs(float[] uNL, float[] uNR, float[] uFR, float[] uFL,
                               float[] uApex, float[] uNormal, float uBaseOffset){
        this.uNL = uNL; this.uNR = uNR; this.uFR = uFR; this.uFL = uFL;
        this.uApex = uApex; this.uNormal = uNormal; this.uBaseOffset = uBaseOffset;
    }
}


