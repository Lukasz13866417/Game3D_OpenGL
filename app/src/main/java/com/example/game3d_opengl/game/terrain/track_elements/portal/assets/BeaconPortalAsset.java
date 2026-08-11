package com.example.game3d_opengl.game.terrain.track_elements.portal.assets;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class BeaconPortalAsset implements PortalAsset {
    private static final float HALF_EXTENT = (float) (1.0 / Math.sqrt(3.0));
    private static final PortalAssetData DATA = build();

    @Override
    public PortalAssetData buildMeshData() {
        return DATA;
    }

    private static PortalAssetData build() {
        Vector3D[] corners = new Vector3D[]{
                v(-HALF_EXTENT, -HALF_EXTENT, -HALF_EXTENT),
                v(+HALF_EXTENT, -HALF_EXTENT, -HALF_EXTENT),
                v(+HALF_EXTENT, +HALF_EXTENT, -HALF_EXTENT),
                v(-HALF_EXTENT, +HALF_EXTENT, -HALF_EXTENT),
                v(-HALF_EXTENT, -HALF_EXTENT, +HALF_EXTENT),
                v(+HALF_EXTENT, -HALF_EXTENT, +HALF_EXTENT),
                v(+HALF_EXTENT, +HALF_EXTENT, +HALF_EXTENT),
                v(-HALF_EXTENT, +HALF_EXTENT, +HALF_EXTENT)
        };

        final int faceVertexCount = 24;
        Vector3D[] verts = new Vector3D[corners.length + faceVertexCount];
        float[] normals = new float[verts.length * 3];
        float[] faceGroups = new float[verts.length];
        System.arraycopy(corners, 0, verts, 0, corners.length);
        for (int i = 0; i < corners.length; ++i) {
            Vector3D n = corners[i].withLen(1f);
            int no = i * 3;
            normals[no] = n.x;
            normals[no + 1] = n.y;
            normals[no + 2] = n.z;
        }

        int[][] faces = new int[6][];
        int cursor = corners.length;
        cursor = appendFace(verts, normals, faceGroups, faces, 0, cursor,
                corners[4], corners[5], corners[6], corners[7], 0f, v(0f, 0f, 1f));
        cursor = appendFace(verts, normals, faceGroups, faces, 1, cursor,
                corners[1], corners[0], corners[3], corners[2], 1f, v(0f, 0f, -1f));
        cursor = appendFace(verts, normals, faceGroups, faces, 2, cursor,
                corners[0], corners[4], corners[7], corners[3], 0f, v(-1f, 0f, 0f));
        cursor = appendFace(verts, normals, faceGroups, faces, 3, cursor,
                corners[5], corners[1], corners[2], corners[6], 1f, v(1f, 0f, 0f));
        cursor = appendFace(verts, normals, faceGroups, faces, 4, cursor,
                corners[3], corners[7], corners[6], corners[2], 0f, v(0f, 1f, 0f));
        appendFace(verts, normals, faceGroups, faces, 5, cursor,
                corners[0], corners[1], corners[5], corners[4], 1f, v(0f, -1f, 0f));

        int[][] edges = new int[][]{
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        return new PortalAssetData(verts, normals, faceGroups, faces, edges);
    }

    private static int appendFace(
            Vector3D[] verts,
            float[] normals,
            float[] faceGroups,
            int[][] faces,
            int faceIndex,
            int cursor,
            Vector3D v0,
            Vector3D v1,
            Vector3D v2,
            Vector3D v3,
            float group,
            Vector3D normal
    ) {
        verts[cursor] = v0;
        verts[cursor + 1] = v1;
        verts[cursor + 2] = v2;
        verts[cursor + 3] = v3;
        for (int i = 0; i < 4; ++i) {
            int vi = cursor + i;
            int no = vi * 3;
            normals[no] = normal.x;
            normals[no + 1] = normal.y;
            normals[no + 2] = normal.z;
            faceGroups[vi] = group;
        }
        faces[faceIndex] = new int[]{cursor, cursor + 1, cursor + 2, cursor + 3};
        return cursor + 4;
    }

    private static Vector3D v(float x, float y, float z) {
        return new Vector3D(x, y, z);
    }
}
