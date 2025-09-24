package com.example.game3d_opengl.game.track_elements.spike;

import com.example.game3d_opengl.rendering.object3d.shader.ShaderArgValues;
import com.example.game3d_opengl.rendering.util3d.FColor;

public final class SpikeWireframeShaderArgs {

    public static final class VS extends ShaderArgValues {
        public float[] mvp; // 4x4 column-major
        public int viewportW;
        public int viewportH;
        public float halfPx;
        public float uDepthBiasNDC;

        // per-instance spike uniforms
        public float[] uNL;      // length 3
        public float[] uNR;      // length 3
        public float[] uFR;      // length 3
        public float[] uFL;      // length 3
        public float[] uApex;    // length 3
        public float[] uNormal;  // length 3
        public float   uBaseOffset;
    }

    public static final class FS extends ShaderArgValues {
        public FColor color;
    }
}


