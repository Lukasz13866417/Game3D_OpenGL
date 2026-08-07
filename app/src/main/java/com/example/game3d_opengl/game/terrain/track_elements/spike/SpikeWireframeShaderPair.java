package com.example.game3d_opengl.game.terrain.track_elements.spike;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.layout.VertexLayout;
import com.example.game3d_opengl.rendering.shader.MeshShaderPair;
import com.example.game3d_opengl.rendering.shader.ShaderPair;

/**
 * Wireframe shader for spikes that maps canonical spike endpoints (weights + t)
 * to world space using per-instance uniforms, then expands edges to a screen-space
 * quad to achieve constant pixel thickness.
 */
public final class SpikeWireframeShaderPair
        <L extends VertexLayout.HasWeightsA
                & VertexLayout.HasTA
                & VertexLayout.HasWeightsB
                & VertexLayout.HasTB
                & VertexLayout.HasEdgeEnd
                & VertexLayout.HasEdgeSide>
        extends MeshShaderPair<SpikeWireframeShaderArgs.VS, SpikeWireframeShaderArgs.FS, L> {

    // Uniforms
    private int uMVP, uViewport, uHalfPx, uCapPx, uDepthBiasNDC, uColor;
    private int uNL, uNR, uFR, uFL, uApex, uNormal, uBaseOffset;

    // Attributes
    private int aWeightsA, aTA, aWeightsB, aTB, aEnd, aSide;

    public static SpikeWireframeShaderPair<VertexLayout.SpikeCanonicalWireframeLayout> sharedShader = null;

    private SpikeWireframeShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static SpikeWireframeShaderPair<VertexLayout.SpikeCanonicalWireframeLayout> getSharedShader(){
        if (sharedShader == null){
            throw new IllegalStateException(
                    "Shader instance is null. Needs calling LOAD_SHADER_CODE first"
            );
        }
        return sharedShader;
    }

    public static void LOAD_SHADER_CODE() {
        String vs =
                "#version 300 es\n" +
                "uniform mat4 uMVPMatrix;\n" +
                "uniform vec2 uViewport;\n" +
                "uniform float uHalfPx;\n" +
                "uniform float uCapPx;\n" +
                "uniform float uDepthBiasNDC;\n" +
                "uniform vec3 uNL, uNR, uFR, uFL;\n" +
                "uniform vec3 uApex;\n" +
                "uniform vec3 uNormal;\n" +
                "uniform float uBaseOffset;\n" +
                "in vec4 aWeightsA;\n" +
                "in float aTA;\n" +
                "in vec4 aWeightsB;\n" +
                "in float aTB;\n" +
                "in float aEnd;\n" +
                "in float aSide;\n" +
                "vec2 ndc(vec4 clip){ return clip.xy / clip.w; }\n" +
                "out vec2 vA_ndc;\n" +
                "out vec2 vB_ndc;\n" +
                "void main(){\n" +
                "  vec3 pBaseA = aWeightsA.x * uNL + aWeightsA.y * uNR + aWeightsA.z * uFR + aWeightsA.w * uFL;\n" +
                "  vec3 worldA = mix(pBaseA + uNormal * uBaseOffset, uApex, aTA);\n" +
                "  vec3 pBaseB = aWeightsB.x * uNL + aWeightsB.y * uNR + aWeightsB.z * uFR + aWeightsB.w * uFL;\n" +
                "  vec3 worldB = mix(pBaseB + uNormal * uBaseOffset, uApex, aTB);\n" +
                "  vec4 A_clip = uMVPMatrix * vec4(worldA, 1.0);\n" +
                "  vec4 B_clip = uMVPMatrix * vec4(worldB, 1.0);\n" +
                "  vec2 A_ndc = ndc(A_clip);\n" +
                "  vec2 B_ndc = ndc(B_clip);\n" +
                "  vec2 ndc2px = 0.5 * uViewport;\n" +
                "  vec2 d_pix = (B_ndc - A_ndc) * ndc2px;\n" +
                "  float l2 = dot(d_pix, d_pix);\n" +
                "  vec2 n_pix = (l2 > 1e-8) ? normalize(vec2(-d_pix.y, d_pix.x)) : vec2(0.0);\n" +
                "  vec2 dir_pix = (l2 > 1e-8) ? normalize(d_pix) : vec2(0.0);\n" +
                "  vec2 delta_ndc = (uHalfPx * n_pix) / ndc2px;\n" +
                "  vec2 cap_ndc = (uCapPx * dir_pix) / ndc2px;\n" +
                "  vec2 A_ext = A_ndc - cap_ndc;\n" +
                "  vec2 B_ext = B_ndc + cap_ndc;\n" +
                "  vec4 P_clip = mix(A_clip, B_clip, aEnd);\n" +
                "  vec2 P_ndc  = mix(A_ext,  B_ext,  aEnd);\n" +
                "  vec2 out_ndc = P_ndc + aSide * delta_ndc;\n" +
                "  vA_ndc = A_ndc;\n" +
                "  vB_ndc = B_ndc;\n" +
                "  gl_Position = vec4(out_ndc * P_clip.w, P_clip.z, P_clip.w);\n" +
                "  gl_Position.z += uDepthBiasNDC * gl_Position.w;\n" +
                "}";

        String fs =
                "#version 300 es\n" +
                "precision mediump float;\n" +
                "uniform vec4 uColor;\n" +
                "uniform vec2 uViewport;\n" +
                "uniform float uHalfPx;\n" +
                "in vec2 vA_ndc;\n" +
                "in vec2 vB_ndc;\n" +
                "out vec4 fragColor;\n" +
                "void main(){\n" +
                "  vec2 A_px = (vA_ndc * 0.5 + 0.5) * uViewport;\n" +
                "  vec2 B_px = (vB_ndc * 0.5 + 0.5) * uViewport;\n" +
                "  vec2 P_px = gl_FragCoord.xy;\n" +
                "  vec2 AB = B_px - A_px;\n" +
                "  float len2 = dot(AB, AB);\n" +
                "  float t = 0.0;\n" +
                "  if (len2 > 1e-6) {\n" +
                "    t = dot(P_px - A_px, AB) / len2;\n" +
                "  }\n" +
                "  float dist;\n" +
                "  if (t < 0.0) {\n" +
                "    dist = length(P_px - A_px);\n" +
                "  } else if (t > 1.0) {\n" +
                "    dist = length(P_px - B_px);\n" +
                "  } else {\n" +
                "    float area = abs(AB.x * (P_px.y - A_px.y) - AB.y * (P_px.x - A_px.x));\n" +
                "    dist = area / sqrt(len2 + 1e-6);\n" +
                "  }\n" +
                "  if (dist > uHalfPx) discard;\n" +
                "  fragColor = uColor; }";

        sharedShader = new Builder().fromSource(vs, fs).build();
    }

    @Override
    protected void setupAttribLocations() {
        int p = getProgramHandle();
        uMVP = GLES20.glGetUniformLocation(p, "uMVPMatrix");
        uViewport = GLES20.glGetUniformLocation(p, "uViewport");
        uHalfPx = GLES20.glGetUniformLocation(p, "uHalfPx");
        uCapPx = GLES20.glGetUniformLocation(p, "uCapPx");
        uDepthBiasNDC = GLES20.glGetUniformLocation(p, "uDepthBiasNDC");
        uColor = GLES20.glGetUniformLocation(p, "uColor");

        uNL = GLES20.glGetUniformLocation(p, "uNL");
        uNR = GLES20.glGetUniformLocation(p, "uNR");
        uFR = GLES20.glGetUniformLocation(p, "uFR");
        uFL = GLES20.glGetUniformLocation(p, "uFL");
        uApex = GLES20.glGetUniformLocation(p, "uApex");
        uNormal = GLES20.glGetUniformLocation(p, "uNormal");
        uBaseOffset = GLES20.glGetUniformLocation(p, "uBaseOffset");

        aWeightsA = GLES20.glGetAttribLocation(p, "aWeightsA");
        aTA       = GLES20.glGetAttribLocation(p, "aTA");
        aWeightsB = GLES20.glGetAttribLocation(p, "aWeightsB");
        aTB       = GLES20.glGetAttribLocation(p, "aTB");
        aEnd      = GLES20.glGetAttribLocation(p, "aEnd");
        aSide     = GLES20.glGetAttribLocation(p, "aSide");
    }

    @Override
    protected void enableAndPointVertexAttribs(L layout) {
        final int stride = layout.strideBytes();
        layout.weightsA().enableAndPoint(aWeightsA, stride);
        layout.tA().enableAndPoint(aTA, stride);
        layout.weightsB().enableAndPoint(aWeightsB, stride);
        layout.tB().enableAndPoint(aTB, stride);
        layout.edgeEnd().enableAndPoint(aEnd, stride);
        layout.edgeSide().enableAndPoint(aSide, stride);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aWeightsA);
        GLES20.glDisableVertexAttribArray(aTA);
        GLES20.glDisableVertexAttribArray(aWeightsB);
        GLES20.glDisableVertexAttribArray(aTB);
        GLES20.glDisableVertexAttribArray(aEnd);
        GLES20.glDisableVertexAttribArray(aSide);
    }

    @Override
    protected void transferUniformArgsToGPU(SpikeWireframeShaderArgs.VS v, SpikeWireframeShaderArgs.FS f) {
        GLES20.glUniformMatrix4fv(uMVP, 1, false, v.mvp, 0);
        GLES20.glUniform2f(uViewport, v.viewportW, v.viewportH);
        GLES20.glUniform1f(uHalfPx, v.halfPx);
        GLES20.glUniform1f(uCapPx, v.capPx);
        GLES20.glUniform1f(uDepthBiasNDC, v.uDepthBiasNDC);
        GLES20.glUniform4f(uColor, f.color.r(), f.color.g(), f.color.b(), f.color.a());

        GLES20.glUniform3fv(uNL, 1, v.uNL, 0);
        GLES20.glUniform3fv(uNR, 1, v.uNR, 0);
        GLES20.glUniform3fv(uFR, 1, v.uFR, 0);
        GLES20.glUniform3fv(uFL, 1, v.uFL, 0);
        GLES20.glUniform3fv(uApex, 1, v.uApex, 0);
        GLES20.glUniform3fv(uNormal, 1, v.uNormal, 0);
        GLES20.glUniform1f(uBaseOffset, v.uBaseOffset);
    }

    public static final class Builder
            extends ShaderPair.BaseBuilder<
                    SpikeWireframeShaderPair<VertexLayout.SpikeCanonicalWireframeLayout>,
                    Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected SpikeWireframeShaderPair<VertexLayout.SpikeCanonicalWireframeLayout> create(
                int programHandle, String vs, String fs) {
            return new SpikeWireframeShaderPair<>(programHandle, vs, fs);
        }
    }
}


