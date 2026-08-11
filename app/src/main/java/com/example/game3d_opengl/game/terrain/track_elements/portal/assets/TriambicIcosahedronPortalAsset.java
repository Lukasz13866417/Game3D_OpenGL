package com.example.game3d_opengl.game.terrain.track_elements.portal.assets;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Small triambic icosahedron represented as a convex triakis icosahedron.
 * Built as flat-shaded triangles with per-face group ids.
 */
public final class TriambicIcosahedronPortalAsset implements PortalAsset {
    private static final PortalAssetData DATA = build();

    @Override
    public PortalAssetData buildMeshData() {
        return DATA;
    }

    private static PortalAssetData build() {
        final float PHI = (1f + (float) Math.sqrt(5.0)) / 2f;

        float[][] icoRaw = {
                { 0,  1,  PHI}, { 0, -1,  PHI}, { 0,  1, -PHI}, { 0, -1, -PHI},
                { 1,  PHI,  0}, {-1,  PHI,  0}, { 1, -PHI,  0}, {-1, -PHI,  0},
                { PHI,  0,  1}, {-PHI,  0,  1}, { PHI,  0, -1}, {-PHI,  0, -1}
        };
        float[][] icoVerts = new float[12][3];
        for (int i = 0; i < 12; i++) {
            float len = (float) Math.sqrt(
                    icoRaw[i][0] * icoRaw[i][0] +
                            icoRaw[i][1] * icoRaw[i][1] +
                            icoRaw[i][2] * icoRaw[i][2]);
            icoVerts[i][0] = icoRaw[i][0] / len;
            icoVerts[i][1] = icoRaw[i][1] / len;
            icoVerts[i][2] = icoRaw[i][2] / len;
        }

        int[][] icoFaces = {
                {0,1,8},{0,8,4},{0,4,5},{0,5,9},{0,9,1},
                {1,6,8},{8,6,10},{8,10,4},{4,10,2},{4,2,5},
                {5,2,11},{5,11,9},{9,11,7},{9,7,1},{1,7,6},
                {3,6,7},{3,7,11},{3,11,2},{3,2,10},{3,10,6}
        };

        float stellationHeight = (PHI * PHI) / (float) Math.sqrt(3.0);

        List<float[]> allVerts = new ArrayList<>();
        for (float[] v : icoVerts) {
            allVerts.add(v);
        }

        float maxR = 0f;
        for (int fi = 0; fi < 20; fi++) {
            int[] f = icoFaces[fi];
            float cx = (icoVerts[f[0]][0] + icoVerts[f[1]][0] + icoVerts[f[2]][0]) / 3f;
            float cy = (icoVerts[f[0]][1] + icoVerts[f[1]][1] + icoVerts[f[2]][1]) / 3f;
            float cz = (icoVerts[f[0]][2] + icoVerts[f[1]][2] + icoVerts[f[2]][2]) / 3f;
            float cLen = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (cLen < 1e-6f) cLen = 1f;
            float nx = cx / cLen, ny = cy / cLen, nz = cz / cLen;
            float sx = cx + nx * stellationHeight;
            float sy = cy + ny * stellationHeight;
            float sz = cz + nz * stellationHeight;
            float r = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (r > maxR) maxR = r;
            allVerts.add(new float[]{sx, sy, sz});
        }

        for (float[] v : icoVerts) {
            float r = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            if (r > maxR) maxR = r;
        }
        for (float[] v : allVerts) {
            v[0] /= maxR;
            v[1] /= maxR;
            v[2] /= maxR;
        }

        int[][] triFaces = new int[60][3];
        int[] faceGroupIds = new int[60];
        for (int fi = 0; fi < 20; fi++) {
            int a = icoFaces[fi][0];
            int b = icoFaces[fi][1];
            int c = icoFaces[fi][2];
            int s = 12 + fi;
            triFaces[fi * 3] = new int[]{a, b, s};
            triFaces[fi * 3 + 1] = new int[]{b, c, s};
            triFaces[fi * 3 + 2] = new int[]{c, a, s};
            int group = fi % 2;
            faceGroupIds[fi * 3] = group;
            faceGroupIds[fi * 3 + 1] = group;
            faceGroupIds[fi * 3 + 2] = group;
        }

        int nTris = 60;
        Vector3D[] outVerts = new Vector3D[nTris * 3];
        float[] outNormals = new float[nTris * 3 * 3];
        float[] outFaceGroups = new float[nTris * 3];
        int[][] outFaces = new int[nTris][3];

        for (int ti = 0; ti < nTris; ti++) {
            float[] va = allVerts.get(triFaces[ti][0]);
            float[] vb = allVerts.get(triFaces[ti][1]);
            float[] vc = allVerts.get(triFaces[ti][2]);

            float e1x = vb[0] - va[0], e1y = vb[1] - va[1], e1z = vb[2] - va[2];
            float e2x = vc[0] - va[0], e2y = vc[1] - va[1], e2z = vc[2] - va[2];
            float nx = e1y * e2z - e1z * e2y;
            float ny = e1z * e2x - e1x * e2z;
            float nz = e1x * e2y - e1y * e2x;
            float nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nLen < 1e-8f) nLen = 1f;
            nx /= nLen; ny /= nLen; nz /= nLen;

            float cx = (va[0] + vb[0] + vc[0]) / 3f;
            float cy = (va[1] + vb[1] + vc[1]) / 3f;
            float cz = (va[2] + vb[2] + vc[2]) / 3f;
            if (nx * cx + ny * cy + nz * cz < 0f) {
                nx = -nx; ny = -ny; nz = -nz;
            }

            int base = ti * 3;
            outVerts[base] = new Vector3D(va[0], va[1], va[2]);
            outVerts[base + 1] = new Vector3D(vb[0], vb[1], vb[2]);
            outVerts[base + 2] = new Vector3D(vc[0], vc[1], vc[2]);

            for (int k = 0; k < 3; k++) {
                outNormals[(base + k) * 3] = nx;
                outNormals[(base + k) * 3 + 1] = ny;
                outNormals[(base + k) * 3 + 2] = nz;
                outFaceGroups[base + k] = faceGroupIds[ti];
            }

            outFaces[ti] = new int[]{base, base + 1, base + 2};
        }

        return new PortalAssetData(outVerts, outNormals, outFaceGroups, outFaces);
    }
}

