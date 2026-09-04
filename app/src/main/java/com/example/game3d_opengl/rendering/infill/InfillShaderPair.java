package com.example.game3d_opengl.rendering.infill;

import android.content.res.AssetManager;
import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.layout.VertexLayout;
import com.example.game3d_opengl.rendering.shader.MeshShaderPair;
import com.example.game3d_opengl.rendering.shader.ShaderPair;

public final class InfillShaderPair<
        L extends VertexLayout.HasPosition & VertexLayout.HasNormals>
        extends MeshShaderPair<InfillShaderArgs.VS, InfillShaderArgs.FS, L> {


    private static InfillShaderPair<VertexLayout.PositionNormalLayout> sharedShader = null;

    public static InfillShaderPair<VertexLayout.PositionNormalLayout> getSharedShader(){
        if (sharedShader == null){
            throw new IllegalStateException(
                    "Shader instance is null. Needs calling LOAD_SHADER_CODE first"
            );
        }
        return sharedShader;
    }


    private int uMVP, uModel, uColor, aPos, aNormal;
    private int uLightPos, uLightColor, uCameraPos, uAmbient, uDiffuse, uSpecular, uShininess;


    public InfillShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs,fs);
    }


    public static void LOAD_SHADER_CODE(AssetManager assetManager){
        if (sharedShader != null) {
            return;
        }
        String vs =
                "#version 300 es\n" +
                "uniform mat4 uMVPMatrix;\n" +
                "uniform mat4 uModelMatrix;\n" +
                "in vec3 vPosition;\n" +
                "in vec3 aNormal;\n" +
                "out vec3 vWorldPos;\n" +
                "out vec3 vWorldNormal;\n" +
                "void main(){\n" +
                "  vec4 wp = uModelMatrix * vec4(vPosition, 1.0);\n" +
                "  vWorldPos = wp.xyz;\n" +
                "  vWorldNormal = mat3(uModelMatrix) * aNormal;\n" +
                "  gl_Position = uMVPMatrix * vec4(vPosition, 1.0);\n" +
                "}";
        String fs =
                "#version 300 es\n" +
                "precision mediump float;\n" +
                "uniform vec4 vColor;\n" +
                "uniform vec3 uLightPos;\n" +
                "uniform vec3 uLightColor;\n" +
                "uniform vec3 uCameraPos;\n" +
                "uniform float uAmbient;\n" +
                "uniform float uDiffuse;\n" +
                "uniform float uSpecular;\n" +
                "uniform float uShininess;\n" +
                "in vec3 vWorldPos;\n" +
                "in vec3 vWorldNormal;\n" +
                "out vec4 fragColor;\n" +
                "void main(){\n" +
                "  vec3 N = normalize(vWorldNormal);\n" +
                "  vec3 L = normalize(uLightPos - vWorldPos);\n" +
                "  vec3 V = normalize(uCameraPos - vWorldPos);\n" +
                "  vec3 H = normalize(L + V);\n" +
                "  float NdotL = max(dot(N, L), 0.0);\n" +
                "  float NdotH = max(dot(N, H), 0.0);\n" +
                "  float spec = pow(NdotH, uShininess);\n" +
                "  vec3 color = vColor.rgb * (uAmbient + uDiffuse * NdotL) + uLightColor * uSpecular * spec;\n" +
                "  fragColor = vec4(color, vColor.a);\n" +
                "}";
        sharedShader = new Builder().fromSource(vs,fs).build();
    }

    @Override
    protected void enableAndPointVertexAttribs(L layout) {
        int stride = layout.strideBytes();
        layout.position().enableAndPoint(aPos, stride);
        layout.normals().enableAndPoint(aNormal, stride);
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


    // Nested types
    public static final class Builder
            extends ShaderPair.BaseBuilder<InfillShaderPair<VertexLayout.PositionNormalLayout>, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected InfillShaderPair<VertexLayout.PositionNormalLayout> create(
                int programHandle, String vs, String fs) {
            return new InfillShaderPair<>(programHandle, vs, fs);
        }
    }

}
