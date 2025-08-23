package com.example.game3d_opengl.rendering.object3d;

import android.opengl.GLES20;

public final class BasicShaderPair extends ShaderPair<BasicShaderArgs.VS, BasicShaderArgs.FS> {
    // 1) Constants and static fields
    public static final BasicShaderPair sharedShader = BasicShaderPair.createDefault();

    // 2) Instance fields
    private int uMVP, uColor, aPos;

    // 3) Constructors
    public BasicShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static BasicShaderPair createDefault() {
        String vs = "uniform mat4 uMVPMatrix; attribute vec4 vPosition; void main(){ gl_Position = uMVPMatrix * vPosition; }";
        String fs = "precision mediump float; uniform vec4 vColor; void main(){ gl_FragColor = vColor; }";
        return new Builder().fromSource(vs, fs).build();
    }

    // 4) Public methods (API)
    @Override
    public void enableAndPointVertexAttribs() {
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 3 * 4, 0);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aPos);
    }

    // 5) Protected methods
    @Override
    protected void setupAttribLocations() {
        this.uMVP = GLES20.glGetUniformLocation(getProgramHandle(), "uMVPMatrix");
        this.uColor = GLES20.glGetUniformLocation(getProgramHandle(), "vColor");
        this.aPos = GLES20.glGetAttribLocation(getProgramHandle(), "vPosition");
    }

    @Override
    protected void transferArgsToGPU(BasicShaderArgs.VS vertexArgs, BasicShaderArgs.FS fragmentArgs) {
        GLES20.glUniformMatrix4fv(uMVP, 1, false, vertexArgs.mvp, 0);
        GLES20.glUniform4fv(uColor, 1, fragmentArgs.color.rgba, 0);
    }

    // 6) Private methods

    // Nested types
    public static final class Builder extends ShaderPair.BaseBuilder<BasicShaderPair, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected BasicShaderPair create(int programHandle, String vs, String fs) {
            return new BasicShaderPair(programHandle, vs, fs);
        }
    }

}


