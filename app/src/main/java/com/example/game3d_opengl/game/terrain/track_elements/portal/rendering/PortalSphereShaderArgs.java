package com.example.game3d_opengl.game.terrain.track_elements.portal.rendering;

import com.example.game3d_opengl.rendering.shader.ShaderArgValues;
import com.example.game3d_opengl.rendering.util3d.FColor;

public final class PortalSphereShaderArgs {
    public static final class VS extends ShaderArgValues {
        public float[] vp;
        public float centerX, centerY, centerZ;
        public float scaleX, scaleY, scaleZ;
        public float[] rotation; // 9-element column-major mat3
    }

    public static final class FS extends ShaderArgValues {
        public FColor colorA;
        public FColor colorB;
        public float lightX, lightY, lightZ;
        public FColor lightColor;
        public float cameraX, cameraY, cameraZ;
        public float ambient;
        public float diffuse;
        public float specular;
        public float shininess;
    }
}
