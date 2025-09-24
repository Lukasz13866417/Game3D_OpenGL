package com.example.game3d_opengl.rendering.wireframe;

import android.content.res.AssetManager;
import android.opengl.GLES20;
import com.example.game3d_opengl.rendering.shader.ShaderPair;

/**
 * Thick-wire edges by extruding each edge (v0,v1) to a screen-space quad.
 * Vertex layout per-vertex (interleaved):
 *   aPos0.xyz, aPos1.xyz, aT, aSide   // 8 floats per vertex
 */
public final class WireframeShaderPair extends ShaderPair<WireframeShaderArgs.VS, WireframeShaderArgs.FS> {

    // Uniforms
    private int uMVP, uViewport, uColor, uHalfPx, uDepthBiasNDC;
    // Attributes
    private int aPosA, aPosB, aEnd, aSide;
    // Instance to use
    private static WireframeShaderPair sharedShader = null;


    public WireframeShaderPair(int programHandle, String vs, String fs) {
        super(programHandle, vs, fs);
    }


    public static WireframeShaderPair getSharedShader(){
        if (sharedShader == null){
            throw new IllegalStateException(
                    "Shader instance is null. Needs calling LOAD_SHADER_CODE first"
            );
        }
        return sharedShader;
    }

    public static void LOAD_SHADER_CODE(AssetManager assetManager) {
        String vs = "attribute vec3 aPosA;\n" +
                "attribute vec3 aPosB;\n" +
                "attribute float aEnd;   // 0.0 -> A, 1.0 -> B\n" +
                "attribute float aSide;  // -1.0 or +1.0\n" +
                "\n" +
                "uniform mat4 uMVP;\n" +
                "uniform vec2 uViewport; // (VW, VH)\n" +
                "uniform float uHalfPx;  // half thickness in pixels\n" +
                "uniform float uDepthBiasNDC;"+
                "\n" +
                "vec2 ndc(vec4 clip){ return clip.xy / clip.w; }\n" +
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
                "\n" +
                "    // Convert pixel offset back to NDC\n" +
                "    vec2 delta_ndc = (uHalfPx * n_pix) / ndc2px;\n" +
                "\n" +
                "    // Pick endpoint, apply ± offset in NDC, reinflate to clip (exact)\n" +
                "    vec4 P_clip = mix(A_clip, B_clip, aEnd);\n" +
                "    vec2 P_ndc  = mix(A_ndc,  B_ndc,  aEnd);\n" +
                "    vec2 out_ndc = P_ndc + aSide * delta_ndc;\n" +
                "\n" +
                "    gl_Position = vec4(out_ndc * P_clip.w, P_clip.z, P_clip.w);\n" +
                "gl_Position.z += uDepthBiasNDC * gl_Position.w;\n"+
                "}";


        String fs =
                "precision mediump float;\n" +
                        "uniform vec4 uColor;\n" +
                        "void main(){ gl_FragColor = uColor; }";
        sharedShader = new Builder().fromSource(vs, fs).build();
    }

    @Override
    protected void setupAttribLocations() {
        int p = getProgramHandle();
        uMVP = GLES20.glGetUniformLocation(p, "uMVP");
        uViewport = GLES20.glGetUniformLocation(p, "uViewport");
        uHalfPx = GLES20.glGetUniformLocation(p, "uHalfPx");
        uColor = GLES20.glGetUniformLocation(p, "uColor");
        uDepthBiasNDC = GLES20.glGetUniformLocation(p, "uDepthBiasNDC");

        aPosA = GLES20.glGetAttribLocation(p, "aPosA");
        aPosB = GLES20.glGetAttribLocation(p, "aPosB");
        aEnd  = GLES20.glGetAttribLocation(p, "aEnd");
        aSide = GLES20.glGetAttribLocation(p, "aSide");
    }

    @Override
    public void enableAndPointVertexAttribs() {
        final int stride = 8 * 4;
        GLES20.glEnableVertexAttribArray(aPosA);
        GLES20.glVertexAttribPointer(aPosA, 3, GLES20.GL_FLOAT, false, stride, 0);

        GLES20.glEnableVertexAttribArray(aPosB);
        GLES20.glVertexAttribPointer(aPosB, 3, GLES20.GL_FLOAT, false, stride, 12);

        GLES20.glEnableVertexAttribArray(aEnd);
        GLES20.glVertexAttribPointer(aEnd, 1, GLES20.GL_FLOAT, false, stride, 24);

        GLES20.glEnableVertexAttribArray(aSide);
        GLES20.glVertexAttribPointer(aSide, 1, GLES20.GL_FLOAT, false, stride, 28);
    }

    @Override
    public void disableVertexAttribs() {
        GLES20.glDisableVertexAttribArray(aPosA);
        GLES20.glDisableVertexAttribArray(aPosB);
        GLES20.glDisableVertexAttribArray(aEnd);
        GLES20.glDisableVertexAttribArray(aSide);
    }

    @Override
    protected void transferArgsToGPU(WireframeShaderArgs.VS v, WireframeShaderArgs.FS f) {
        GLES20.glUniformMatrix4fv(uMVP, 1, false, v.mvp, 0);
        GLES20.glUniform2f(uViewport, v.viewportW, v.viewportH);
        GLES20.glUniform1f(uHalfPx, v.halfPx);
        GLES20.glUniform4f(uColor, f.color.r(), f.color.g(), f.color.b(), f.color.a());
        GLES20.glUniform1f(uDepthBiasNDC, v.uDepthBiasNDC);
    }

    public static final class Builder extends ShaderPair.BaseBuilder<WireframeShaderPair, Builder> {
        @Override protected Builder self() { return this; }
        @Override protected WireframeShaderPair create(int programHandle, String vs, String fs) {
            return new WireframeShaderPair(programHandle, vs, fs);
        }
    }
}
