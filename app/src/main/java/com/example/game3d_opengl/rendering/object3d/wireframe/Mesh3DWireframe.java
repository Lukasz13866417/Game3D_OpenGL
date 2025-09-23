package com.example.game3d_opengl.rendering.object3d.wireframe;

import com.example.game3d_opengl.rendering.Camera;
import com.example.game3d_opengl.rendering.object3d.AbstractMesh3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.util.ArrayList;

public class Mesh3DWireframe extends AbstractMesh3D<WireframeShaderPair> {

    private final FColor edgeColor;
    final float pixelWidth;

    public Mesh3DWireframe(Builder builder) {
        super(builder);
        this.edgeColor = builder.edgeColor;
        this.pixelWidth = builder.pixelWidth;
        this.fs = new WireframeShaderArgs.FS();
        this.vs = new WireframeShaderArgs.VS();
    }

    private final WireframeShaderArgs.VS vs;

    // Fragment shader args are easy
    private final WireframeShaderArgs.FS fs;


    @Override
    protected void setVariableArgsValues(float[] mvp, WireframeShaderPair s) {
        // vertex shader uniform args
        vs.color = edgeColor;
        vs.mvp = mvp;
        vs.halfPx = pixelWidth;
        vs.viewportW = Camera.SCREEN_WIDTH;
        vs.viewportH = Camera.SCREEN_HEIGHT;
        vs.uDepthBiasNDC = -5e-3f; // TODO change to builder arg.

        // fragment shader is easy here
        fs.color = edgeColor;
        s.setArgs(vs, fs);
    }

    public static class Builder extends BaseBuilder<Mesh3DWireframe, Builder, WireframeShaderPair> {
        private FColor edgeColor;

        private final float UNSET_PIXEL_WIDTH = -1f;
        private float pixelWidth = UNSET_PIXEL_WIDTH; // desired thickness in pixels

        public Builder edgeColor(FColor c) {
            this.edgeColor = c;
            return this;
        }

        public Builder pixelWidth(float px) {
            this.pixelWidth = px;
            return this;
        }

        @Override
        public void checkValid() {
            shader(WireframeShaderPair.getSharedShader());
            super.checkValid();
            assert edgeColor != null;
            assert pixelWidth != UNSET_PIXEL_WIDTH;
        }

        @Override
        protected float[] setVertexData() {
            // 1) Extract edges from original user faces (dedup optional)
            ArrayList<int[]> edges = new ArrayList<>();
            for (int[] face : faces) {
                int n = face.length;
                for (int k = 0; k < n; ++k) {
                    int i = face[k];
                    int j = face[(k + 1) % n];
                    if (i == j) continue;
                    int a = Math.min(i, j), b = Math.max(i, j);
                    edges.add(new int[]{a, b});
                }
            }

            final int vertsPerEdge = 4;
            final int floatsPerVert = 8; // aPosA(3) + aPosB(3) + aEnd(1) + aSide(1)
            float[] out = new float[edges.size() * vertsPerEdge * floatsPerVert];

            // 2) Build vertex stream and NEW faces that reference this stream
            int[][] newFaces = new int[edges.size()][];
            int vFloat = 0;
            int vBase  = 0; // counts vertices in the *expanded* VBO (increments by 4 per edge)

            for (int e = 0; e < edges.size(); ++e) {
                int ia = edges.get(e)[0], ib = edges.get(e)[1];
                Vector3D A = verts[ia], B = verts[ib];

                // Emit the 4 vertices for this edge (A-, A+, B-, B+)
                vFloat = putEdgeVert(out, vFloat, A, B, 0f, -1f); // base+0
                vFloat = putEdgeVert(out, vFloat, A, B, 0f, +1f); // base+1
                vFloat = putEdgeVert(out, vFloat, A, B, 1f, -1f); // base+2
                vFloat = putEdgeVert(out, vFloat, A, B, 1f, +1f); // base+3

                newFaces[e] = new int[]{ vBase + 0, vBase + 1, vBase + 2, vBase + 3 };

                vBase += 4;
            }

            // 3) Replace faces so the base class triangulates them into the IBO
            this.faces = newFaces;

            return out;
        }

        private static int putEdgeVert(float[] dst, int off,
                                       Vector3D A, Vector3D B,
                                       float end, float side) {
            // aPosA
            dst[off++] = (float)A.x; dst[off++] = (float)A.y; dst[off++] = (float)A.z;
            // aPosB
            dst[off++] = (float)B.x; dst[off++] = (float)B.y; dst[off++] = (float)B.z;
            // aEnd, aSide
            dst[off++] = end; dst[off++] = side;
            return off;
        }
        @Override
        public Builder self() {
            return this;
        }

        @Override
        public Mesh3DWireframe create() {
            return new Mesh3DWireframe(this);
        }

    }
}


