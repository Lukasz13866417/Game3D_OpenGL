package com.example.game3d_opengl.rendering.wireframe;

import android.content.res.AssetManager;
import android.opengl.GLES20;
import com.example.game3d_opengl.rendering.layout.VertexLayout;
import com.example.game3d_opengl.rendering.shader.MeshShaderPair;
import com.example.game3d_opengl.rendering.shader.ShaderPair;

/**
 * Thick-wire edges by extruding each edge (v0,v1) to a screen-space quad.
 * Vertex layout per-vertex (interleaved):
 *   aPos0.xyz, aPos1.xyz, aT, aSide   // 8 floats per vertex
 */
public final class WireframeShaderPair<
        L extends VertexLayout.HasPositionA
                & VertexLayout.HasPositionB
                & VertexLayout.HasEdgeEnd
                & VertexLayout.HasEdgeSide>
        extends MeshShaderPair<WireframeShaderArgs.VS, WireframeShaderArgs.FS, L> {

    // Uniforms
    private int uMVP, uViewport, uViewportOrigin, uColor, uHalfPx, uCapPx, uDepthBiasNDC;
    // Attributes
    private int aPosA, aPosB, aEnd, aSide;
    // Instance to use
    private static WireframeShaderPair<VertexLayout.EdgeABLayout> sharedShader = null;


    public WireframeShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }


    public static WireframeShaderPair<VertexLayout.EdgeABLayout> getSharedShader(){
        if (sharedShader == null){
            throw new IllegalStateException(
                    "Shader instance is null. Needs calling LOAD_SHADER_CODE first"
            );
        }
        return sharedShader;
    }

    public static void LOAD_SHADER_CODE(AssetManager assetManager) {
        if (sharedShader != null) {
            return;
        }
        String vs = "#version 300 es\n" +
                "in vec3 aPosA;\n" +
                "in vec3 aPosB;\n" +
                "in float aEnd;   // 0.0 -> A, 1.0 -> B\n" +
                "in float aSide;  // -1.0 or +1.0\n" +
                "\n" +
                "uniform mat4 uMVP;\n" +
                "uniform vec2 uViewport; // (VW, VH)\n" +
                "uniform float uHalfPx;  // half thickness in pixels\n" +
                "uniform float uCapPx;   // extra length at ends in pixels\n" +
                "uniform float uDepthBiasNDC;"+
                "\n" +
                "vec2 ndc(vec4 clip){ return clip.xy / clip.w; }\n" +
                "out highp vec2 vA_ndc;\n" +
                "out highp vec2 vB_ndc;\n" +
                "\n" +
                "void main(){\n" +
                "    // Transform both endpoints to clip\n" +
                "    vec4 A_clip = uMVP * vec4(aPosA, 1.0);\n" +
                "    vec4 B_clip = uMVP * vec4(aPosB, 1.0);\n" +
                "\n" +
                "    // Work in NDC for direction; measure in pixels\n" +
                "    vec2 A_ndc = ndc(A_clip);\n" +
                "    vec2 B_ndc = ndc(B_clip);\n" +
                "    vec2 ndc2px = 0.5 * uViewport;\n" +
                "\n" +
                "    vec2 d_pix = (B_ndc - A_ndc) * ndc2px;\n" +
                "    float l2 = dot(d_pix, d_pix);\n" +
                "    vec2 n_pix = (l2 > 1e-8) ? normalize(vec2(-d_pix.y, d_pix.x)) : vec2(0.0);\n" +
                "    vec2 dir_pix = (l2 > 1e-8) ? normalize(d_pix) : vec2(0.0);\n" +
                "\n" +
                "    // Convert pixel offsets back to NDC\n" +
                "    vec2 delta_ndc = (uHalfPx * n_pix) / ndc2px;\n" +
                "    vec2 cap_ndc = (uCapPx * dir_pix) / ndc2px;\n" +
                "\n" +
                "    // Extend endpoints for overlap, keep originals for cap clipping\n" +
                "    vec2 A_ext = A_ndc - cap_ndc;\n" +
                "    vec2 B_ext = B_ndc + cap_ndc;\n" +
                "\n" +
                "    // Pick endpoint, apply ± offset in NDC, reinflate to clip (exact)\n" +
                "    vec4 P_clip = mix(A_clip, B_clip, aEnd);\n" +
                "    vec2 P_ndc  = mix(A_ext,  B_ext,  aEnd);\n" +
                "    vec2 out_ndc = P_ndc + aSide * delta_ndc;\n" +
                "    vA_ndc = A_ndc;\n" +
                "    vB_ndc = B_ndc;\n" +
                "\n" +
                "    gl_Position = vec4(out_ndc * P_clip.w, P_clip.z, P_clip.w);\n" +
                "gl_Position.z += uDepthBiasNDC * gl_Position.w;\n"+
                "}";


        String fs =
                "#version 300 es\n" +
                        "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
                        "precision highp float;\n" +
                        "#else\n" +
                        "precision mediump float;\n" +
                        "#endif\n" +
                        "uniform vec4 uColor;\n" +
                        "uniform vec2 uViewport;\n" +
                        "uniform vec2 uViewportOrigin;\n" +
                        "uniform float uHalfPx;\n" +
                        "in highp vec2 vA_ndc;\n" +
                        "in highp vec2 vB_ndc;\n" +
                        "out vec4 fragColor;\n" +
                        "void main(){\n" +
                        "  vec2 A_px = uViewportOrigin + (vA_ndc * 0.5 + 0.5) * uViewport;\n" +
                        "  vec2 B_px = uViewportOrigin + (vB_ndc * 0.5 + 0.5) * uViewport;\n" +
                        "  vec2 P_px = gl_FragCoord.xy;\n" +
                        "  vec2 AB = B_px - A_px;\n" +
                        "  float len2 = dot(AB, AB);\n" +
                        "  float t = 0.0;\n" +
                        "  if (len2 > 1e-6) {\n" +
                        "    t = dot(P_px - A_px, AB) / len2;\n" +
                        "  }\n" +
                        "  float dist;\n" +
                        "  if (t < 0.0) {\n" +
                        "    dist = length(P_px - A_px);\n" +
                        "  } else if (t > 1.0) {\n" +
                        "    dist = length(P_px - B_px);\n" +
                        "  } else {\n" +
                        "    float area = abs(AB.x * (P_px.y - A_px.y) - AB.y * (P_px.x - A_px.x));\n" +
                        "    dist = area / sqrt(len2 + 1e-6);\n" +
                        "  }\n" +
                        "  if (dist > uHalfPx) discard;\n" +
                        "  fragColor = uColor; }\n";
        sharedShader = new Builder().fromSource(vs, fs).build();
    }

    @Override
    protected void setupAttribLocations() {
        int p = getProgramHandle();
        uMVP = GLES20.glGetUniformLocation(p, "uMVP");
        uViewport = GLES20.glGetUniformLocation(p, "uViewport");
        uViewportOrigin = GLES20.glGetUniformLocation(p, "uViewportOrigin");
        uHalfPx = GLES20.glGetUniformLocation(p, "uHalfPx");
        uCapPx = GLES20.glGetUniformLocation(p, "uCapPx");
        uColor = GLES20.glGetUniformLocation(p, "uColor");
        uDepthBiasNDC = GLES20.glGetUniformLocation(p, "uDepthBiasNDC");

        aPosA = GLES20.glGetAttribLocation(p, "aPosA");
        aPosB = GLES20.glGetAttribLocation(p, "aPosB");
        aEnd  = GLES20.glGetAttribLocation(p, "aEnd");
        aSide = GLES20.glGetAttribLocation(p, "aSide");
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
    protected void transferUniformArgsToGPU(WireframeShaderArgs.VS v, WireframeShaderArgs.FS f) {
        GLES20.glUniformMatrix4fv(uMVP, 1, false, v.mvp, 0);
        GLES20.glUniform2f(uViewport, v.viewportW, v.viewportH);
        GLES20.glUniform2f(uViewportOrigin, v.viewportX, v.viewportY);
        GLES20.glUniform1f(uHalfPx, v.halfPx);
        GLES20.glUniform1f(uCapPx, v.capPx);
        GLES20.glUniform4f(uColor, f.color.r(), f.color.g(), f.color.b(), f.color.a());
        GLES20.glUniform1f(uDepthBiasNDC, v.uDepthBiasNDC);
    }

    public static final class Builder
            extends ShaderPair.BaseBuilder<WireframeShaderPair<VertexLayout.EdgeABLayout>, Builder> {
        @Override protected Builder self() { return this; }
        @Override protected WireframeShaderPair<VertexLayout.EdgeABLayout> create(
                int programHandle, String vs, String fs) {
            return new WireframeShaderPair<>(programHandle, vs, fs);
        }
    }
}
