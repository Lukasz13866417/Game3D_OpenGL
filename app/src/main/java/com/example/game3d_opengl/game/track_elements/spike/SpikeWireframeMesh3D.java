package com.example.game3d_opengl.game.track_elements.spike;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.mesh.AbstractMesh3D;
import com.example.game3d_opengl.rendering.mesh.MeshDrawArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Custom mesh for spike wireframe. Builds an expanded vertex stream per canonical edge:
 * each canonical edge (A,B) becomes 4 vertices (A-, A+, B-, B+) with attributes:
 *   aWeightsA(4), aTA(1), aWeightsB(4), aTB(1), aEnd(1), aSide(1)
 * Stride: 12 floats per vertex.
 */
public final class SpikeWireframeMesh3D extends AbstractMesh3D<SpikeWireframeDrawArgs, SpikeWireframeShaderPair> {

    private final FColor edgeColor;
    private final float pixelWidth;
    private final float uDepthBiasNDC;

    // Per-instance spike uniforms
    private final float[] uNL, uNR, uFR, uFL, uApex, uNormal;
    private final float uBaseOffset;

    private final SpikeWireframeShaderArgs.VS vs;
    private final SpikeWireframeShaderArgs.FS fs;

    private SpikeWireframeMesh3D(Builder b) {
        super(b);
        this.edgeColor = b.edgeColor;
        this.pixelWidth = b.pixelWidth;
        this.uDepthBiasNDC = b.uDepthBiasNDC;
        this.uNL = b.uNL; this.uNR = b.uNR; this.uFR = b.uFR; this.uFL = b.uFL;
        this.uApex = b.uApex; this.uNormal = b.uNormal; this.uBaseOffset = b.uBaseOffset;

        this.vs = new SpikeWireframeShaderArgs.VS();
        this.fs = new SpikeWireframeShaderArgs.FS();
    }

    @Override
    public void draw(SpikeWireframeDrawArgs args) {
        GLES20.glDepthMask(false);
        try {
            super.draw(args);
        } finally {
            GLES20.glDepthMask(true);
        }
    }

    @Override
    protected void setVariableArgsValues(SpikeWireframeDrawArgs args, SpikeWireframeShaderPair shader) {
        vs.mvp = args.vp;
        vs.viewportW = args.viewportW;
        vs.viewportH = args.viewportH;
        vs.halfPx = args.halfPx;
        vs.uDepthBiasNDC = args.uDepthBiasNDC;
        vs.uNL = args.uNL; vs.uNR = args.uNR; vs.uFR = args.uFR; vs.uFL = args.uFL; vs.uApex = args.uApex; vs.uNormal = args.uNormal;
        vs.uBaseOffset = args.uBaseOffset;

        fs.color = args.color != null ? args.color : edgeColor;
        shader.setArgs(vs, fs);
    }

    public static final class Builder extends AbstractMesh3D.BaseBuilder<SpikeWireframeMesh3D, Builder, SpikeWireframeShaderPair> {
        private FColor edgeColor = FColor.CLR(1,1,1,1);
        private float pixelWidth = 1.5f;
        private float uDepthBiasNDC = -2e-4f;

        // Per-instance uniforms
        private float[] uNL, uNR, uFR, uFL, uApex, uNormal;
        private float uBaseOffset;

        public Builder color(FColor c){ this.edgeColor = c; return this; }
        public Builder pixelWidth(float px){ this.pixelWidth = px; return this; }
        public Builder depthBias(float ndc){ this.uDepthBiasNDC = ndc; return this; }
        public Builder instanceUniforms(float[] nl, float[] nr, float[] fr, float[] fl,
                                        float[] apex, float[] normal, float baseOffset){
            this.uNL = nl; this.uNR = nr; this.uFR = fr; this.uFL = fl;
            this.uApex = apex; this.uNormal = normal; this.uBaseOffset = baseOffset; return this; }

        @Override
        protected Builder self() { return this; }

        @Override
        protected SpikeWireframeMesh3D create() { return new SpikeWireframeMesh3D(this); }

        @Override
        public void checkValid() {
            shader(SpikeWireframeShaderPair.sharedShader);
            super.checkValid();
        }

        @Override
        protected float[] setVertexData() {
            // Canonical spike vertices: weights + t
            float[][] canonical = new float[][]{
                    {1,0,0,0, 0}, // NL
                    {0,1,0,0, 0}, // NR
                    {0,0,1,0, 0}, // FR
                    {0,0,0,1, 0}, // FL
                    {0,0,0,0, 1}  // Apex
            };
            // Edges to render:
            //  - Base perimeter: (NL,NR), (NR,FR), (FR,FL), (FL,NL)
            //  - Sides to apex: (NL,Apex), (NR,Apex), (FR,Apex), (FL,Apex)
            int[][] edges = new int[][]{{0,1},{1,2},{2,3},{3,0},{0,4},{1,4},{2,4},{3,4}};

            final int vertsPerEdge = 4;
            final int floatsPerVert = 12;
            float[] out = new float[edges.length * vertsPerEdge * floatsPerVert];
            int o = 0;
            for (int[] e : edges){
                int ia = e[0], ib = e[1];
                float[] A = canonical[ia];
                float[] B = canonical[ib];
                // Emit A-, A+, B-, B+
                o = putVert(out, o, A, B, 0f, -1f);
                o = putVert(out, o, A, B, 0f, +1f);
                o = putVert(out, o, A, B, 1f, -1f);
                o = putVert(out, o, A, B, 1f, +1f);
            }

            // Replace faces by quads per edge so base class triangulates them
            int nEdges = edges.length;
            int[][] newFaces = new int[nEdges][];
            for (int i = 0; i < nEdges; i++) newFaces[i] = new int[]{ i*4+0, i*4+1, i*4+2, i*4+3 };
            this.faces = newFaces;

            // Provide dummy verts array to satisfy builder contract (not used by shader)
            this.verts = new Vector3D[]{ new Vector3D(0,0,0) };
            return out;
        }

        private static int putVert(float[] dst, int o, float[] A, float[] B, float end, float side){
            // aWeightsA (4)
            dst[o++] = A[0]; dst[o++] = A[1]; dst[o++] = A[2]; dst[o++] = A[3];
            // aTA (1)
            dst[o++] = A[4];
            // aWeightsB (4)
            dst[o++] = B[0]; dst[o++] = B[1]; dst[o++] = B[2]; dst[o++] = B[3];
            // aTB (1)
            dst[o++] = B[4];
            // aEnd (1)
            dst[o++] = end;
            // aSide (1)
            dst[o++] = side;
            return o;
        }
    }
}


