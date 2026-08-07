package com.example.game3d_opengl.rendering.batching;

public final class SpikeCanonicalGeometryBuilder {
    public static final int FILL_FACE_COUNT = 4;
    public static final int FILL_VERTS_PER_FACE = 3;
    public static final int FILL_VERTEX_COUNT = FILL_FACE_COUNT * FILL_VERTS_PER_FACE;
    public static final int FILL_STRIDE_FLOATS = 13;

    private static final float[][] CANONICAL_W = new float[][]{
            {1f, 0f, 0f, 0f},
            {0f, 1f, 0f, 0f},
            {0f, 0f, 1f, 0f},
            {0f, 0f, 0f, 1f},
            {0f, 0f, 0f, 0f}
    };
    private static final float[] CANONICAL_T = new float[]{0f, 0f, 0f, 0f, 1f};
    private static final int[][] FACE_CANON = new int[][]{
            {0, 1, 4},
            {1, 2, 4},
            {2, 3, 4},
            {3, 0, 4}
    };

    private SpikeCanonicalGeometryBuilder() {}

    public static float[] buildFillVertexData() {
        float[] data = new float[FILL_VERTEX_COUNT * FILL_STRIDE_FLOATS];

        int w = 0;
        for (int[] face : FACE_CANON) {
            int iBaseA = face[0];
            int iBaseB = face[1];
            for (int k = 0; k < FILL_VERTS_PER_FACE; k++) {
                int ci = face[k];
                float[] vertexWeights = CANONICAL_W[ci];
                data[w++] = vertexWeights[0];
                data[w++] = vertexWeights[1];
                data[w++] = vertexWeights[2];
                data[w++] = vertexWeights[3];
                data[w++] = CANONICAL_T[ci];
                data[w++] = CANONICAL_W[iBaseA][0];
                data[w++] = CANONICAL_W[iBaseA][1];
                data[w++] = CANONICAL_W[iBaseA][2];
                data[w++] = CANONICAL_W[iBaseA][3];
                data[w++] = CANONICAL_W[iBaseB][0];
                data[w++] = CANONICAL_W[iBaseB][1];
                data[w++] = CANONICAL_W[iBaseB][2];
                data[w++] = CANONICAL_W[iBaseB][3];
            }
        }
        return data;
    }

    public static int[][] buildFillFaces() {
        return new int[][]{
                {0, 1, 2},
                {3, 4, 5},
                {6, 7, 8},
                {9, 10, 11}
        };
    }

    public static StaticGeometrySource buildFillGeometry() {
        return new StaticGeometrySource(
                buildFillVertexData(),
                buildFillFaces(),
                FILL_STRIDE_FLOATS * 4
        );
    }
}
