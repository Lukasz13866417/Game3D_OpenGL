package com.example.game3d_opengl.game.terrain.track_elements.portal.assets;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class PortalAssetData {
    public final Vector3D[] verts;
    public final float[] normals;
    public final float[] faceGroups;
    public final int[][] faces;

    public PortalAssetData(Vector3D[] verts, float[] normals, float[] faceGroups, int[][] faces) {
        this.verts = verts;
        this.normals = normals;
        this.faceGroups = faceGroups;
        this.faces = faces;
    }
}

