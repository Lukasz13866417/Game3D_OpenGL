package com.example.game3d_opengl.rendering.wireframe;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.layout.VertexLayout;
import com.example.game3d_opengl.rendering.mesh.AbstractMesh3D;
import com.example.game3d_opengl.rendering.mesh.BaseMeshDrawArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.util.ArrayList;

public class Mesh3DWireframe extends AbstractMesh3D<
        BaseMeshDrawArgs,
        WireframeShaderPair<VertexLayout.EdgeABLayout>,
        VertexLayout.EdgeABLayout> {

    private final FColor edgeColor;
    final float pixelWidth;
    final float capPixels;

    public Mesh3DWireframe(Builder builder) {
        super(builder);
        this.edgeColor = builder.edgeColor;
        this.pixelWidth = builder.pixelWidth;
        this.capPixels = builder.capPixels;
        this.fs = new WireframeShaderArgs.FS();
        this.vs = new WireframeShaderArgs.VS();
    }

    private final WireframeShaderArgs.VS vs;
    private static final int[] VIEWPORT_TMP = new int[4];

    // Fragment shader args are easy
    private final WireframeShaderArgs.FS fs;

    public boolean canUpdateGeometry(Vector3D[] verts, int[][] faces) {
        float[] vertexData = buildExpandedVertexData(verts, faces);
        return canUpdateVertexData(vertexData.length);
    }

    public void updateGeometry(Vector3D[] verts, int[][] faces) {
        updateVertexData(buildExpandedVertexData(verts, faces));
    }


    @Override
    protected void setVariableArgsValues(
            BaseMeshDrawArgs args,
            WireframeShaderPair<VertexLayout.EdgeABLayout> s) {
        // vertex shader uniform args
        vs.color = edgeColor;
        vs.mvp = args.vp;
        vs.halfPx = pixelWidth;
        vs.capPx = capPixels;
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, VIEWPORT_TMP, 0);
        applyViewport(VIEWPORT_TMP, vs);
        vs.uDepthBiasNDC = -5e-3f; // TODO change to builder arg.

        // fragment shader is easy here
        fs.color = edgeColor;
        s.setArgs(vs, fs);
    }

    static void applyViewport(int[] viewport, WireframeShaderArgs.VS target) {
        target.viewportX = viewport[0];
        target.viewportY = viewport[1];
        target.viewportW = Math.max(1, viewport[2]);
        target.viewportH = Math.max(1, viewport[3]);
    }

    public static class Builder extends BaseBuilder<
            Mesh3DWireframe,
            Builder,
            BaseMeshDrawArgs,
            WireframeShaderPair<VertexLayout.EdgeABLayout>,
            VertexLayout.EdgeABLayout> {
        private FColor edgeColor;

        private final float UNSET_PIXEL_WIDTH = -1f;
        private float pixelWidth = UNSET_PIXEL_WIDTH; // desired thickness in pixels
        private float capPixels = Float.NaN;

        public Builder edgeColor(FColor c) {
            this.edgeColor = c;
            return this;
        }

        public Builder pixelWidth(float px) {
            this.pixelWidth = px;
            return this;
        }

        public Builder capPixels(float px) {
            this.capPixels = px;
            return this;
        }

        @Override
        public void checkValid() {
            shader(WireframeShaderPair.getSharedShader());
            super.checkValid();
            assert edgeColor != null;
            assert pixelWidth != UNSET_PIXEL_WIDTH;
            if (Float.isNaN(capPixels)) {
                capPixels = pixelWidth;
            }
        }

        @Override
        protected float[] setVertexData() {
            // 1) Extract edges from original user faces (dedup optional)
            ArrayList<int[]> edges = extractEdges(faces);
            float[] out = buildExpandedVertexData(verts, edges);

            // 2) Build vertex stream and NEW faces that reference this stream
            int[][] newFaces = new int[edges.size()][];
            int vBase  = 0; // counts vertices in the *expanded* VBO (increments by 4 per edge)

            for (int e = 0; e < edges.size(); ++e) {
                // Order vertices so the diagonal spans across the line width (A- -> B+),
                // avoiding a split along the line direction.
                newFaces[e] = new int[]{ vBase + 0, vBase + 1, vBase + 3, vBase + 2 };

                vBase += 4;
            }

            // 3) Replace faces so the base class triangulates them into the IBO
            this.faces = newFaces;

            return out;
        }

        @Override
        protected VertexLayout.EdgeABLayout createLayout() {
            return VertexLayout.EdgeABLayout.INSTANCE;
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

    private static float[] buildExpandedVertexData(
            Vector3D[] verts, int[][] faces) {
        return buildExpandedVertexData(verts, extractEdges(faces));
    }

    private static float[] buildExpandedVertexData(
            Vector3D[] verts, ArrayList<int[]> edges) {
        final int vertsPerEdge = 4;
        final int floatsPerVert = 8;
        float[] out = new float[edges.size() * vertsPerEdge * floatsPerVert];
        int vFloat = 0;
        for (int[] edge : edges) {
            Vector3D a = verts[edge[0]];
            Vector3D b = verts[edge[1]];
            vFloat = putEdgeVert(out, vFloat, a, b, 0f, -1f);
            vFloat = putEdgeVert(out, vFloat, a, b, 0f, +1f);
            vFloat = putEdgeVert(out, vFloat, a, b, 1f, -1f);
            vFloat = putEdgeVert(out, vFloat, a, b, 1f, +1f);
        }
        return out;
    }

    private static ArrayList<int[]> extractEdges(int[][] faces) {
        ArrayList<int[]> edges = new ArrayList<>();
        for (int[] face : faces) {
            int n = face.length;
            for (int k = 0; k < n; ++k) {
                int i = face[k];
                int j = face[(k + 1) % n];
                if (i == j) {
                    continue;
                }
                edges.add(new int[]{Math.min(i, j), Math.max(i, j)});
            }
        }
        return edges;
    }

    private static int putEdgeVert(float[] dst, int off,
                                   Vector3D a, Vector3D b,
                                   float end, float side) {
        dst[off++] = (float) a.x;
        dst[off++] = (float) a.y;
        dst[off++] = (float) a.z;
        dst[off++] = (float) b.x;
        dst[off++] = (float) b.y;
        dst[off++] = (float) b.z;
        dst[off++] = end;
        dst[off++] = side;
        return off;
    }
}
