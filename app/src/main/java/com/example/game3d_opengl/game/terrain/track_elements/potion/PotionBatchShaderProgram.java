package com.example.game3d_opengl.game.terrain.track_elements.potion;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.batching.AbstractBatchShaderProgram;
import com.example.game3d_opengl.rendering.batching.StaticGeometrySource;

final class PotionBatchShaderProgram extends AbstractBatchShaderProgram {
    private static final int ATTR_POSITION = 0;
    private static final int ATTR_NORMAL = 1;

    private static final String VS =
            "#version 310 es\n" +
            "layout(location = 0) in vec3 aPosition;\n" +
            "layout(location = 1) in vec3 aNormal;\n" +
            "\n" +
            "layout(std140, binding = 0) uniform PassBlock {\n" +
            "    mat4 uVP;\n" +
            "    vec4 uLightPos;\n" +
            "    vec4 uLightColor;\n" +
            "    vec4 uCameraPos;\n" +
            "    vec4 uThemeColor;\n" +
            "};\n" +
            "\n" +
            "struct PotionObject {\n" +
            "    mat4 model;\n" +
            "    vec4 color;\n" +
            "};\n" +
            "\n" +
            "layout(std430, binding = 1) readonly buffer ObjectBlock {\n" +
            "    PotionObject objects[];\n" +
            "};\n" +
            "\n" +
            "out vec3 vWorldPos;\n" +
            "out vec3 vWorldNormal;\n" +
            "out vec4 vColor;\n" +
            "\n" +
            "void main(){\n" +
            "    PotionObject obj = objects[gl_InstanceID];\n" +
            "    vec4 worldPos = obj.model * vec4(aPosition, 1.0);\n" +
            "    vWorldPos = worldPos.xyz;\n" +
            "    vWorldNormal = mat3(obj.model) * aNormal;\n" +
            "    vColor = obj.color;\n" +
            "    gl_Position = uVP * worldPos;\n" +
            "}\n";

    private static final String FS =
            "#version 310 es\n" +
            "precision highp float;\n" +
            "\n" +
            "layout(std140, binding = 0) uniform PassBlock {\n" +
            "    mat4 uVP;\n" +
            "    vec4 uLightPos;\n" +
            "    vec4 uLightColor;\n" +
            "    vec4 uCameraPos;\n" +
            "    vec4 uThemeColor;\n" +
            "};\n" +
            "\n" +
            "in vec3 vWorldPos;\n" +
            "in vec3 vWorldNormal;\n" +
            "in vec4 vColor;\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "void main(){\n" +
            "    vec3 N = normalize(vWorldNormal);\n" +
            "    vec3 V = normalize(uCameraPos.xyz - vWorldPos);\n" +
            "    if (dot(N, V) < 0.0) {\n" +
            "        N = -N;\n" +
            "    }\n" +
            "    vec3 L = normalize(uLightPos.xyz - vWorldPos);\n" +
            "    vec3 H = normalize(L + V);\n" +
            "    float wrappedDiffuse = max(0.0, 0.5 * dot(N, L) + 0.5);\n" +
            "    float NdotH = max(dot(N, H), 0.0);\n" +
            "    float spec = pow(NdotH, 48.0) * wrappedDiffuse;\n" +
            "    vec3 color = vColor.rgb * (0.45 + 0.85 * wrappedDiffuse) + uLightColor.rgb * 1.2 * spec;\n" +
            "    fragColor = vec4(color, vColor.a);\n" +
            "}\n";

    PotionBatchShaderProgram() {
        super(VS, FS);
        initProgram();
    }

    @Override
    protected void onProgramLinked() {
        // Explicit attribute locations and buffer bindings are encoded in GLSL.
    }

    @Override
    public void bindGeometryAttributes(StaticGeometrySource geometry) {
        final int stride = geometry.strideBytes();
        GLES20.glEnableVertexAttribArray(ATTR_POSITION);
        GLES20.glVertexAttribPointer(ATTR_POSITION, 3, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(ATTR_NORMAL);
        GLES20.glVertexAttribPointer(ATTR_NORMAL, 3, GLES20.GL_FLOAT, false, stride, 3 * 4);
    }

    @Override
    public void disableGeometryAttributes() {
        GLES20.glDisableVertexAttribArray(ATTR_POSITION);
        GLES20.glDisableVertexAttribArray(ATTR_NORMAL);
    }
}
