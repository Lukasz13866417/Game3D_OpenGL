package com.example.game3d_opengl.game.terrain.track_elements.spike;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.layout.VertexLayout;
import com.example.game3d_opengl.rendering.shader.MeshShaderPair;
import com.example.game3d_opengl.rendering.shader.ShaderPair;

/**
 * Shader for spikes with non-affine bases and Blinn-Phong lighting.
 * Vertex positions are computed from canonical (weights + t) and per-instance
 * quad corners + apex. Each face also carries its two base corners so the
 * vertex shader can reconstruct a stable per-face normal and anchor point.
 */
public final class SpikeInfillShaderPair
        <L extends VertexLayout.HasWeights
                & VertexLayout.HasT
                & VertexLayout.HasFaceBaseAWeights
                & VertexLayout.HasFaceBaseBWeights>
        extends MeshShaderPair<InfillShaderArgs.VS, InfillShaderArgs.FS, L> {

    private int uMVP, uColor;
    private int uNL, uNR, uFR, uFL, uApex, uNormal, uBaseOffset;
    private int uLightPos, uLightColor, uCameraPos, uSpecular, uShininess;
    private int aWeights, aT, aFaceBaseAWeights, aFaceBaseBWeights;

    private SpikeInfillShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static SpikeInfillShaderPair<VertexLayout.SpikeCanonicalFillLayout> sharedShader = null;

    public static SpikeInfillShaderPair<VertexLayout.SpikeCanonicalFillLayout> getSharedShader() {
        if (sharedShader == null) {
            throw new IllegalStateException(
                    "Shader instance is null. Needs calling LOAD_SHADER_CODE first");
        }
        return sharedShader;
    }

    public static void LOAD_SHADER_CODE() {
        String vs =
                "#version 300 es\n" +
                "uniform mat4 uMVPMatrix;\n" +
                "uniform vec3 uNL, uNR, uFR, uFL;\n" +
                "uniform vec3 uApex;\n" +
                "uniform vec3 uNormal;\n" +
                "uniform float uBaseOffset;\n" +
                "in vec4 aWeights;\n" +
                "in float aT;\n" +
                "in vec4 aFaceBaseAWeights;\n" +
                "in vec4 aFaceBaseBWeights;\n" +
                "flat out vec3 vFaceAnchorPos;\n" +
                "flat out vec3 vFaceNormal;\n" +
                "vec3 safeNormalize(vec3 v, vec3 fallback){\n" +
                "  float lenSq = dot(v, v);\n" +
                "  return (lenSq > 1e-8) ? v * inversesqrt(lenSq) : fallback;\n" +
                "}\n" +
                "vec3 mapBase(vec4 w){\n" +
                "  vec3 pBase = w.x * uNL + w.y * uNR + w.z * uFR + w.w * uFL;\n" +
                "  return pBase + uNormal * uBaseOffset;\n" +
                "}\n" +
                "vec3 mapWorld(vec4 w, float t){\n" +
                "  return mix(mapBase(w), uApex, t);\n" +
                "}\n" +
                "void main(){\n" +
                "  vec3 worldPos = mapWorld(aWeights, aT);\n" +
                "  vec3 faceBaseA = mapBase(aFaceBaseAWeights);\n" +
                "  vec3 faceBaseB = mapBase(aFaceBaseBWeights);\n" +
                "  vec3 faceNormal = safeNormalize(cross(faceBaseB - faceBaseA, uApex - faceBaseA), uNormal);\n" +
                "  if (dot(faceNormal, uNormal) < 0.0) {\n" +
                "    faceNormal = -faceNormal;\n" +
                "  }\n" +
                "  vFaceNormal = faceNormal;\n" +
                "  vFaceAnchorPos = (faceBaseA + faceBaseB + uApex) / 3.0;\n" +
                "  gl_Position = uMVPMatrix * vec4(worldPos, 1.0);\n" +
                "}";
        String fs =
                "#version 300 es\n" +
                "precision highp float;\n" +
                "uniform vec4 vColor;\n" +
                "uniform vec3 uLightPos;\n" +
                "uniform vec3 uLightColor;\n" +
                "uniform vec3 uCameraPos;\n" +
                "uniform float uSpecular;\n" +
                "uniform float uShininess;\n" +
                "flat in vec3 vFaceAnchorPos;\n" +
                "flat in vec3 vFaceNormal;\n" +
                "out vec4 fragColor;\n" +
                "vec3 safeNormalize(vec3 v, vec3 fallback){\n" +
                "  float lenSq = dot(v, v);\n" +
                "  return (lenSq > 1e-8) ? v * inversesqrt(lenSq) : fallback;\n" +
                "}\n" +
                "void main(){\n" +
                "  vec3 N = safeNormalize(vFaceNormal, vec3(0.0, 1.0, 0.0));\n" +
                "  vec3 V = safeNormalize(uCameraPos - vFaceAnchorPos, N);\n" +
                "  vec3 L = safeNormalize(uLightPos - vFaceAnchorPos, N);\n" +
                "  float playerFacing = clamp(0.5 + 0.5 * dot(N, V), 0.0, 1.0);\n" +
                "  float lightFacing = max(dot(N, L), 0.0);\n" +
                "  float shade = min(0.90, mix(0.58, 0.82, playerFacing) + 0.06 * lightFacing);\n" +
                "  vec3 color = vColor.rgb * shade;\n" +
                "  fragColor = vec4(color, vColor.a);\n" +
                "}";
        sharedShader = new Builder().fromSource(vs, fs).build();
    }

    @Override
    protected void enableAndPointVertexAttribs(L layout) {
        final int stride = layout.strideBytes();
        layout.weights().enableAndPoint(aWeights, stride);
        layout.t().enableAndPoint(aT, stride);
        layout.faceBaseAWeights().enableAndPoint(aFaceBaseAWeights, stride);
        layout.faceBaseBWeights().enableAndPoint(aFaceBaseBWeights, stride);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aWeights);
        GLES20.glDisableVertexAttribArray(aT);
        GLES20.glDisableVertexAttribArray(aFaceBaseAWeights);
        GLES20.glDisableVertexAttribArray(aFaceBaseBWeights);
    }

    @Override
    protected void setupAttribLocations() {
        int p = getProgramHandle();
        this.uMVP = GLES20.glGetUniformLocation(p, "uMVPMatrix");
        this.uColor = GLES20.glGetUniformLocation(p, "vColor");
        this.uNL = GLES20.glGetUniformLocation(p, "uNL");
        this.uNR = GLES20.glGetUniformLocation(p, "uNR");
        this.uFR = GLES20.glGetUniformLocation(p, "uFR");
        this.uFL = GLES20.glGetUniformLocation(p, "uFL");
        this.uApex = GLES20.glGetUniformLocation(p, "uApex");
        this.uNormal = GLES20.glGetUniformLocation(p, "uNormal");
        this.uBaseOffset = GLES20.glGetUniformLocation(p, "uBaseOffset");
        this.uLightPos = GLES20.glGetUniformLocation(p, "uLightPos");
        this.uLightColor = GLES20.glGetUniformLocation(p, "uLightColor");
        this.uCameraPos = GLES20.glGetUniformLocation(p, "uCameraPos");
        this.uSpecular = GLES20.glGetUniformLocation(p, "uSpecular");
        this.uShininess = GLES20.glGetUniformLocation(p, "uShininess");
        this.aWeights = GLES20.glGetAttribLocation(p, "aWeights");
        this.aT = GLES20.glGetAttribLocation(p, "aT");
        this.aFaceBaseAWeights = GLES20.glGetAttribLocation(p, "aFaceBaseAWeights");
        this.aFaceBaseBWeights = GLES20.glGetAttribLocation(p, "aFaceBaseBWeights");
    }

    @Override
    protected void transferUniformArgsToGPU(InfillShaderArgs.VS vertexArgs, InfillShaderArgs.FS fragmentArgs) {
        if (vertexArgs == null || fragmentArgs == null
                || vertexArgs.mvp == null || fragmentArgs.color == null
                || vertexArgs.uNL == null || vertexArgs.uNR == null
                || vertexArgs.uFR == null || vertexArgs.uFL == null
                || vertexArgs.uApex == null || vertexArgs.uNormal == null) {
            throw new IllegalArgumentException("SpikeInfillShaderPair:" +
                    " all per-instance uniforms must be provided");
        }

        GLES20.glUniformMatrix4fv(uMVP, 1, false, vertexArgs.mvp, 0);
        GLES20.glUniform4fv(uColor, 1, fragmentArgs.color.rgba, 0);
        GLES20.glUniform3fv(uNL, 1, vertexArgs.uNL, 0);
        GLES20.glUniform3fv(uNR, 1, vertexArgs.uNR, 0);
        GLES20.glUniform3fv(uFR, 1, vertexArgs.uFR, 0);
        GLES20.glUniform3fv(uFL, 1, vertexArgs.uFL, 0);
        GLES20.glUniform3fv(uApex, 1, vertexArgs.uApex, 0);
        GLES20.glUniform3fv(uNormal, 1, vertexArgs.uNormal, 0);
        GLES20.glUniform1f(uBaseOffset, vertexArgs.uBaseOffset);

        GLES20.glUniform3f(uLightPos, fragmentArgs.lightX, fragmentArgs.lightY, fragmentArgs.lightZ);
        if (fragmentArgs.lightColor != null) {
            GLES20.glUniform3f(uLightColor,
                    fragmentArgs.lightColor.r(), fragmentArgs.lightColor.g(), fragmentArgs.lightColor.b());
        } else {
            GLES20.glUniform3f(uLightColor, 1f, 1f, 1f);
        }
        GLES20.glUniform3f(uCameraPos, fragmentArgs.cameraX, fragmentArgs.cameraY, fragmentArgs.cameraZ);
        GLES20.glUniform1f(uSpecular, fragmentArgs.specular);
        GLES20.glUniform1f(uShininess, Math.max(1f, fragmentArgs.shininess));
    }

    public static final class Builder
            extends ShaderPair.BaseBuilder<SpikeInfillShaderPair<VertexLayout.SpikeCanonicalFillLayout>, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected SpikeInfillShaderPair<VertexLayout.SpikeCanonicalFillLayout> create(
                int programHandle, String vs, String fs) {
            return new SpikeInfillShaderPair<>(programHandle, vs, fs);
        }
    }
}
