package com.example.game3d_opengl.rendering.batching;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SpikeCanonicalGeometryBuilderTest {
    private static final float EPS = 1e-6f;

    @Test
    public void fill_geometry_uses_per_face_base_pairs_for_stable_normals() {
        StaticGeometrySource geometry = SpikeCanonicalGeometryBuilder.buildFillGeometry();
        float[] data = geometry.vertexData();

        assertEquals(
                SpikeCanonicalGeometryBuilder.FILL_STRIDE_FLOATS * 4,
                geometry.strideBytes()
        );
        assertEquals(
                SpikeCanonicalGeometryBuilder.FILL_VERTEX_COUNT * SpikeCanonicalGeometryBuilder.FILL_STRIDE_FLOATS,
                data.length
        );
        assertArrayEquals(new int[]{0, 1, 2}, geometry.faces()[0]);
        assertArrayEquals(new int[]{3, 4, 5}, geometry.faces()[1]);
        assertArrayEquals(new int[]{6, 7, 8}, geometry.faces()[2]);
        assertArrayEquals(new int[]{9, 10, 11}, geometry.faces()[3]);

        float[][] expectedWeights = new float[][]{
                {1f, 0f, 0f, 0f},
                {0f, 1f, 0f, 0f},
                {0f, 0f, 0f, 0f},
                {0f, 1f, 0f, 0f},
                {0f, 0f, 1f, 0f},
                {0f, 0f, 0f, 0f},
                {0f, 0f, 1f, 0f},
                {0f, 0f, 0f, 1f},
                {0f, 0f, 0f, 0f},
                {0f, 0f, 0f, 1f},
                {1f, 0f, 0f, 0f},
                {0f, 0f, 0f, 0f}
        };
        float[] expectedT = new float[]{0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f};
        float[][] expectedFaceBaseA = new float[][]{
                {1f, 0f, 0f, 0f},
                {1f, 0f, 0f, 0f},
                {1f, 0f, 0f, 0f},
                {0f, 1f, 0f, 0f},
                {0f, 1f, 0f, 0f},
                {0f, 1f, 0f, 0f},
                {0f, 0f, 1f, 0f},
                {0f, 0f, 1f, 0f},
                {0f, 0f, 1f, 0f},
                {0f, 0f, 0f, 1f},
                {0f, 0f, 0f, 1f},
                {0f, 0f, 0f, 1f}
        };
        float[][] expectedFaceBaseB = new float[][]{
                {0f, 1f, 0f, 0f},
                {0f, 1f, 0f, 0f},
                {0f, 1f, 0f, 0f},
                {0f, 0f, 1f, 0f},
                {0f, 0f, 1f, 0f},
                {0f, 0f, 1f, 0f},
                {0f, 0f, 0f, 1f},
                {0f, 0f, 0f, 1f},
                {0f, 0f, 0f, 1f},
                {1f, 0f, 0f, 0f},
                {1f, 0f, 0f, 0f},
                {1f, 0f, 0f, 0f}
        };

        for (int vertexIndex = 0; vertexIndex < SpikeCanonicalGeometryBuilder.FILL_VERTEX_COUNT; vertexIndex++) {
            assertArrayEquals(expectedWeights[vertexIndex], readAttribute(data, vertexIndex, 0, 4), EPS);
            assertEquals(expectedT[vertexIndex], readAttribute(data, vertexIndex, 4, 1)[0], EPS);
            assertArrayEquals(expectedFaceBaseA[vertexIndex], readAttribute(data, vertexIndex, 5, 4), EPS);
            assertArrayEquals(expectedFaceBaseB[vertexIndex], readAttribute(data, vertexIndex, 9, 4), EPS);
        }
    }

    private static float[] readAttribute(float[] data, int vertexIndex, int offset, int length) {
        float[] attribute = new float[length];
        int base = vertexIndex * SpikeCanonicalGeometryBuilder.FILL_STRIDE_FLOATS + offset;
        System.arraycopy(data, base, attribute, 0, length);
        return attribute;
    }
}
