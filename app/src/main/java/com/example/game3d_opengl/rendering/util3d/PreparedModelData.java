package com.example.game3d_opengl.rendering.util3d;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class PreparedModelData {
    private final Vector3D[] verts;
    private final int[][] faces;
    private final Vector3D[] normals;

    public PreparedModelData(Vector3D[] verts, int[][] faces, Vector3D[] normals) {
        this.verts = verts;
        this.faces = faces;
        this.normals = normals;
    }

    public Vector3D[] verts() {
        return verts;
    }

    public int[][] faces() {
        return faces;
    }

    public Vector3D[] normals() {
        return normals;
    }

    public boolean hasNormals() {
        return normals != null;
    }
}
