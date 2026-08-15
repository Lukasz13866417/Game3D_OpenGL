package com.example.game3d_opengl.game.terrain.track_elements.portal.rendering;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.layout.VertexLayout;
import com.example.game3d_opengl.rendering.shader.MeshShaderPair;
import com.example.game3d_opengl.rendering.shader.ShaderPair;

/**
 * Screen-space wireframe shader for the portal icosahedron.
 * Each edge is a quad (4 verts) with per-vertex attributes:
 *   aPosA(3), aPosB(3), aEnd(1), aSide(1) = 8 floats, stride 32 bytes.
 * The vertex shader transforms both endpoints through uCenter + uRotation * (pos * uScale),
 * projects to screen space, and offsets perpendicular to the edge direction.
 */
public final class PortalWireframeShaderPair
        <L extends VertexLayout.HasPositionA
                & VertexLayout.HasPositionB
                & VertexLayout.HasEdgeEnd
                & VertexLayout.HasEdgeSide>
        extends MeshShaderPair<PortalWireframeShaderArgs.VS, PortalWireframeShaderArgs.FS, L> {

    private int uVP, uCenter, uScale, uRotation;
    private int uViewport, uHalfPx, uDepthBiasNDC, uColor;
    private int aPosA, aPosB, aEnd, aSide;

    private PortalWireframeShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }

    /** Creates a shader program for the caller's current GL context. */
    public static PortalWireframeShaderPair<VertexLayout.EdgeABLayout> createContextShader() {
        String vs =
                "#version 300 es\n" +
                "uniform mat4 uVPMatrix;\n" +
                "uniform vec3 uCenter;\n" +
                "uniform vec3 uScale;\n" +
                "uniform mat3 uRotation;\n" +
                "uniform vec2 uViewport;\n" +
                "uniform float uHalfPx;\n" +
                "uniform float uDepthBiasNDC;\n" +
                "in vec3 aPosA;\n" +
                "in vec3 aPosB;\n" +
                "in float aEnd;\n" +
                "in float aSide;\n" +
                "vec3 toWorld(vec3 p){ return uCenter + uRotation * (p * uScale); }\n" +
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
                "#version 300 es\n" +
                "precision mediump float;\n" +
                "uniform vec4 uColor;\n" +
                "out vec4 fragColor;\n" +
                "void main(){\n" +
                "  fragColor = uColor;\n" +
                "}";
        return new Builder().fromSource(vs, fs).build();
    }

    @Override
    protected void enableAndPointVertexAttribs(L layout) {
        final int stride = layout.strideBytes();
        layout.positionA().enableAndPoint(aPosA, stride);
        layout.positionB().enableAndPoint(aPosB, stride);
        layout.edgeEnd().enableAndPoint(aEnd, stride);
        layout.edgeSide().enableAndPoint(aSide, stride);
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
        uScale = GLES20.glGetUniformLocation(p, "uScale");
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
        GLES20.glUniform3f(uScale, v.scaleX, v.scaleY, v.scaleZ);
        GLES20.glUniformMatrix3fv(uRotation, 1, false, v.rotation, 0);
        GLES20.glUniform2f(uViewport, v.viewportW, v.viewportH);
        GLES20.glUniform1f(uHalfPx, v.halfPx);
        GLES20.glUniform1f(uDepthBiasNDC, v.depthBiasNDC);
        GLES20.glUniform4f(uColor, f.color.r(), f.color.g(), f.color.b(), f.color.a());
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        deleteOwnedProgram();
    }

    public static final class Builder
            extends ShaderPair.BaseBuilder<PortalWireframeShaderPair<VertexLayout.EdgeABLayout>, Builder> {
        @Override
        protected Builder self() { return this; }

        @Override
        protected PortalWireframeShaderPair<VertexLayout.EdgeABLayout> create(
                int programHandle, String vs, String fs) {
            return new PortalWireframeShaderPair<>(programHandle, vs, fs);
        }
    }
}
