package com.example.game3d_opengl.game.track_elements.spike;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.object3d.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.object3d.shader.ShaderPair;
import com.example.game3d_opengl.rendering.object3d.wireframe.WireframeShaderPair;

/**
 * Shader for spikes with non-affine bases. Vertex positions are computed
 * from per-vertex base weights and per-instance quad corners + apex.
 */
public final class SpikeWireframeShaderPair
        extends ShaderPair<InfillShaderArgs.VS, InfillShaderArgs.FS> {


    // uniforms
    private int uMVP, uColor, uHalfPx, uDepthBiasNDC;
    private int uNL, uNR, uFR, uFL, uApex, uNormal, uBaseOffset;

    // attributes
    private int aWeights, aT;

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
                "attribute vec4 aWeights;\n" +
                "attribute float aT;\n" +
                "attribute float aSide;\n" +
                "void main(){\n" +
                "  vec3 pBase = aWeights.x * uNL + aWeights.y * uNR + aWeights.z * uFR + aWeights.w * uFL;\n" +
                "  vec3 worldPos = mix(pBase + uNormal * uBaseOffset, uApex, aT);\n" +
                "  vec4 P_clip = uMVPMatrix * vec4(worldPos, 1.0);\n" +
                "  vec2 P_ndc = P_clip.xy / P_clip.w;\n" +
                "  // Fake a small perpendicular (constant thickness fallback)\n" +
                "  vec2 ndc2px = 0.5 * uViewport;\n" +
                "  vec2 delta_ndc = (uHalfPx * normalize(vec2(1.0,0.0))) / ndc2px;\n" +
                "  vec2 out_ndc = P_ndc + aSide * delta_ndc;\n" +
                "  gl_Position = vec4(out_ndc * P_clip.w, P_clip.z, P_clip.w);\n" +
                "  gl_Position.z += uDepthBiasNDC * gl_Position.w;\n" +
                "}";
        String fs =
                "precision mediump float;\n" +
                "uniform vec4 vColor;\n" +
                "void main(){ gl_FragColor = vColor; }";
        sharedShader = new Builder().fromSource(vs, fs).build();
    }

    @Override
    protected void setupAttribLocations() {
        this.uMVP = GLES20.glGetUniformLocation(getProgramHandle(), "uMVPMatrix");
        this.uColor = GLES20.glGetUniformLocation(getProgramHandle(), "vColor");
        this.uHalfPx = GLES20.glGetUniformLocation(getProgramHandle(), "uHalfPx");
        this.uDepthBiasNDC = GLES20.glGetUniformLocation(getProgramHandle(), "uDepthBiasNDC");
        // viewport is needed for pixel-normal conversion
        int uViewport = GLES20.glGetUniformLocation(getProgramHandle(), "uViewport");
        this.uNL = GLES20.glGetUniformLocation(getProgramHandle(), "uNL");
        this.uNR = GLES20.glGetUniformLocation(getProgramHandle(), "uNR");
        this.uFR = GLES20.glGetUniformLocation(getProgramHandle(), "uFR");
        this.uFL = GLES20.glGetUniformLocation(getProgramHandle(), "uFL");
        this.uApex = GLES20.glGetUniformLocation(getProgramHandle(), "uApex");
        this.uNormal = GLES20.glGetUniformLocation(getProgramHandle(), "uNormal");
        this.uBaseOffset = GLES20.glGetUniformLocation(getProgramHandle(), "uBaseOffset");

        this.aWeights = GLES20.glGetAttribLocation(getProgramHandle(), "aWeights");
        this.aT = GLES20.glGetAttribLocation(getProgramHandle(), "aT");
    }


    @Override
    public void enableAndPointVertexAttribs() {

        // weights + t layout from shared spike VBO; we add aSide as a constant attribute per draw if needed
        GLES20.glEnableVertexAttribArray(aWeights);
        GLES20.glVertexAttribPointer(aWeights, 4, GLES20.GL_FLOAT, false, 5 * 4, 0);
        GLES20.glEnableVertexAttribArray(aT);
        GLES20.glVertexAttribPointer(aT, 1, GLES20.GL_FLOAT, false, 5 * 4, 4 * 4);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aWeights);
        GLES20.glDisableVertexAttribArray(aT);
    }



    @Override
    protected void transferArgsToGPU(InfillShaderArgs.VS vertexArgs, InfillShaderArgs.FS fragmentArgs) {
        GLES20.glUniformMatrix4fv(uMVP, 1, false, vertexArgs.mvp, 0);
        GLES20.glUniform4fv(uColor, 1, fragmentArgs.color.rgba, 0);
    }

    public void setInstanceUniforms(float[] nl, float[] nr, float[] fr, float[] fl, float[] apex, float[] normal, float baseOffset) {
        GLES20.glUniform3fv(uNL, 1, nl, 0);
        GLES20.glUniform3fv(uNR, 1, nr, 0);
        GLES20.glUniform3fv(uFR, 1, fr, 0);
        GLES20.glUniform3fv(uFL, 1, fl, 0);
        GLES20.glUniform3fv(uApex, 1, apex, 0);
        GLES20.glUniform3fv(uNormal, 1, normal, 0);
        GLES20.glUniform1f(uBaseOffset, baseOffset);
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


