package com.example.game3d_opengl.rendering.text;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.shader.ShaderPair;

public final class TextShaderPair extends ShaderPair<TextShaderArgs.VS, TextShaderArgs.FS> {

    private static TextShaderPair sharedShader = null;

    private int aPosition;
    private int aUV;
    private int aColor;
    private int uTexture;

    private TextShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static TextShaderPair getSharedShader() {
        if (sharedShader == null) {
            sharedShader = new Builder().fromSource(buildVS(), buildFS()).build();
        }
        return sharedShader;
    }

    private static String buildVS() {
        return "#version 300 es\n" +
               "in vec2 aPosition;\n" +
               "in vec2 aUV;\n" +
               "in vec4 aColor;\n" +
               "out vec2 vUV;\n" +
               "out vec4 vColor;\n" +
               "void main(){\n" +
               "  gl_Position = vec4(aPosition.xy, 0.0, 1.0);\n" +
               "  vUV = aUV;\n" +
               "  vColor = aColor;\n" +
               "}";
    }

    private static String buildFS() {
        return "#version 300 es\n" +
               "precision mediump float;\n" +
               "uniform sampler2D uTexture;\n" +
               "in vec2 vUV;\n" +
               "in vec4 vColor;\n" +
               "out vec4 fragColor;\n" +
               "void main(){\n" +
               "  float alpha = texture(uTexture, vUV).a;\n" +
               "  fragColor = vec4(vColor.rgb, vColor.a * alpha);\n" +
               "}";
    }

    @Override
    protected void setupAttribLocations() {
        int p = getProgramHandle();
        aPosition = GLES20.glGetAttribLocation(p, "aPosition");
        aUV = GLES20.glGetAttribLocation(p, "aUV");
        aColor = GLES20.glGetAttribLocation(p, "aColor");
        uTexture = GLES20.glGetUniformLocation(p, "uTexture");
    }

    @Override
    public void enableAndPointVertexAttribs() {
        final int stride = 8 * 4; // pos(2) + uv(2) + color(4)
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, stride, 0);

        GLES20.glEnableVertexAttribArray(aUV);
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, stride, 2 * 4);

        GLES20.glEnableVertexAttribArray(aColor);
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, stride, 4 * 4);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glDisableVertexAttribArray(aUV);
        GLES20.glDisableVertexAttribArray(aColor);
    }

    @Override
    protected void transferUniformArgsToGPU(TextShaderArgs.VS vertexArgs, TextShaderArgs.FS fragmentArgs) {
        int unit = fragmentArgs != null ? fragmentArgs.textureUnit : 0;
        GLES20.glUniform1i(uTexture, unit);
    }

    public static final class Builder extends ShaderPair.BaseBuilder<TextShaderPair, Builder> {
        @Override
        protected Builder self() {
            return this;
        }

        @Override
        protected TextShaderPair create(int programHandle, String vs, String fs) {
            return new TextShaderPair(programHandle, vs, fs);
        }
    }
}
