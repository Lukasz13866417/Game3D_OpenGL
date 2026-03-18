package com.example.game3d_opengl.game.terrain.terrain_api.terrainutil;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.shader.ShaderPair;

public final class TerrainRibbonShaderPair
        extends ShaderPair<InfillShaderArgs.VS, InfillShaderArgs.FS> {

    public static final TerrainRibbonShaderPair sharedShader
            = TerrainRibbonShaderPair.createDefault();

    private int uMVP, uColor, aPosition, uLightPos, uLightColor, aNormalAlpha;

    private TerrainRibbonShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static TerrainRibbonShaderPair createDefault() {
        String vs =
                "uniform mat4 uMVPMatrix;\n" +
                        "attribute vec3 aPosition;\n" +
                        "attribute vec4 aNormalAlpha;\n" +
                        "varying vec3 vWorldPos;\n" +
                        "varying vec3 vNormal;\n" +
                        "varying float vAlpha;\n" +
                        "void main(){\n" +
                        "  gl_Position = uMVPMatrix * vec4(aPosition, 1.0);\n" +
                        "  vWorldPos = aPosition;\n" +
                        "  vNormal = aNormalAlpha.xyz;\n" +
                        "  vAlpha = aNormalAlpha.w;\n" +
                        "}";
        String fs =
                "precision highp float;\n" +
                        "uniform vec4 vColor;\n" +
                        "varying vec3 vWorldPos;\n" +
                        "varying vec3 vNormal;\n" +
                        "varying float vAlpha;\n" +
                        "uniform vec3 uLightPos;\n" +
                        "uniform vec3 uLightColor;\n" +
                        "uniform int isDepthPass;\n" +
                        "void main(){\n" +
                        "  if (isDepthPass == 1) {\n" +
                        "    if (vAlpha < 1.0) discard;\n" +
                        "    return;\n" +
                        "  }\n" +
                        "  const float AMBIENT = 0.08;\n" +
                        "  const float CONTRAST = 1.35;\n" +
                        "  const float HIGHLIGHT_GAIN = 1.25;\n" +
                        "  vec3 toLight = uLightPos - vWorldPos;\n" +
                        "  float distSq = dot(toLight, toLight);\n" +
                        "  float atten = 1.0 / (1.0 + 0.0000001 * distSq);\n" +
                        "  vec3 L = toLight * inversesqrt(distSq);\n" +
                        "  vec3 N = normalize(vNormal);\n" +
                        "  float diff = max(dot(L, N), 0.0);\n" +
                        "  float lit = clamp((diff * atten - 0.5) * CONTRAST + 0.5, 0.0, 1.0);\n" +
                        "  vec3 lighting = uLightColor * (lit * HIGHLIGHT_GAIN) + vec3(AMBIENT);\n" +
                        "  gl_FragColor = vec4(vColor.rgb * lighting, vColor.a * vAlpha);\n" +
                        "}";
        return new Builder().fromSource(vs, fs).build();
    }

    @Override
    public void enableAndPointVertexAttribs() {
        // Attribute layout: vec3 aPosition, vec4 aNormalAlpha
        final int stride = (3 + 4) * 4; // 28 bytes
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(aNormalAlpha);
        GLES20.glVertexAttribPointer(aNormalAlpha, 4, GLES20.GL_FLOAT, false, stride, 12);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glDisableVertexAttribArray(aNormalAlpha);
    }

    @Override
    protected void setupAttribLocations() {
        this.uMVP = GLES20.glGetUniformLocation(getProgramHandle(), "uMVPMatrix");
        this.uColor = GLES20.glGetUniformLocation(getProgramHandle(), "vColor");
        this.aPosition = GLES20.glGetAttribLocation(getProgramHandle(), "aPosition");
        this.uLightPos = GLES20.glGetUniformLocation(getProgramHandle(), "uLightPos");
        this.uLightColor = GLES20.glGetUniformLocation(getProgramHandle(), "uLightColor");
        this.aNormalAlpha = GLES20.glGetAttribLocation(getProgramHandle(), "aNormalAlpha");
        this.uIsDepthPass = GLES20.glGetUniformLocation(getProgramHandle(), "isDepthPass");
    }

    @Override
    protected void transferUniformArgsToGPU(InfillShaderArgs.VS vertexArgs,
                                            InfillShaderArgs.FS fragmentArgs) {
        GLES20.glUniformMatrix4fv(uMVP, 1, false, vertexArgs.mvp, 0);
        GLES20.glUniform4fv(uColor, 1, fragmentArgs.color.rgba, 0);
        GLES20.glUniform3f(uLightPos, fragmentArgs.lightX, fragmentArgs.lightY, fragmentArgs.lightZ);
        GLES20.glUniform3f(uLightColor, fragmentArgs.lightColor.r(), fragmentArgs.lightColor.g(), fragmentArgs.lightColor.b());
        GLES20.glUniform1i(uIsDepthPass, fragmentArgs.isDepthPass);
    }

    private int uIsDepthPass;

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


