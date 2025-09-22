package com.example.game3d_opengl.rendering.object3d.infill;

import com.example.game3d_opengl.rendering.object3d.shader.ShaderArgValues;
import com.example.game3d_opengl.rendering.util3d.FColor;

public final class InfillShaderArgs {

    public static final class VS extends ShaderArgValues {
        public float[] mvp; // not owned
    }

    public static final class FS extends ShaderArgValues {
        public FColor color;
    }
}


