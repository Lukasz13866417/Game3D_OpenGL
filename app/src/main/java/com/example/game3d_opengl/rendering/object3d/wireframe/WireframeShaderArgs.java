package com.example.game3d_opengl.rendering.object3d.wireframe;

import com.example.game3d_opengl.rendering.object3d.shader.ShaderArgValues;
import com.example.game3d_opengl.rendering.util3d.FColor;

public final class WireframeShaderArgs {

    public static final class VS extends ShaderArgValues {
        public float[] mvp; // not owned
        public int viewportW;
        public int viewportH;
        public float halfPx;

        public FColor color;

        public float uDepthBiasNDC;
    }

    public static final class FS extends ShaderArgValues {
        public FColor color;
    }
}


