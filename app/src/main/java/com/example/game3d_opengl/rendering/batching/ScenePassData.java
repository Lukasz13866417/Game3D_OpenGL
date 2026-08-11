package com.example.game3d_opengl.rendering.batching;

import java.util.Arrays;

public final class ScenePassData {
    public final float[] vp = new float[16];
    public float lightX;
    public float lightY;
    public float lightZ;
    public float lightW = 1f;
    public float lightColorR = 1f;
    public float lightColorG = 1f;
    public float lightColorB = 1f;
    public float lightColorA = 1f;
    public float cameraX;
    public float cameraY;
    public float cameraZ;
    public float cameraW = 1f;
    public float themeR = 1f;
    public float themeG = 1f;
    public float themeB = 1f;
    public float themeA = 1f;

    public void setVp(float[] sourceVp) {
        if (sourceVp == null || sourceVp.length < 16) {
            throw new IllegalArgumentException("sourceVp must contain 16 floats");
        }
        System.arraycopy(sourceVp, 0, vp, 0, 16);
    }

    public void reset() {
        Arrays.fill(vp, 0f);
        lightX = 0f;
        lightY = 0f;
        lightZ = 0f;
        lightW = 1f;
        lightColorR = 1f;
        lightColorG = 1f;
        lightColorB = 1f;
        lightColorA = 1f;
        cameraX = 0f;
        cameraY = 0f;
        cameraZ = 0f;
        cameraW = 1f;
        themeR = 1f;
        themeG = 1f;
        themeB = 1f;
        themeA = 1f;
    }
}
