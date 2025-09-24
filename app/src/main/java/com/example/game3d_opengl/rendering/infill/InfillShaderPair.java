package com.example.game3d_opengl.rendering.infill;

import android.content.res.AssetManager;
import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.shader.ShaderPair;

public final class InfillShaderPair extends ShaderPair<InfillShaderArgs.VS, InfillShaderArgs.FS> {


    private static InfillShaderPair sharedShader = null;

    public static InfillShaderPair getSharedShader(){
        if (sharedShader == null){
            throw new IllegalStateException(
                    "Shader instance is null. Needs calling LOAD_SHADER_CODE first"
            );
        }
        return sharedShader;
    }


    private int uMVP, uColor, aPos;


    public InfillShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs,fs);
    }


    public static void LOAD_SHADER_CODE(AssetManager assetManager){
        String vs = "uniform mat4 uMVPMatrix; attribute vec4 vPosition; void main(){ gl_Position = uMVPMatrix * vPosition; }";
        String fs = "precision mediump float; uniform vec4 vColor; void main(){ gl_FragColor = vColor; }";
        sharedShader = new Builder().fromSource(vs,fs).build();
    }

    @Override
    public void enableAndPointVertexAttribs() {
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 3 * 4, 0);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aPos);
    }

    @Override
    protected void setupAttribLocations() {
        this.uMVP = GLES20.glGetUniformLocation(getProgramHandle(), "uMVPMatrix");
        this.uColor = GLES20.glGetUniformLocation(getProgramHandle(), "vColor");
        this.aPos = GLES20.glGetAttribLocation(getProgramHandle(), "vPosition");
    }

    @Override
    protected void transferArgsToGPU(InfillShaderArgs.VS vertexArgs, InfillShaderArgs.FS fragmentArgs) {
        GLES20.glUniformMatrix4fv(uMVP, 1, false, vertexArgs.mvp, 0);
        GLES20.glUniform4fv(uColor, 1, fragmentArgs.color.rgba, 0);
    }


    // Nested types
    public static final class Builder extends ShaderPair.BaseBuilder<InfillShaderPair, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected InfillShaderPair create(int programHandle, String vs, String fs) {
            return new InfillShaderPair(programHandle, vs, fs);
        }
    }

}


