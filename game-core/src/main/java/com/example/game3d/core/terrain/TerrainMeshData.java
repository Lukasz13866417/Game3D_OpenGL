package com.example.game3d.core.terrain;

/**
 * Renderer-neutral triangle stream produced from the authoritative collision mesh. Vertices are
 * deliberately duplicated per triangle so no renderer can silently choose another quad diagonal.
 */
public final class TerrainMeshData {
    public final float[] positions;
    public final float[] normals;
    public final long[] triangleIds;

    private TerrainMeshData(float[] positions, float[] normals, long[] triangleIds) {
        this.positions = positions;
        this.normals = normals;
        this.triangleIds = triangleIds;
    }

    public static TerrainMeshData from(TerrainWorld world) {
        int triangleCount = world.triangles().size();
        float[] positions = new float[triangleCount * 9];
        float[] normals = new float[triangleCount * 9];
        long[] ids = new long[triangleCount];
        for (int i = 0; i < triangleCount; i++) {
            TerrainTriangle triangle = world.triangles().get(i);
            ids[i] = triangle.id;
            write(positions, i * 9, triangle.a.x, triangle.a.y, triangle.a.z);
            write(positions, i * 9 + 3, triangle.b.x, triangle.b.y, triangle.b.z);
            write(positions, i * 9 + 6, triangle.c.x, triangle.c.y, triangle.c.z);
            for (int vertex = 0; vertex < 3; vertex++) {
                write(normals, i * 9 + vertex * 3,
                        triangle.normal.x, triangle.normal.y, triangle.normal.z);
            }
        }
        return new TerrainMeshData(positions, normals, ids);
    }

    private static void write(float[] target, int offset, double x, double y, double z) {
        target[offset] = (float) x;
        target[offset + 1] = (float) y;
        target[offset + 2] = (float) z;
    }
}
