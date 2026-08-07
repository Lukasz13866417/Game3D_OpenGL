package com.example.game3d_opengl.rendering.batching;

import java.nio.FloatBuffer;

public final class ScenePassBlockEncoder implements PassBlockEncoder<ScenePassData> {
    public static final ScenePassBlockEncoder INSTANCE = new ScenePassBlockEncoder();

    private static final int FLOATS_PER_PASS = 16 + 4 + 4 + 4 + 4;

    private ScenePassBlockEncoder() {}

    @Override
    public int floatCount() {
        return FLOATS_PER_PASS;
    }

    @Override
    public void encode(ScenePassData pass, FloatBuffer target) {
        target.put(pass.vp);
        target.put(pass.lightX).put(pass.lightY).put(pass.lightZ).put(pass.lightW);
        target.put(pass.lightColorR).put(pass.lightColorG).put(pass.lightColorB).put(pass.lightColorA);
        target.put(pass.cameraX).put(pass.cameraY).put(pass.cameraZ).put(pass.cameraW);
        target.put(pass.themeR).put(pass.themeG).put(pass.themeB).put(pass.themeA);
    }
}
