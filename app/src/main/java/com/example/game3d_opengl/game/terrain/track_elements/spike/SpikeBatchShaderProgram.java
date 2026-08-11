package com.example.game3d_opengl.game.terrain.track_elements.spike;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.batching.AbstractBatchShaderProgram;
import com.example.game3d_opengl.rendering.batching.StaticGeometrySource;

final class SpikeBatchShaderProgram extends AbstractBatchShaderProgram {
    private static final int ATTR_WEIGHTS = 0;
    private static final int ATTR_T = 1;
    private static final int ATTR_FACE_BASE_A_WEIGHTS = 2;
    private static final int ATTR_FACE_BASE_B_WEIGHTS = 3;

    private static final String VS =
            "#version 310 es\n" +
            "layout(location = 0) in vec4 aWeights;\n" +
            "layout(location = 1) in float aT;\n" +
            "layout(location = 2) in vec4 aFaceBaseAWeights;\n" +
            "layout(location = 3) in vec4 aFaceBaseBWeights;\n" +
            "\n" +
            "layout(std140, binding = 0) uniform PassBlock {\n" +
            "    mat4 uVP;\n" +
            "    vec4 uLightPos;\n" +
            "    vec4 uLightColor;\n" +
            "    vec4 uCameraPos;\n" +
            "    vec4 uThemeColor;\n" +
            "};\n" +
            "\n" +
            "struct SpikeObject {\n" +
            "    vec4 nl;\n" +
            "    vec4 nr;\n" +
            "    vec4 fr;\n" +
            "    vec4 fl;\n" +
            "    vec4 apex;\n" +
            "    vec4 normalAndBaseOffset;\n" +
            "};\n" +
            "\n" +
            "layout(std430, binding = 1) readonly buffer ObjectBlock {\n" +
            "    SpikeObject objects[];\n" +
            "};\n" +
            "\n" +
            "flat out vec3 vFaceAnchorPos;\n" +
            "flat out vec3 vFaceNormal;\n" +
            "\n" +
            "vec3 safeNormalize(vec3 v, vec3 fallback) {\n" +
            "    float lenSq = dot(v, v);\n" +
            "    return (lenSq > 1e-8) ? v * inversesqrt(lenSq) : fallback;\n" +
            "}\n" +
            "\n" +
            "vec3 mapBase(SpikeObject obj, vec4 w) {\n" +
            "    vec3 pBase = w.x * obj.nl.xyz + w.y * obj.nr.xyz + w.z * obj.fr.xyz + w.w * obj.fl.xyz;\n" +
            "    return pBase + obj.normalAndBaseOffset.xyz * obj.normalAndBaseOffset.w;\n" +
            "}\n" +
            "\n" +
            "vec3 mapWorld(SpikeObject obj, vec4 w, float t) {\n" +
            "    return mix(mapBase(obj, w), obj.apex.xyz, t);\n" +
            "}\n" +
            "\n" +
            "void main(){\n" +
            "    SpikeObject obj = objects[gl_InstanceID];\n" +
            "    vec3 worldPos = mapWorld(obj, aWeights, aT);\n" +
            "    vec3 faceBaseA = mapBase(obj, aFaceBaseAWeights);\n" +
            "    vec3 faceBaseB = mapBase(obj, aFaceBaseBWeights);\n" +
            "    vec3 faceNormal = safeNormalize(\n" +
            "            cross(faceBaseB - faceBaseA, obj.apex.xyz - faceBaseA),\n" +
            "            obj.normalAndBaseOffset.xyz);\n" +
            "    if (dot(faceNormal, obj.normalAndBaseOffset.xyz) < 0.0) {\n" +
            "        faceNormal = -faceNormal;\n" +
            "    }\n" +
            "    vFaceNormal = faceNormal;\n" +
            "    vFaceAnchorPos = (faceBaseA + faceBaseB + obj.apex.xyz) / 3.0;\n" +
            "    gl_Position = uVP * vec4(worldPos, 1.0);\n" +
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
            "flat in vec3 vFaceAnchorPos;\n" +
            "flat in vec3 vFaceNormal;\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "vec3 safeNormalize(vec3 v, vec3 fallback) {\n" +
            "    float lenSq = dot(v, v);\n" +
            "    return (lenSq > 1e-8) ? v * inversesqrt(lenSq) : fallback;\n" +
            "}\n" +
            "\n" +
            "void main(){\n" +
            "    vec3 N = safeNormalize(vFaceNormal, vec3(0.0, 1.0, 0.0));\n" +
            "    vec3 V = safeNormalize(uCameraPos.xyz - vFaceAnchorPos, N);\n" +
            "    vec3 L = safeNormalize(uLightPos.xyz - vFaceAnchorPos, N);\n" +
            "    float playerFacing = clamp(0.5 + 0.5 * dot(N, V), 0.0, 1.0);\n" +
            "    float lightFacing = max(dot(N, L), 0.0);\n" +
            "    float shade = min(0.90, mix(0.58, 0.82, playerFacing) + 0.06 * lightFacing);\n" +
            "    vec3 color = uThemeColor.rgb * shade;\n" +
            "    fragColor = vec4(color, 1.0);\n" +
            "}\n";

    SpikeBatchShaderProgram() {
        super(VS, FS);
        initProgram();
    }

    @Override
    protected void onProgramLinked() {
        // Explicit attribute locations and binding points are encoded in GLSL.
    }

    @Override
    public void bindGeometryAttributes(StaticGeometrySource geometry) {
        final int stride = geometry.strideBytes();
        GLES20.glEnableVertexAttribArray(ATTR_WEIGHTS);
        GLES20.glVertexAttribPointer(ATTR_WEIGHTS, 4, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(ATTR_T);
        GLES20.glVertexAttribPointer(ATTR_T, 1, GLES20.GL_FLOAT, false, stride, 4 * 4);
        GLES20.glEnableVertexAttribArray(ATTR_FACE_BASE_A_WEIGHTS);
        GLES20.glVertexAttribPointer(ATTR_FACE_BASE_A_WEIGHTS, 4, GLES20.GL_FLOAT, false, stride, 5 * 4);
        GLES20.glEnableVertexAttribArray(ATTR_FACE_BASE_B_WEIGHTS);
        GLES20.glVertexAttribPointer(ATTR_FACE_BASE_B_WEIGHTS, 4, GLES20.GL_FLOAT, false, stride, 9 * 4);
    }

    @Override
    public void disableGeometryAttributes() {
        GLES20.glDisableVertexAttribArray(ATTR_WEIGHTS);
        GLES20.glDisableVertexAttribArray(ATTR_T);
        GLES20.glDisableVertexAttribArray(ATTR_FACE_BASE_A_WEIGHTS);
        GLES20.glDisableVertexAttribArray(ATTR_FACE_BASE_B_WEIGHTS);
    }
}
