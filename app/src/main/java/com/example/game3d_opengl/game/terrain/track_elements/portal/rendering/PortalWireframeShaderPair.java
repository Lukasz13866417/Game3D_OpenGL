package com.example.game3d_opengl.game.terrain.track_elements.portal.rendering;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.shader.ShaderPair;

/**
 * Screen-space wireframe shader for the portal icosahedron.
 * Each edge is a quad (4 verts) with per-vertex attributes:
 *   aPosA(3), aPosB(3), aEnd(1), aSide(1) = 8 floats, stride 32 bytes.
 * The vertex shader transforms both endpoints through uCenter + uRotation * (pos * uRadius),
 * projects to screen space, and offsets perpendicular to the edge direction.
 */
public final class PortalWireframeShaderPair
        extends ShaderPair<PortalWireframeShaderArgs.VS, PortalWireframeShaderArgs.FS> {

    private static PortalWireframeShaderPair sharedShader = null;

    private int uVP, uCenter, uRadius, uRotation;
    private int uViewport, uHalfPx, uDepthBiasNDC, uColor;
    private int aPosA, aPosB, aEnd, aSide;

    private PortalWireframeShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    public static PortalWireframeShaderPair getSharedShader() {
        if (sharedShader == null) {
            sharedShader = createDefault();
        }
        return sharedShader;
    }

    private static PortalWireframeShaderPair createDefault() {
        String vs =
                "uniform mat4 uVPMatrix;\n" +
                "uniform vec3 uCenter;\n" +
                "uniform float uRadius;\n" +
                "uniform mat3 uRotation;\n" +
                "uniform vec2 uViewport;\n" +
                "uniform float uHalfPx;\n" +
                "uniform float uDepthBiasNDC;\n" +
                "attribute vec3 aPosA;\n" +
                "attribute vec3 aPosB;\n" +
                "attribute float aEnd;\n" +
                "attribute float aSide;\n" +
                "vec3 toWorld(vec3 p){ return uCenter + uRotation * p * uRadius; }\n" +
                "vec2 ndc(vec4 clip){ return clip.xy / clip.w; }\n" +
                "void main(){\n" +
                "  vec3 wA = toWorld(aPosA);\n" +
                "  vec3 wB = toWorld(aPosB);\n" +
                "  vec4 clipA = uVPMatrix * vec4(wA, 1.0);\n" +
                "  vec4 clipB = uVPMatrix * vec4(wB, 1.0);\n" +
                "  vec2 ndcA = ndc(clipA);\n" +
                "  vec2 ndcB = ndc(clipB);\n" +
                "  vec2 ndc2px = 0.5 * uViewport;\n" +
                "  vec2 d_pix = (ndcB - ndcA) * ndc2px;\n" +
                "  float l2 = dot(d_pix, d_pix);\n" +
                "  vec2 n_pix = (l2 > 1e-8) ? normalize(vec2(-d_pix.y, d_pix.x)) : vec2(0.0);\n" +
                "  vec2 delta_ndc = (uHalfPx * n_pix) / ndc2px;\n" +
                "  vec4 P_clip = mix(clipA, clipB, aEnd);\n" +
                "  vec2 P_ndc  = mix(ndcA,  ndcB,  aEnd);\n" +
                "  vec2 out_ndc = P_ndc + aSide * delta_ndc;\n" +
                "  gl_Position = vec4(out_ndc * P_clip.w, P_clip.z, P_clip.w);\n" +
                "  gl_Position.z += uDepthBiasNDC * gl_Position.w;\n" +
                "}";
        String fs =
                "precision mediump float;\n" +
                "uniform vec4 uColor;\n" +
                "void main(){\n" +
                "  gl_FragColor = uColor;\n" +
                "}";
        return new Builder().fromSource(vs, fs).build();
    }

    @Override
    public void enableAndPointVertexAttribs() {
        final int stride = 8 * 4;
        GLES20.glEnableVertexAttribArray(aPosA);
        GLES20.glVertexAttribPointer(aPosA, 3, GLES20.GL_FLOAT, false, stride, 0);
        GLES20.glEnableVertexAttribArray(aPosB);
        GLES20.glVertexAttribPointer(aPosB, 3, GLES20.GL_FLOAT, false, stride, 3 * 4);
        GLES20.glEnableVertexAttribArray(aEnd);
        GLES20.glVertexAttribPointer(aEnd, 1, GLES20.GL_FLOAT, false, stride, 6 * 4);
        GLES20.glEnableVertexAttribArray(aSide);
        GLES20.glVertexAttribPointer(aSide, 1, GLES20.GL_FLOAT, false, stride, 7 * 4);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aPosA);
        GLES20.glDisableVertexAttribArray(aPosB);
        GLES20.glDisableVertexAttribArray(aEnd);
        GLES20.glDisableVertexAttribArray(aSide);
    }

    @Override
    protected void setupAttribLocations() {
        int p = getProgramHandle();
        uVP = GLES20.glGetUniformLocation(p, "uVPMatrix");
        uCenter = GLES20.glGetUniformLocation(p, "uCenter");
        uRadius = GLES20.glGetUniformLocation(p, "uRadius");
        uRotation = GLES20.glGetUniformLocation(p, "uRotation");
        uViewport = GLES20.glGetUniformLocation(p, "uViewport");
        uHalfPx = GLES20.glGetUniformLocation(p, "uHalfPx");
        uDepthBiasNDC = GLES20.glGetUniformLocation(p, "uDepthBiasNDC");
        uColor = GLES20.glGetUniformLocation(p, "uColor");
        aPosA = GLES20.glGetAttribLocation(p, "aPosA");
        aPosB = GLES20.glGetAttribLocation(p, "aPosB");
        aEnd = GLES20.glGetAttribLocation(p, "aEnd");
        aSide = GLES20.glGetAttribLocation(p, "aSide");
    }

    @Override
    protected void transferUniformArgsToGPU(PortalWireframeShaderArgs.VS v,
                                            PortalWireframeShaderArgs.FS f) {
        GLES20.glUniformMatrix4fv(uVP, 1, false, v.vp, 0);
        GLES20.glUniform3f(uCenter, v.centerX, v.centerY, v.centerZ);
        GLES20.glUniform1f(uRadius, v.radius);
        GLES20.glUniformMatrix3fv(uRotation, 1, false, v.rotation, 0);
        GLES20.glUniform2f(uViewport, v.viewportW, v.viewportH);
        GLES20.glUniform1f(uHalfPx, v.halfPx);
        GLES20.glUniform1f(uDepthBiasNDC, v.depthBiasNDC);
        GLES20.glUniform4f(uColor, f.color.r(), f.color.g(), f.color.b(), f.color.a());
    }

    public static final class Builder
            extends ShaderPair.BaseBuilder<PortalWireframeShaderPair, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected PortalWireframeShaderPair create(int programHandle, String vs, String fs) {
            return new PortalWireframeShaderPair(programHandle, vs, fs);
        }
    }
}
