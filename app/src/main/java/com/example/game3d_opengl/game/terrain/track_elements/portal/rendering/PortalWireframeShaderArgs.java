package com.example.game3d_opengl.game.terrain.track_elements.portal.rendering;

import com.example.game3d_opengl.rendering.shader.ShaderArgValues;
import com.example.game3d_opengl.rendering.util3d.FColor;

public final class PortalWireframeShaderArgs {
    public static final class VS extends ShaderArgValues {
        public float[] vp;
        public float centerX, centerY, centerZ;
        public float radius;
        public float[] rotation; // 9-element column-major mat3
        public int viewportW, viewportH;
        public float halfPx;
        public float depthBiasNDC;
    }

    public static final class FS extends ShaderArgValues {
        public FColor color;
    }
}
