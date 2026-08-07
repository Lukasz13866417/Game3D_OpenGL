package com.example.game3d_opengl.game.progress_bar;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.layout.VertexLayout;
import com.example.game3d_opengl.rendering.shader.MeshShaderPair;
import com.example.game3d_opengl.rendering.shader.ShaderPair;

public final class ProgressBarFillShaderPair
        <L extends VertexLayout.HasPosition>
        extends MeshShaderPair<ProgressBarFillShaderArgs.VS, ProgressBarFillShaderArgs.FS, L> {

    private static ProgressBarFillShaderPair<VertexLayout.PositionLayout> sharedShader;

    private int aPosition;
    private int uMVP;
    private int uColor;
    private int uProgress;

    private ProgressBarFillShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static ProgressBarFillShaderPair<VertexLayout.PositionLayout> getSharedShader() {
        if (sharedShader == null) {
            sharedShader = new Builder().fromSource(buildVS(), buildFS()).build();
        }
        return sharedShader;
    }

    private static String buildVS() {
        return "#version 300 es\n" +
               "uniform mat4 uMVPMatrix;\n" +
               "in vec3 aPosition;\n" +
               "out float vU;\n" +
               "void main(){\n" +
               "  gl_Position = uMVPMatrix * vec4(aPosition, 1.0);\n" +
               "  vU = aPosition.x;\n" +
               "}";
    }

    private static String buildFS() {
        return "#version 300 es\n" +
               "precision mediump float;\n" +
               "uniform vec4 uColor;\n" +
               "uniform float uProgress;\n" +
               "in float vU;\n" +
               "out vec4 fragColor;\n" +
               "void main(){\n" +
               "  if (vU > uProgress) discard;\n" +
               "  fragColor = uColor;\n" +
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
    protected void enableAndPointVertexAttribs(L layout) {
        layout.position().enableAndPoint(aPosition, layout.strideBytes());
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

    public static final class Builder
            extends ShaderPair.BaseBuilder<ProgressBarFillShaderPair<VertexLayout.PositionLayout>, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected ProgressBarFillShaderPair<VertexLayout.PositionLayout> create(
                int programHandle, String vs, String fs) {
            return new ProgressBarFillShaderPair<>(programHandle, vs, fs);
        }
    }
}
