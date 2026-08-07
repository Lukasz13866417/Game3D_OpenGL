package com.example.game3d_opengl.game.terrain.track_elements.portal.rendering;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.layout.VertexLayout;
import com.example.game3d_opengl.rendering.shader.MeshShaderPair;
import com.example.game3d_opengl.rendering.shader.ShaderPair;

public final class PortalShaderPair<
        L extends VertexLayout.HasPosition & VertexLayout.HasTexCoords>
        extends MeshShaderPair<PortalShaderArgs.VS, PortalShaderArgs.FS, L> {

    private static PortalShaderPair<VertexLayout.PositionUvLayout> sharedShader = null;

    public static void LOAD_SHADER_CODE() {
        if (sharedShader != null) return;
        String vs = ""
                + "#version 300 es\n"
                + "uniform mat4 uMVPMatrix;\n"
                + "in vec4 aPosition;\n"
                + "in vec2 aUV;\n"
                + "out vec2 vUV;\n"
                + "void main(){\n"
                + "  vUV = aUV;\n"
                + "  gl_Position = uMVPMatrix * aPosition;\n"
                + "}\n";
        String fs = ""
                + "#version 300 es\n"
                + "precision mediump float;\n"
                + "uniform sampler2D uTexture;\n"
                + "in vec2 vUV;\n"
                + "out vec4 fragColor;\n"
                + "void main(){\n"
                + "  fragColor = texture(uTexture, vUV);\n"
                + "}\n";
        sharedShader = new Builder().fromSource(vs, fs).build();
    }

    public static PortalShaderPair<VertexLayout.PositionUvLayout> getSharedShader() {
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
    protected void enableAndPointVertexAttribs(L layout) {
        int stride = layout.strideBytes();
        layout.position().enableAndPoint(aPos, stride);
        layout.texCoords().enableAndPoint(aUV, stride);
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

    public static final class Builder
            extends ShaderPair.BaseBuilder<PortalShaderPair<VertexLayout.PositionUvLayout>, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected PortalShaderPair<VertexLayout.PositionUvLayout> create(
                int programHandle, String vs, String fs) {
            return new PortalShaderPair<>(programHandle, vs, fs);
        }
    }
}
