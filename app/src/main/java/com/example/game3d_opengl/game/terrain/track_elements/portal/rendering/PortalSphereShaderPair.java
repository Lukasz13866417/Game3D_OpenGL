package com.example.game3d_opengl.game.terrain.track_elements.portal.rendering;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.shader.ShaderPair;

public final class PortalSphereShaderPair
        extends ShaderPair<PortalSphereShaderArgs.VS, PortalSphereShaderArgs.FS> {

    private static PortalSphereShaderPair sharedShader = null;

    private int uVP, uCenter, uRadius, uRotation;
    private int uColorA, uColorB;
    private int uLightPos, uLightColor, uCameraPos;
    private int uAmbient, uDiffuse, uSpecular, uShininess;
    private int aPosition, aNormal, aFaceGroup;

    private PortalSphereShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static PortalSphereShaderPair getSharedShader() {
        if (sharedShader == null) {
            sharedShader = createDefault();
        }
        return sharedShader;
    }

    private static PortalSphereShaderPair createDefault() {
        String vs =
                "uniform mat4 uVPMatrix;\n" +
                "uniform vec3 uCenter;\n" +
                "uniform float uRadius;\n" +
                "uniform mat3 uRotation;\n" +
                "attribute vec3 aPosition;\n" +
                "attribute vec3 aNormal;\n" +
                "attribute float aFaceGroup;\n" +
                "varying vec3 vWorldPos;\n" +
                "varying vec3 vWorldNormal;\n" +
                "varying float vFaceGroup;\n" +
                "void main(){\n" +
                "  vec3 rotated = uRotation * aPosition;\n" +
                "  vWorldPos = uCenter + rotated * uRadius;\n" +
                "  vWorldNormal = normalize(uRotation * aNormal);\n" +
                "  vFaceGroup = aFaceGroup;\n" +
                "  gl_Position = uVPMatrix * vec4(vWorldPos, 1.0);\n" +
                "}";
        String fs =
                "precision highp float;\n" +
                "uniform vec4 uColorA;\n" +
                "uniform vec4 uColorB;\n" +
                "uniform vec3 uLightPos;\n" +
                "uniform vec3 uLightColor;\n" +
                "uniform vec3 uCameraPos;\n" +
                "uniform float uAmbient;\n" +
                "uniform float uDiffuse;\n" +
                "uniform float uSpecular;\n" +
                "uniform float uShininess;\n" +
                "varying vec3 vWorldPos;\n" +
                "varying vec3 vWorldNormal;\n" +
                "varying float vFaceGroup;\n" +
                "void main(){\n" +
                "  vec3 base = mix(uColorA.rgb, uColorB.rgb, step(0.5, vFaceGroup));\n" +
                "  vec3 N = normalize(vWorldNormal);\n" +
                "  vec3 L = normalize(uLightPos - vWorldPos);\n" +
                "  vec3 V = normalize(uCameraPos - vWorldPos);\n" +
                "  vec3 H = normalize(L + V);\n" +
                "  float NdotL = max(dot(N, L), 0.0);\n" +
                "  float NdotH = max(dot(N, H), 0.0);\n" +
                "  float spec = pow(NdotH, uShininess);\n" +
                "  vec3 color = base * (uAmbient + uDiffuse * NdotL) + uLightColor * uSpecular * spec;\n" +
                "  gl_FragColor = vec4(color, 1.0);\n" +
                "}";
        return new Builder().fromSource(vs, fs).build();
    }

    @Override
    public void enableAndPointVertexAttribs() {
        final int stride = 7 * 4; // 7 floats: pos(3) + normal(3) + faceGroup(1)
        GLES20.glEnableVertexAttribArray(aPosition);
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(aNormal);
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, stride, 3 * 4);
        GLES20.glEnableVertexAttribArray(aFaceGroup);
        GLES20.glVertexAttribPointer(aFaceGroup, 1, GLES20.GL_FLOAT, false, stride, 6 * 4);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aPosition);
        GLES20.glDisableVertexAttribArray(aNormal);
        GLES20.glDisableVertexAttribArray(aFaceGroup);
    }

    @Override
    protected void setupAttribLocations() {
        int p = getProgramHandle();
        uVP = GLES20.glGetUniformLocation(p, "uVPMatrix");
        uCenter = GLES20.glGetUniformLocation(p, "uCenter");
        uRadius = GLES20.glGetUniformLocation(p, "uRadius");
        uRotation = GLES20.glGetUniformLocation(p, "uRotation");
        uColorA = GLES20.glGetUniformLocation(p, "uColorA");
        uColorB = GLES20.glGetUniformLocation(p, "uColorB");
        uLightPos = GLES20.glGetUniformLocation(p, "uLightPos");
        uLightColor = GLES20.glGetUniformLocation(p, "uLightColor");
        uCameraPos = GLES20.glGetUniformLocation(p, "uCameraPos");
        uAmbient = GLES20.glGetUniformLocation(p, "uAmbient");
        uDiffuse = GLES20.glGetUniformLocation(p, "uDiffuse");
        uSpecular = GLES20.glGetUniformLocation(p, "uSpecular");
        uShininess = GLES20.glGetUniformLocation(p, "uShininess");
        aPosition = GLES20.glGetAttribLocation(p, "aPosition");
        aNormal = GLES20.glGetAttribLocation(p, "aNormal");
        aFaceGroup = GLES20.glGetAttribLocation(p, "aFaceGroup");
    }

    @Override
    protected void transferUniformArgsToGPU(PortalSphereShaderArgs.VS v,
                                            PortalSphereShaderArgs.FS f) {
        GLES20.glUniformMatrix4fv(uVP, 1, false, v.vp, 0);
        GLES20.glUniform3f(uCenter, v.centerX, v.centerY, v.centerZ);
        GLES20.glUniform1f(uRadius, v.radius);
        GLES20.glUniformMatrix3fv(uRotation, 1, false, v.rotation, 0);

        GLES20.glUniform4fv(uColorA, 1, f.colorA.rgba, 0);
        GLES20.glUniform4fv(uColorB, 1, f.colorB.rgba, 0);
        GLES20.glUniform3f(uLightPos, f.lightX, f.lightY, f.lightZ);
        GLES20.glUniform3f(uLightColor, f.lightColor.r(), f.lightColor.g(), f.lightColor.b());
        GLES20.glUniform3f(uCameraPos, f.cameraX, f.cameraY, f.cameraZ);
        GLES20.glUniform1f(uAmbient, f.ambient);
        GLES20.glUniform1f(uDiffuse, f.diffuse);
        GLES20.glUniform1f(uSpecular, f.specular);
        GLES20.glUniform1f(uShininess, f.shininess);
    }

    public static final class Builder
            extends ShaderPair.BaseBuilder<PortalSphereShaderPair, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected PortalSphereShaderPair create(int programHandle, String vs, String fs) {
            return new PortalSphereShaderPair(programHandle, vs, fs);
        }
    }
}
