package com.example.game3d_opengl.rendering.infill;

import com.example.game3d_opengl.rendering.shader.ShaderArgValues;
import com.example.game3d_opengl.rendering.util3d.FColor;

public final class InfillShaderArgs {

    public static final class VS extends ShaderArgValues {
        public float[] mvp; // not owned
        public float[] modelMatrix; // separate model matrix for world-space normals/positions
        /** Object-local X-axis rotation sampled through gl_InstanceID. */
        public float spinAngleStartRadians;
        public float spinAngleStepRadians;
        // Optional translation from a retained mesh's storage frame to the current render frame.
        public float positionOffsetX;
        public float positionOffsetY;
        public float positionOffsetZ;
        // Spike-specific per-instance uniforms (optional for other infill shaders)
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
        public float lightX, lightY, lightZ;
        public FColor lightColor;
        public float cameraX, cameraY, cameraZ;
        public float ambient;
        public float diffuse;
        public float specular;
        public float shininess;
        public float opacityMultiplier;
        public int isDepthPass;
    }
}
