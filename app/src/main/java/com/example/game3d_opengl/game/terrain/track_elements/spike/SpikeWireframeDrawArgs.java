package com.example.game3d_opengl.game.terrain.track_elements.spike;

import com.example.game3d_opengl.rendering.ScreenInfo;
import com.example.game3d_opengl.rendering.mesh.BaseMeshDrawArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;

public final class SpikeWireframeDrawArgs extends BaseMeshDrawArgs {
    public int viewportW;
    public int viewportH;
    public float halfPx;
    public float capPx;
    public float uDepthBiasNDC;

    public float[] uNL;
    public float[] uNR;
    public float[] uFR;
    public float[] uFL;
    public float[] uApex;
    public float[] uNormal;
    public float uBaseOffset;
    public FColor color;

    public SpikeWireframeDrawArgs(float halfPx, float uDepthBiasNDC,
                                  float[] uNL, float[] uNR, float[] uFR, float[] uFL,
                                  float[] uApex, float[] uNormal, float uBaseOffset, FColor color){
        this.viewportW = ScreenInfo.getScreenW();
        this.viewportH = ScreenInfo.getScreenH();
        this.halfPx = halfPx;
        this.capPx = halfPx;
        this.uDepthBiasNDC = uDepthBiasNDC;
        this.uNL = uNL; this.uNR = uNR; this.uFR = uFR; this.uFL = uFL;
        this.uApex = uApex; this.uNormal = uNormal; this.uBaseOffset = uBaseOffset;
        this.color = color;
    }
}


