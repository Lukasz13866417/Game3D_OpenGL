package com.example.game3d_opengl.game.progress_bar;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.shader.ShaderPair;

public final class ProgressBarFillShaderPair
        extends ShaderPair<ProgressBarFillShaderArgs.VS, ProgressBarFillShaderArgs.FS> {

    private static ProgressBarFillShaderPair sharedShader;

    private int aPosition;
    private int uMVP;
    private int uColor;
    private int uProgress;

    private ProgressBarFillShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static ProgressBarFillShaderPair getSharedShader() {
        if (sharedShader == null) {
            sharedShader = new Builder().fromSource(buildVS(), buildFS()).build();
        }
        return sharedShader;
    }

    private static String buildVS() {
        return "uniform mat4 uMVPMatrix;\n" +
               "attribute vec3 aPosition;\n" +
               "varying float vU;\n" +
               "void main(){\n" +
               "  gl_Position = uMVPMatrix * vec4(aPosition, 1.0);\n" +
               "  vU = aPosition.x;\n" +
               "}";
    }

    private static String buildFS() {
        return "precision mediump float;\n" +
               "uniform vec4 uColor;\n" +
               "uniform float uProgress;\n" +
               "varying float vU;\n" +
               "void main(){\n" +
               "  if (vU > uProgress) discard;\n" +
               "  gl_FragColor = uColor;\n" +
               "}";
    }

    @Override
    protected void setupAttribLocations() {
        int p = getProgramHandle();
        uMVP = GLES20.glGetUniformLocation(p, "uMVPMatrix");
        uColor = GLES20.glGetUniformLocation(p, "uColor");
        uProgress = GLES20.glGetUniformLocation(p, "uProgress");
        aPosition = GLES20.glGetAttribLocation(p, "aPosition");
    }

    @Override
    public void enableAndPointVertexAttribs() {
        final int stride = 3 * 4;
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, stride, 0);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aPosition);
    }

    @Override
    protected void transferUniformArgsToGPU(ProgressBarFillShaderArgs.VS v, ProgressBarFillShaderArgs.FS f) {
        GLES20.glUniformMatrix4fv(uMVP, 1, false, v.mvp, 0);
        GLES20.glUniform4fv(uColor, 1, f.color.rgba, 0);
        GLES20.glUniform1f(uProgress, f.progress);
    }

    public static final class Builder extends ShaderPair.BaseBuilder<ProgressBarFillShaderPair, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected ProgressBarFillShaderPair create(int programHandle, String vs, String fs) {
            return new ProgressBarFillShaderPair(programHandle, vs, fs);
        }
    }
}
