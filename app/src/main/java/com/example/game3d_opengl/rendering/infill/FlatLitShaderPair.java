package com.example.game3d_opengl.rendering.infill;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.shader.ShaderPair;

/**
 * Blinn-Phong lighting computed per-vertex (in the vertex shader).
 * Intended for flat shading where all vertices of a face share the same normal,
 * so the lighting result is uniform across each face.
 *
 * Material parameters (ambient, diffuse, specular, shininess) are passed as uniforms.
 */
public final class FlatLitShaderPair extends ShaderPair<InfillShaderArgs.VS, InfillShaderArgs.FS> {

    private static FlatLitShaderPair sharedShader = null;

    public static FlatLitShaderPair getSharedShader() {
        if (sharedShader == null) {
            throw new IllegalStateException(
                    "Shader instance is null. Call LOAD_SHADER_CODE first");
        }
        return sharedShader;
    }

    private int uMVP, uModel, uColor, aPos, aNormal;
    private int uLightPos, uLightColor, uCameraPos, uAmbient, uDiffuse, uSpecular, uShininess;

    public FlatLitShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static void LOAD_SHADER_CODE() {
        String vs =
                "uniform mat4 uMVPMatrix;\n" +
                "uniform mat4 uModelMatrix;\n" +
                "uniform vec4 vColor;\n" +
                "uniform vec3 uLightPos;\n" +
                "uniform vec3 uLightColor;\n" +
                "uniform vec3 uCameraPos;\n" +
                "uniform float uAmbient;\n" +
                "uniform float uDiffuse;\n" +
                "uniform float uSpecular;\n" +
                "uniform float uShininess;\n" +
                "attribute vec3 vPosition;\n" +
                "attribute vec3 aNormal;\n" +
                "varying vec4 vLitColor;\n" +
                "void main(){\n" +
                "  vec4 wp = uModelMatrix * vec4(vPosition, 1.0);\n" +
                "  vec3 worldPos = wp.xyz;\n" +
                "  vec3 worldNormal = normalize(mat3(uModelMatrix) * aNormal);\n" +
                "  vec3 L = normalize(uLightPos - worldPos);\n" +
                "  vec3 V = normalize(uCameraPos - worldPos);\n" +
                "  vec3 H = normalize(L + V);\n" +
                "  float NdotL = max(dot(worldNormal, L), 0.0);\n" +
                "  float NdotH = max(dot(worldNormal, H), 0.0);\n" +
                "  float spec = pow(NdotH, uShininess);\n" +
                "  vec3 color = vColor.rgb * (uAmbient + uDiffuse * NdotL) + uLightColor * uSpecular * spec;\n" +
                "  vLitColor = vec4(color, vColor.a);\n" +
                "  gl_Position = uMVPMatrix * vec4(vPosition, 1.0);\n" +
                "}";
        String fs =
                "precision mediump float;\n" +
                "varying vec4 vLitColor;\n" +
                "void main(){\n" +
                "  gl_FragColor = vLitColor;\n" +
                "}";
        sharedShader = new Builder().fromSource(vs, fs).build();
    }

    private static final int STRIDE = 6 * 4;

    @Override
    public void enableAndPointVertexAttribs() {
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, STRIDE, 0);
        GLES20.glEnableVertexAttribArray(aNormal);
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, STRIDE, 3 * 4);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aNormal);
    }

    @Override
    protected void setupAttribLocations() {
        int p = getProgramHandle();
        this.uMVP = GLES20.glGetUniformLocation(p, "uMVPMatrix");
        this.uModel = GLES20.glGetUniformLocation(p, "uModelMatrix");
        this.uColor = GLES20.glGetUniformLocation(p, "vColor");
        this.uLightPos = GLES20.glGetUniformLocation(p, "uLightPos");
        this.uLightColor = GLES20.glGetUniformLocation(p, "uLightColor");
        this.uCameraPos = GLES20.glGetUniformLocation(p, "uCameraPos");
        this.uAmbient = GLES20.glGetUniformLocation(p, "uAmbient");
        this.uDiffuse = GLES20.glGetUniformLocation(p, "uDiffuse");
        this.uSpecular = GLES20.glGetUniformLocation(p, "uSpecular");
        this.uShininess = GLES20.glGetUniformLocation(p, "uShininess");
        this.aPos = GLES20.glGetAttribLocation(p, "vPosition");
        this.aNormal = GLES20.glGetAttribLocation(p, "aNormal");
    }

    @Override
    protected void transferUniformArgsToGPU(InfillShaderArgs.VS vertexArgs, InfillShaderArgs.FS fragmentArgs) {
        GLES20.glUniformMatrix4fv(uMVP, 1, false, vertexArgs.mvp, 0);
        if (vertexArgs.modelMatrix != null) {
            GLES20.glUniformMatrix4fv(uModel, 1, false, vertexArgs.modelMatrix, 0);
        }
        GLES20.glUniform4fv(uColor, 1, fragmentArgs.color.rgba, 0);

        GLES20.glUniform3f(uLightPos, fragmentArgs.lightX, fragmentArgs.lightY, fragmentArgs.lightZ);
        if (fragmentArgs.lightColor != null) {
            GLES20.glUniform3f(uLightColor,
                    fragmentArgs.lightColor.r(), fragmentArgs.lightColor.g(), fragmentArgs.lightColor.b());
        } else {
            GLES20.glUniform3f(uLightColor, 1f, 1f, 1f);
        }
        GLES20.glUniform3f(uCameraPos, fragmentArgs.cameraX, fragmentArgs.cameraY, fragmentArgs.cameraZ);
        GLES20.glUniform1f(uAmbient, fragmentArgs.ambient);
        GLES20.glUniform1f(uDiffuse, fragmentArgs.diffuse);
        GLES20.glUniform1f(uSpecular, fragmentArgs.specular);
        GLES20.glUniform1f(uShininess, Math.max(1f, fragmentArgs.shininess));
    }

    public static final class Builder extends ShaderPair.BaseBuilder<FlatLitShaderPair, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected FlatLitShaderPair create(int programHandle, String vs, String fs) {
            return new FlatLitShaderPair(programHandle, vs, fs);
        }
    }
}
