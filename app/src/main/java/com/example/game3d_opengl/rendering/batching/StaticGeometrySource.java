package com.example.game3d_opengl.rendering.batching;

public final class StaticGeometrySource {
    private final float[] vertexData;
    private final int[][] faces;
    private final int strideBytes;

    public StaticGeometrySource(float[] vertexData, int[][] faces, int strideBytes) {
        if (vertexData == null) {
            throw new IllegalArgumentException("vertexData == null");
        }
        if (faces == null) {
            throw new IllegalArgumentException("faces == null");
        }
        if (strideBytes <= 0 || (strideBytes & 3) != 0) {
            throw new IllegalArgumentException("strideBytes must be a positive multiple of 4");
        }
        this.vertexData = vertexData;
        this.faces = faces;
        this.strideBytes = strideBytes;
    }

    public float[] vertexData() {
        return vertexData;
    }

    public int[][] faces() {
        return faces;
    }

    public int strideBytes() {
        return strideBytes;
    }
}
