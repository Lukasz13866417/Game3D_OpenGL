package com.example.game3d_opengl.game.terrain.track_elements.spike;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.shader.ShaderPair;

/**
 * Shader for spikes with non-affine bases. Vertex positions are computed
 * from per-vertex base weights and per-instance quad corners + apex.
 */
public final class SpikeInfillShaderPair
        extends ShaderPair<InfillShaderArgs.VS, InfillShaderArgs.FS> {


    private int uMVP, uColor;
    private int uNL, uNR, uFR, uFL, uApex, uNormal, uBaseOffset;
    private int aWeights, aT;

    private SpikeInfillShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static SpikeInfillShaderPair sharedShader = null;

    public static SpikeInfillShaderPair getSharedShader(){
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
                "uniform vec3 uNL, uNR, uFR, uFL;\n" +
                "uniform vec3 uApex;\n" +
                "uniform vec3 uNormal;\n" +
                "uniform float uBaseOffset;\n" +
                "attribute vec4 aWeights;\n" +
                "attribute float aT;\n" +
                "void main(){\n" +
                "  vec3 pBase = aWeights.x * uNL + aWeights.y * uNR + aWeights.z * uFR + aWeights.w * uFL;\n" +
                "  vec3 worldPos = mix(pBase + uNormal * uBaseOffset, uApex, aT);\n" +
                "  gl_Position = uMVPMatrix * vec4(worldPos, 1.0);\n" +
                "}";
        String fs =
                "precision mediump float;\n" +
                "uniform vec4 vColor;\n" +
                "void main(){\n" +
                "  gl_FragColor = vColor;\n" +
                "}";
        sharedShader = new Builder().fromSource(vs, fs).build();
    }

    @Override
    public void enableAndPointVertexAttribs() {
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
    protected void setupAttribLocations() {
        this.uMVP = GLES20.glGetUniformLocation(getProgramHandle(), "uMVPMatrix");
        this.uColor = GLES20.glGetUniformLocation(getProgramHandle(), "vColor");
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
    protected void transferArgsToGPU(InfillShaderArgs.VS vertexArgs, InfillShaderArgs.FS fragmentArgs) {
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
    }

    // setInstanceUniforms removed; instance data now flows via InfillShaderArgs.VS

    public static final class Builder extends ShaderPair.BaseBuilder<SpikeInfillShaderPair, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected SpikeInfillShaderPair create(int programHandle, String vs, String fs) {
            return new SpikeInfillShaderPair(programHandle, vs, fs);
        }
    }
}


