package com.example.game3d_opengl.game.terrain_api.terrainutil;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.shader.ShaderPair;

/**
 * Simple shader for the terrain ribbon: position.xyz + mask in .w, solid color.
 * Discards fragments where interpolated mask is ~0.
 */
public final class TerrainRibbonShaderPair
        extends ShaderPair<InfillShaderArgs.VS, InfillShaderArgs.FS> {

    public static final TerrainRibbonShaderPair sharedShader
                                                          = TerrainRibbonShaderPair.createDefault();

    private int uMVP, uColor, aPosMask;

    private TerrainRibbonShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static TerrainRibbonShaderPair createDefault() {
        String vs =
                "uniform mat4 uMVPMatrix;\n" +
                "attribute vec4 aPosMask;\n" +
                "varying float vMask;\n" +
                "void main(){\n" +
                "  gl_Position = uMVPMatrix * vec4(aPosMask.xyz, 1.0);\n" +
                "  vMask = aPosMask.w;\n" +
                "}";
        String fs =
                "precision mediump float;\n" +
                "uniform vec4 vColor;\n" +
                "varying float vMask;\n" +
                "const float MASK_EPS = 1e-4;\n" +
                "void main(){\n" +
                "  if(vMask <= MASK_EPS) discard;\n" +
                "  gl_FragColor = vColor;\n" +
                "}";
        return new Builder().fromSource(vs, fs).build();
    }

    @Override
    public void enableAndPointVertexAttribs() {
        // Attribute layout: vec4 aPosMask packed as [x,y,z,mask], tightly packed (stride 16 bytes)
        GLES20.glEnableVertexAttribArray(aPosMask);
        GLES20.glVertexAttribPointer(aPosMask, 4, GLES20.GL_FLOAT, false, 4 * 4, 0);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aPosMask);
    }

    @Override
    protected void setupAttribLocations() {
        this.uMVP = GLES20.glGetUniformLocation(getProgramHandle(), "uMVPMatrix");
        this.uColor = GLES20.glGetUniformLocation(getProgramHandle(), "vColor");
        this.aPosMask = GLES20.glGetAttribLocation(getProgramHandle(), "aPosMask");
    }

    @Override
    protected void transferArgsToGPU(InfillShaderArgs.VS vertexArgs,
                                     InfillShaderArgs.FS fragmentArgs) {
        GLES20.glUniformMatrix4fv(uMVP, 1, false, vertexArgs.mvp, 0);
        GLES20.glUniform4fv(uColor, 1, fragmentArgs.color.rgba, 0);
    }

    public static final class Builder extends
            ShaderPair.BaseBuilder<TerrainRibbonShaderPair, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected TerrainRibbonShaderPair create(int programHandle, String vs, String fs) {
            return new TerrainRibbonShaderPair(programHandle, vs, fs);
        }
    }
}


