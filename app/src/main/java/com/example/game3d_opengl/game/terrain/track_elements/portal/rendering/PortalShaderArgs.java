package com.example.game3d_opengl.game.terrain.track_elements.portal.rendering;

import com.example.game3d_opengl.rendering.shader.ShaderArgValues;

public final class PortalShaderArgs {
    public static final class VS extends ShaderArgValues {
        public float[] mvp;
    }

    public static final class FS extends ShaderArgValues {
        public int textureUnit = 0;
    }
}
