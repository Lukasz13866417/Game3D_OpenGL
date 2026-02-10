package com.example.game3d_opengl.game.terrain.track_elements.portal.rendering;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.shader.ShaderPair;

public final class PortalShaderPair extends ShaderPair<PortalShaderArgs.VS, PortalShaderArgs.FS> {

    private static PortalShaderPair sharedShader = null;

    public static void LOAD_SHADER_CODE() {
        if (sharedShader != null) return;
        String vs = ""
                + "uniform mat4 uMVPMatrix;\n"
                + "attribute vec4 aPosition;\n"
                + "attribute vec2 aUV;\n"
                + "varying vec2 vUV;\n"
                + "void main(){\n"
                + "  vUV = aUV;\n"
                + "  gl_Position = uMVPMatrix * aPosition;\n"
                + "}\n";
        String fs = ""
                + "precision mediump float;\n"
                + "uniform sampler2D uTexture;\n"
                + "varying vec2 vUV;\n"
                + "void main(){\n"
                + "  gl_FragColor = texture2D(uTexture, vUV);\n"
                + "}\n";
        sharedShader = new Builder().fromSource(vs, fs).build();
    }

    public static PortalShaderPair getSharedShader() {
        if (sharedShader == null) {
            throw new IllegalStateException("PortalShaderPair not loaded. Call LOAD_SHADER_CODE first.");
        }
        return sharedShader;
    }

    private int uMvp;
    private int uTexture;
    private int aPos;
    private int aUV;

    private PortalShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    @Override
    protected void setupAttribLocations() {
        int p = getProgramHandle();
        uMvp = GLES20.glGetUniformLocation(p, "uMVPMatrix");
        uTexture = GLES20.glGetUniformLocation(p, "uTexture");
        aPos = GLES20.glGetAttribLocation(p, "aPosition");
        aUV = GLES20.glGetAttribLocation(p, "aUV");
    }

    @Override
    public void enableAndPointVertexAttribs() {
        int stride = 5 * 4;
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(aUV);
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, stride, 3 * 4);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aUV);
    }

    @Override
    protected void transferUniformArgsToGPU(PortalShaderArgs.VS vs, PortalShaderArgs.FS fs) {
        if (vs != null && vs.mvp != null) {
            GLES20.glUniformMatrix4fv(uMvp, 1, false, vs.mvp, 0);
        }
        int unit = fs != null ? fs.textureUnit : 0;
        GLES20.glUniform1i(uTexture, unit);
    }

    public static final class Builder extends ShaderPair.BaseBuilder<PortalShaderPair, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected PortalShaderPair create(int programHandle, String vs, String fs) {
            return new PortalShaderPair(programHandle, vs, fs);
        }
    }
}
