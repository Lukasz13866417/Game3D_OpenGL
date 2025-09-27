package com.example.game3d_opengl.game.terrain.track_elements.spike;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.shader.ShaderPair;

/**
 * Wireframe shader for spikes that maps canonical spike endpoints (weights + t)
 * to world space using per-instance uniforms, then expands edges to a screen-space
 * quad to achieve constant pixel thickness.
 */
public final class SpikeWireframeShaderPair
        extends ShaderPair<SpikeWireframeShaderArgs.VS, SpikeWireframeShaderArgs.FS> {

    // Uniforms
    private int uMVP, uViewport, uHalfPx, uDepthBiasNDC, uColor;
    private int uNL, uNR, uFR, uFL, uApex, uNormal, uBaseOffset;

    // Attributes
    private int aWeightsA, aTA, aWeightsB, aTB, aEnd, aSide;

    public static SpikeWireframeShaderPair sharedShader = null;

    private SpikeWireframeShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static SpikeWireframeShaderPair getSharedShader(){
        if (sharedShader == null){
            throw new IllegalStateException(
                    "Shader instance is null. Needs calling LOAD_SHADER_CODE first"
            );
        }
        return sharedShader;
    }

    public static void LOAD_SHADER_CODE() {
        String vs =
                "uniform mat4 uMVPMatrix;\n" +
                "uniform vec2 uViewport;\n" +
                "uniform float uHalfPx;\n" +
                "uniform float uDepthBiasNDC;\n" +
                "uniform vec3 uNL, uNR, uFR, uFL;\n" +
                "uniform vec3 uApex;\n" +
                "uniform vec3 uNormal;\n" +
                "uniform float uBaseOffset;\n" +
                "attribute vec4 aWeightsA;\n" +
                "attribute float aTA;\n" +
                "attribute vec4 aWeightsB;\n" +
                "attribute float aTB;\n" +
                "attribute float aEnd;\n" +
                "attribute float aSide;\n" +
                "vec2 ndc(vec4 clip){ return clip.xy / clip.w; }\n" +
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
                "  vec2 delta_ndc = (uHalfPx * n_pix) / ndc2px;\n" +
                "  vec4 P_clip = mix(A_clip, B_clip, aEnd);\n" +
                "  vec2 P_ndc  = mix(A_ndc,  B_ndc,  aEnd);\n" +
                "  vec2 out_ndc = P_ndc + aSide * delta_ndc;\n" +
                "  gl_Position = vec4(out_ndc * P_clip.w, P_clip.z, P_clip.w);\n" +
                "  gl_Position.z += uDepthBiasNDC * gl_Position.w;\n" +
                "}";

        String fs =
                "precision mediump float;\n" +
                "uniform vec4 uColor;\n" +
                "void main(){ gl_FragColor = uColor; }";

        sharedShader = new Builder().fromSource(vs, fs).build();
    }

    @Override
    protected void setupAttribLocations() {
        int p = getProgramHandle();
        uMVP = GLES20.glGetUniformLocation(p, "uMVPMatrix");
        uViewport = GLES20.glGetUniformLocation(p, "uViewport");
        uHalfPx = GLES20.glGetUniformLocation(p, "uHalfPx");
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
    public void enableAndPointVertexAttribs() {
        final int stride = 12 * 4; // 12 floats per vertex
        GLES20.glEnableVertexAttribArray(aWeightsA);
        GLES20.glVertexAttribPointer(aWeightsA, 4, GLES20.GL_FLOAT, false, stride, 0);

        GLES20.glEnableVertexAttribArray(aTA);
        GLES20.glVertexAttribPointer(aTA, 1, GLES20.GL_FLOAT, false, stride, 4 * 4);

        GLES20.glEnableVertexAttribArray(aWeightsB);
        GLES20.glVertexAttribPointer(aWeightsB, 4, GLES20.GL_FLOAT, false, stride, 5 * 4);

        GLES20.glEnableVertexAttribArray(aTB);
        GLES20.glVertexAttribPointer(aTB, 1, GLES20.GL_FLOAT, false, stride, 9 * 4);

        GLES20.glEnableVertexAttribArray(aEnd);
        GLES20.glVertexAttribPointer(aEnd, 1, GLES20.GL_FLOAT, false, stride, 10 * 4);

        GLES20.glEnableVertexAttribArray(aSide);
        GLES20.glVertexAttribPointer(aSide, 1, GLES20.GL_FLOAT, false, stride, 11 * 4);
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
    protected void transferArgsToGPU(SpikeWireframeShaderArgs.VS v, SpikeWireframeShaderArgs.FS f) {
        GLES20.glUniformMatrix4fv(uMVP, 1, false, v.mvp, 0);
        GLES20.glUniform2f(uViewport, v.viewportW, v.viewportH);
        GLES20.glUniform1f(uHalfPx, v.halfPx);
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

    public static final class Builder extends ShaderPair.BaseBuilder<SpikeWireframeShaderPair, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected SpikeWireframeShaderPair create(int programHandle, String vs, String fs) {
            return new SpikeWireframeShaderPair(programHandle, vs, fs);
        }
    }
}


