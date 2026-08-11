package com.example.game3d_opengl.game.terrain.track_elements.portal.assets;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Torus asset where each torus face carries an outward spike.
 * For every quad face on the torus:
 * - base is that quad (two triangles),
 * - spike apex moves along that face's outward normal,
 * - spike sides are four triangles around the base.
 */
public final class SpikedTorusPortalAsset implements PortalAsset {
    private static final int MAJOR_SEGMENTS = 48;
    private static final int MINOR_SEGMENTS = 24;
    private static final float MAJOR_RADIUS = 0.72f;
    private static final float TUBE_RADIUS = 0.18f;
    private static final float SPIKE_HEIGHT = 0.03f;

    private static final PortalAssetData DATA = build();

    @Override
    public PortalAssetData buildMeshData() {
        return DATA;
    }

    private static PortalAssetData build() {
        Vector3D[][] ring = new Vector3D[MAJOR_SEGMENTS][MINOR_SEGMENTS];
        for (int i = 0; i < MAJOR_SEGMENTS; i++) {
            float u = (float) (2.0 * Math.PI * i / MAJOR_SEGMENTS);
            for (int j = 0; j < MINOR_SEGMENTS; j++) {
                float v = (float) (2.0 * Math.PI * j / MINOR_SEGMENTS);
                ring[i][j] = torusPoint(u, v);
            }
        }

        final int quads = MAJOR_SEGMENTS * MINOR_SEGMENTS;
        final int trisPerQuad = 6; // base(2) + spike sides(4)
        final int totalTris = quads * trisPerQuad;
        final int totalVerts = totalTris * 3;

        Vector3D[] outVerts = new Vector3D[totalVerts];
        float[] outNormals = new float[totalVerts * 3];
        float[] outGroups = new float[totalVerts];
        int[][] outFaces = new int[totalTris][3];

        int tri = 0;
        for (int i = 0; i < MAJOR_SEGMENTS; i++) {
            int i2 = (i + 1) % MAJOR_SEGMENTS;
            for (int j = 0; j < MINOR_SEGMENTS; j++) {
                int j2 = (j + 1) % MINOR_SEGMENTS;

                Vector3D v0 = ring[i][j];
                Vector3D v1 = ring[i2][j];
                Vector3D v2 = ring[i2][j2];
                Vector3D v3 = ring[i][j2];

                Vector3D faceCenter = v0.add(v1).add(v2).add(v3).div(4f);
                Vector3D ringCenter = ringCenterFor(faceCenter);
                Vector3D outward = safeNormalize(faceCenter.sub(ringCenter), new Vector3D(0f, 1f, 0f));
                Vector3D apex = faceCenter.add(outward.mult(SPIKE_HEIGHT));

                float group = ((i + j) & 1) == 0 ? 0f : 1f;

                tri = appendTriangle(outVerts, outNormals, outGroups, outFaces, tri, v0, v1, v2, group, outward);
                tri = appendTriangle(outVerts, outNormals, outGroups, outFaces, tri, v0, v2, v3, group, outward);

                tri = appendTriangle(outVerts, outNormals, outGroups, outFaces, tri, v0, v1, apex, group, outward);
                tri = appendTriangle(outVerts, outNormals, outGroups, outFaces, tri, v1, v2, apex, group, outward);
                tri = appendTriangle(outVerts, outNormals, outGroups, outFaces, tri, v2, v3, apex, group, outward);
                tri = appendTriangle(outVerts, outNormals, outGroups, outFaces, tri, v3, v0, apex, group, outward);
            }
        }

        normalizeToUnitRadius(outVerts);
        return new PortalAssetData(outVerts, outNormals, outGroups, outFaces);
    }

    private static Vector3D torusPoint(float u, float v) {
        float cu = (float) Math.cos(u), su = (float) Math.sin(u);
        float cv = (float) Math.cos(v), sv = (float) Math.sin(v);

        float cx = MAJOR_RADIUS * cu;
        float cz = MAJOR_RADIUS * su;

        float dx = cu * cv;
        float dy = sv;
        float dz = su * cv;

        return new Vector3D(
                cx + TUBE_RADIUS * dx,
                TUBE_RADIUS * dy,
                cz + TUBE_RADIUS * dz
        );
    }

    private static Vector3D ringCenterFor(Vector3D p) {
        float lenXZ = (float) Math.sqrt(p.x * p.x + p.z * p.z);
        if (lenXZ < 1e-6f) {
            return new Vector3D(MAJOR_RADIUS, 0f, 0f);
        }
        float s = MAJOR_RADIUS / lenXZ;
        return new Vector3D(p.x * s, 0f, p.z * s);
    }

    private static Vector3D safeNormalize(Vector3D v, Vector3D fallback) {
        double sql = v.sqlen();
        if (sql < 1e-8) {
            return fallback;
        }
        return v.div((float) Math.sqrt(sql));
    }

    private static int appendTriangle(
            Vector3D[] outVerts,
            float[] outNormals,
            float[] outGroups,
            int[][] outFaces,
            int triIndex,
            Vector3D p0, Vector3D p1, Vector3D p2,
            float group,
            Vector3D orientationRef
    ) {
        float e1x = p1.x - p0.x, e1y = p1.y - p0.y, e1z = p1.z - p0.z;
        float e2x = p2.x - p0.x, e2y = p2.y - p0.y, e2z = p2.z - p0.z;
        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;
        float nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nLen < 1e-8f) {
            nx = orientationRef.x;
            ny = orientationRef.y;
            nz = orientationRef.z;
            nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nLen < 1e-8f) {
                nx = 0f; ny = 1f; nz = 0f;
                nLen = 1f;
            }
        }
        nx /= nLen;
        ny /= nLen;
        nz /= nLen;

        if (nx * orientationRef.x + ny * orientationRef.y + nz * orientationRef.z < 0f) {
            Vector3D tmp = p1;
            p1 = p2;
            p2 = tmp;
            nx = -nx;
            ny = -ny;
            nz = -nz;
        }

        int base = triIndex * 3;
        outVerts[base] = p0;
        outVerts[base + 1] = p1;
        outVerts[base + 2] = p2;
        for (int k = 0; k < 3; k++) {
            int no = (base + k) * 3;
            outNormals[no] = nx;
            outNormals[no + 1] = ny;
            outNormals[no + 2] = nz;
            outGroups[base + k] = group;
        }
        outFaces[triIndex] = new int[]{base, base + 1, base + 2};
        return triIndex + 1;
    }

    private static void normalizeToUnitRadius(Vector3D[] verts) {
        float maxR = 1e-6f;
        for (Vector3D v : verts) {
            float r = (float) Math.sqrt(v.sqlen());
            if (r > maxR) {
                maxR = r;
            }
        }
        for (int i = 0; i < verts.length; i++) {
            verts[i] = verts[i].div(maxR);
        }
    }
}

