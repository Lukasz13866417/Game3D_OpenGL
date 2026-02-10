package com.example.game3d_opengl.rendering.text;

import com.example.game3d_opengl.rendering.shader.ShaderArgValues;

public final class TextShaderArgs {
    public static final class VS extends ShaderArgValues {
        // No per-vertex uniforms needed for screen-space text.
    }

    public static final class FS extends ShaderArgValues {
        public int textureUnit = 0;
    }
}
