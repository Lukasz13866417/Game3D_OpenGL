package com.example.game3d_opengl.game.terrain_api.terrainutil;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.shader.ShaderPair;

public final class TerrainRibbonShaderPair
        extends ShaderPair<InfillShaderArgs.VS, InfillShaderArgs.FS> {

    public static final TerrainRibbonShaderPair sharedShader
            = TerrainRibbonShaderPair.createDefault();

    private int uMVP, uColor, aPosMask, uLightPos, uLightColor, aNormal;

    private TerrainRibbonShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static TerrainRibbonShaderPair createDefault() {
        String vs =
                "uniform mat4 uMVPMatrix;\n" +
                        "attribute vec4 aPosMask;\n" +
                        "attribute vec3 aNormal;\n" +
                        "varying float vMask;\n" +
                        "varying vec3 vWorldPos;\n" +
                        "varying vec3 vNormal;\n" +
                        "void main(){\n" +
                        "  gl_Position = uMVPMatrix * vec4(aPosMask.xyz, 1.0);\n" +
                        "  vMask = aPosMask.w;\n" +
                        "  vWorldPos = aPosMask.xyz;\n" +
                        "  vNormal = aNormal;\n" +
                        "}";
        String fs =
                "precision mediump float;\n" +
                        "uniform vec4 vColor;\n" +
                        "varying float vMask;\n" +
                        "varying vec3 vWorldPos;\n" +
                        "varying vec3 vNormal;\n" +
                        "uniform vec3 uLightPos;\n" +
                        "uniform vec3 uLightColor;\n" +
                        "const float MASK_EPS = 1e-4;\n" +
                        "void main(){\n" +
                        "  if(vMask <= MASK_EPS) discard;\n" +
                        "  vec3 toLight = uLightPos - vWorldPos;\n" +
                        "  float distSq = dot(toLight, toLight);\n" +
                        "  float atten = 1.0 / (1.0 + 0.0000001 * distSq);\n" +
                        "  vec3 L = toLight * inversesqrt(distSq);\n" +
                        "  vec3 N = normalize(vNormal);\n" +
                        "  float diff = max(dot(L, N), 0.0);\n" +
                        "  vec3 lighting = uLightColor * diff * atten + vec3(0.3);\n" +
                        "  gl_FragColor = vec4(vColor.rgb * lighting, vColor.a);\n" +
                        "}";
        return new Builder().fromSource(vs, fs).build();
    }

    @Override
    public void enableAndPointVertexAttribs() {
        final int stride = 8 * 4; // 32 bytes
        GLES20.glEnableVertexAttribArray(aPosMask);
        GLES20.glVertexAttribPointer(aPosMask, 4, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(aNormal);
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, stride, 16);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aPosMask);
        GLES20.glDisableVertexAttribArray(aNormal);
    }

    @Override
    protected void setupAttribLocations() {
        this.uMVP = GLES20.glGetUniformLocation(getProgramHandle(), "uMVPMatrix");
        this.uColor = GLES20.glGetUniformLocation(getProgramHandle(), "vColor");
        this.aPosMask = GLES20.glGetAttribLocation(getProgramHandle(), "aPosMask");
        this.uLightPos = GLES20.glGetUniformLocation(getProgramHandle(), "uLightPos");
        this.uLightColor = GLES20.glGetUniformLocation(getProgramHandle(), "uLightColor");
        this.aNormal = GLES20.glGetAttribLocation(getProgramHandle(), "aNormal");
    }

    @Override
    protected void transferArgsToGPU(InfillShaderArgs.VS vertexArgs,
                                     InfillShaderArgs.FS fragmentArgs) {
        GLES20.glUniformMatrix4fv(uMVP, 1, false, vertexArgs.mvp, 0);
        GLES20.glUniform4fv(uColor, 1, fragmentArgs.color.rgba, 0);
        GLES20.glUniform3f(uLightPos, fragmentArgs.lightX, fragmentArgs.lightY, fragmentArgs.lightZ);
        GLES20.glUniform3f(uLightColor, fragmentArgs.lightColor.r(), fragmentArgs.lightColor.g(), fragmentArgs.lightColor.b());
    }

    public static final class Builder extends
            ShaderPair.BaseBuilder<TerrainRibbonShaderPair, Builder> {
        @Override
        protected Builder self() {
            return this;
        }

        @Override
        protected TerrainRibbonShaderPair create(int programHandle, String vs, String fs) {
            return new TerrainRibbonShaderPair(programHandle, vs, fs);
        }
    }
}


