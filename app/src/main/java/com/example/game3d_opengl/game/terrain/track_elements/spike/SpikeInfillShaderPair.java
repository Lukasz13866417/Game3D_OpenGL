package com.example.game3d_opengl.game.terrain.track_elements.spike;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.shader.ShaderPair;

/**
 * Shader for spikes with non-affine bases and Blinn-Phong lighting.
 * Vertex positions are computed from canonical (weights + t) and per-instance
 * quad corners + apex. For stable flat shading, each face also carries a
 * per-face anchor point used to keep light/view vectors constant per face.
 * Face normals come from derivatives when available; otherwise it falls back
 * to the provided base normal.
 */
public final class SpikeInfillShaderPair
        extends ShaderPair<InfillShaderArgs.VS, InfillShaderArgs.FS> {

    private int uMVP, uColor;
    private int uNL, uNR, uFR, uFL, uApex, uNormal, uBaseOffset;
    private int uLightPos, uLightColor, uCameraPos, uSpecular, uShininess;
    private int aWeights, aT, aFaceAnchorWeights, aFaceAnchorT;

    private SpikeInfillShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static SpikeInfillShaderPair sharedShader = null;

    public static SpikeInfillShaderPair getSharedShader() {
        if (sharedShader == null) {
            throw new IllegalStateException(
                    "Shader instance is null. Needs calling LOAD_SHADER_CODE first");
        }
        return sharedShader;
    }

    public static void LOAD_SHADER_CODE() {
        String vs =
                "uniform mat4 uMVPMatrix;\n" +
                "uniform vec3 uNL, uNR, uFR, uFL;\n" +
                "uniform vec3 uApex;\n" +
                "uniform vec3 uNormal;\n" +
                "uniform float uBaseOffset;\n" +
                "attribute vec4 aWeights;\n" +
                "attribute float aT;\n" +
                "attribute vec4 aFaceAnchorWeights;\n" +
                "attribute float aFaceAnchorT;\n" +
                "varying vec3 vWorldPos;\n" +
                "varying vec3 vFaceAnchorPos;\n" +
                "varying vec3 vFallbackNormal;\n" +
                "vec3 mapWorld(vec4 w, float t){\n" +
                "  vec3 pBase = w.x * uNL + w.y * uNR + w.z * uFR + w.w * uFL;\n" +
                "  return mix(pBase + uNormal * uBaseOffset, uApex, t);\n" +
                "}\n" +
                "void main(){\n" +
                "  vec3 worldPos = mapWorld(aWeights, aT);\n" +
                "  vWorldPos = worldPos;\n" +
                "  vFaceAnchorPos = mapWorld(aFaceAnchorWeights, aFaceAnchorT);\n" +
                "  vFallbackNormal = -uNormal;\n" +
                "  gl_Position = uMVPMatrix * vec4(worldPos, 1.0);\n" +
                "}";
        String fs =
                "#ifdef GL_OES_standard_derivatives\n" +
                "#extension GL_OES_standard_derivatives : enable\n" +
                "#endif\n" +
                "precision highp float;\n" +
                "uniform vec4 vColor;\n" +
                "uniform vec3 uLightPos;\n" +
                "uniform vec3 uLightColor;\n" +
                "uniform vec3 uCameraPos;\n" +
                "uniform float uSpecular;\n" +
                "uniform float uShininess;\n" +
                "varying vec3 vWorldPos;\n" +
                "varying vec3 vFaceAnchorPos;\n" +
                "varying vec3 vFallbackNormal;\n" +
                "void main(){\n" +
                "  vec3 fallbackN = normalize(vFallbackNormal);\n" +
                "  vec3 N = fallbackN;\n" +
                "#ifdef GL_OES_standard_derivatives\n" +
                "  vec3 dPosDx = dFdx(vWorldPos);\n" +
                "  vec3 dPosDy = dFdy(vWorldPos);\n" +
                "  vec3 faceN = normalize(cross(dPosDx, dPosDy));\n" +
                "  if (dot(faceN, faceN) > 1e-8) N = faceN;\n" +
                "#endif\n" +
                "  vec3 L = normalize(uLightPos - vFaceAnchorPos);\n" +
                "  vec3 V = normalize(uCameraPos - vFaceAnchorPos);\n" +
                "  if (dot(N, L) < 0.0) N = -N;\n" +
                "  vec3 H = normalize(L + V);\n" +
                "  float NdotL = max(dot(N, L), 0.0);\n" +
                "  float NdotH = max(dot(N, H), 0.0);\n" +
                "  float spec = (NdotL > 0.0) ? pow(NdotH, uShininess) : 0.0;\n" +
                "  vec3 color = vColor.rgb * (0.2 + 1.2 * NdotL) + uLightColor * uSpecular * spec;\n" +
                "  gl_FragColor = vec4(color, vColor.a);\n" +
                "}";
        sharedShader = new Builder().fromSource(vs, fs).build();
    }

    @Override
    public void enableAndPointVertexAttribs() {
        GLES20.glEnableVertexAttribArray(aWeights);
        final int stride = 10 * 4;
        GLES20.glVertexAttribPointer(aWeights, 4, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(aT);
        GLES20.glVertexAttribPointer(aT, 1, GLES20.GL_FLOAT, false, stride, 4 * 4);
        GLES20.glEnableVertexAttribArray(aFaceAnchorWeights);
        GLES20.glVertexAttribPointer(aFaceAnchorWeights, 4, GLES20.GL_FLOAT, false, stride, 5 * 4);
        GLES20.glEnableVertexAttribArray(aFaceAnchorT);
        GLES20.glVertexAttribPointer(aFaceAnchorT, 1, GLES20.GL_FLOAT, false, stride, 9 * 4);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aWeights);
        GLES20.glDisableVertexAttribArray(aT);
        GLES20.glDisableVertexAttribArray(aFaceAnchorWeights);
        GLES20.glDisableVertexAttribArray(aFaceAnchorT);
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
        this.aFaceAnchorWeights = GLES20.glGetAttribLocation(p, "aFaceAnchorWeights");
        this.aFaceAnchorT = GLES20.glGetAttribLocation(p, "aFaceAnchorT");
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

    public static final class Builder extends ShaderPair.BaseBuilder<SpikeInfillShaderPair, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected SpikeInfillShaderPair create(int programHandle, String vs, String fs) {
            return new SpikeInfillShaderPair(programHandle, vs, fs);
        }
    }
}
