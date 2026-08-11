package com.example.game3d_opengl.game.progress_bar;

import com.example.game3d_opengl.rendering.shader.ShaderArgValues;
import com.example.game3d_opengl.rendering.util3d.FColor;

public final class ProgressBarFillShaderArgs {
    public static final class VS extends ShaderArgValues {
        public float[] mvp; // not owned
    }

    public static final class FS extends ShaderArgValues {
        public FColor color;
        public float progress;
    }
}
