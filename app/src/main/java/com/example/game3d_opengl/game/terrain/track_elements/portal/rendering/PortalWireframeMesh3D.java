package com.example.game3d_opengl.game.terrain.track_elements.portal.rendering;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.mesh.AbstractMesh3D;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Wireframe mesh for the portal icosahedron. Each edge becomes a screen-space quad.
 * Vertex layout: aPosA(3), aPosB(3), aEnd(1), aSide(1) = 8 floats per vertex.
 */
public final class PortalWireframeMesh3D
        extends AbstractMesh3D<PortalWireframeDrawArgs, PortalWireframeShaderPair> {

    private static final int[] VIEWPORT_TMP = new int[4];
    private static final float DEPTH_BIAS_NDC = -2e-4f;

    private final PortalWireframeShaderArgs.VS vs = new PortalWireframeShaderArgs.VS();
    private final PortalWireframeShaderArgs.FS fs = new PortalWireframeShaderArgs.FS();
    private final float halfPx;

    private PortalWireframeMesh3D(Builder b) {
        super(b);
        this.halfPx = b.halfPx;
    }

    @Override
    public void draw(PortalWireframeDrawArgs args) {
        GLES20.glDepthMask(false);
        try {
            super.draw(args);
        } finally {
            GLES20.glDepthMask(true);
        }
    }

    @Override
    protected void setVariableArgsValues(PortalWireframeDrawArgs args,
                                         PortalWireframeShaderPair shader) {
        vs.vp = args.vp;
        vs.centerX = args.centerX;
        vs.centerY = args.centerY;
        vs.centerZ = args.centerZ;
        vs.radius = args.radius;
        vs.rotation = args.rotation;
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, VIEWPORT_TMP, 0);
        vs.viewportW = Math.max(1, VIEWPORT_TMP[2]);
        vs.viewportH = Math.max(1, VIEWPORT_TMP[3]);
        vs.halfPx = halfPx;
        vs.depthBiasNDC = DEPTH_BIAS_NDC;

        fs.color = args.color;
        shader.setArgs(vs, fs);
    }

    public static final class Builder
            extends BaseBuilder<PortalWireframeMesh3D, Builder, PortalWireframeShaderPair> {

        private float halfPx = 0.7f;
        private int[][] edges;

        public Builder halfPx(float px) { this.halfPx = px; return this; }
        public Builder edges(int[][] edges) { this.edges = edges; return this; }

        @Override
        public void checkValid() {
            shader(PortalWireframeShaderPair.getSharedShader());
            super.checkValid();
        }

        @Override
        protected float[] setVertexData() {
            final int vertsPerEdge = 4;
            final int floatsPerVert = 8;
            float[] out = new float[edges.length * vertsPerEdge * floatsPerVert];
            int o = 0;
            for (int[] e : edges) {
                Vector3D a = verts[e[0]];
                Vector3D b = verts[e[1]];
                o = putVert(out, o, a, b, 0f, -1f);
                o = putVert(out, o, a, b, 0f, +1f);
                o = putVert(out, o, a, b, 1f, -1f);
                o = putVert(out, o, a, b, 1f, +1f);
            }
            int nEdges = edges.length;
            int[][] newFaces = new int[nEdges][];
            for (int i = 0; i < nEdges; i++) {
                newFaces[i] = new int[]{i * 4, i * 4 + 1, i * 4 + 3, i * 4 + 2};
            }
            this.faces = newFaces;
            this.verts = new Vector3D[]{new Vector3D(0, 0, 0)};
            return out;
        }

        private static int putVert(float[] dst, int o,
                                   Vector3D a, Vector3D b,
                                   float end, float side) {
            dst[o++] = a.x; dst[o++] = a.y; dst[o++] = a.z;
            dst[o++] = b.x; dst[o++] = b.y; dst[o++] = b.z;
            dst[o++] = end;
            dst[o++] = side;
            return o;
        }

        @Override
        protected Builder self() { return this; }

        @Override
        protected PortalWireframeMesh3D create() { return new PortalWireframeMesh3D(this); }
    }
}
