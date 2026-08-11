package com.example.game3d_opengl.rendering.batching;

import static com.example.game3d_opengl.rendering.util3d.RenderingUtils.triangulateFaceEarClipping;

import com.example.game3d_opengl.rendering.RenderConfig;
import com.example.game3d_opengl.rendering.util3d.PreparedModelData;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public final class PositionNormalGeometryBuilder {
    private PositionNormalGeometryBuilder() {}

    public static StaticGeometrySource build(PreparedModelData model) {
        return build(model.verts(), model.faces(), model.hasNormals() ? model.normals() : null);
    }

    public static StaticGeometrySource buildSmooth(PreparedModelData model) {
        return buildSmooth(model.verts(), model.faces(), model.hasNormals() ? model.normals() : null);
    }

    public static StaticGeometrySource build(Vector3D[] verts, int[][] faces, Vector3D[] suppliedNormals) {
        if (RenderConfig.FLAT_SHADING) {
            return buildFlat(verts, faces);
        }
        return buildSmooth(verts, faces, suppliedNormals);
    }

    private static StaticGeometrySource buildFlat(Vector3D[] verts, int[][] faces) {
        int totalTris = 0;
        for (int[] face : faces) {
            if (face != null && face.length >= 3) {
                totalTris += triangulateFaceEarClipping(face, verts).length / 3;
            }
        }
        int totalVerts = totalTris * 3;
        float[] out = new float[totalVerts * 6];
        int[][] newFaces = new int[totalTris][3];
        int vi = 0;
        int fi = 0;
        for (int[] face : faces) {
            if (face == null || face.length < 3) {
                continue;
            }
            short[] tris = triangulateFaceEarClipping(face, verts);
            for (int i = 0; i < tris.length; i += 3) {
                Vector3D a = verts[tris[i]];
                Vector3D b = verts[tris[i + 1]];
                Vector3D c = verts[tris[i + 2]];
                float e1x = b.x - a.x;
                float e1y = b.y - a.y;
                float e1z = b.z - a.z;
                float e2x = c.x - a.x;
                float e2y = c.y - a.y;
                float e2z = c.z - a.z;
                float nx = e1y * e2z - e1z * e2y;
                float ny = e1z * e2x - e1x * e2z;
                float nz = e1x * e2y - e1y * e2x;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 1e-8f) {
                    nx /= len;
                    ny /= len;
                    nz /= len;
                }

                int o = vi * 6;
                o = putVertex(out, o, a, nx, ny, nz);
                o = putVertex(out, o, b, nx, ny, nz);
                putVertex(out, o, c, nx, ny, nz);
                newFaces[fi++] = new int[]{vi, vi + 1, vi + 2};
                vi += 3;
            }
        }
        return new StaticGeometrySource(out, newFaces, 6 * 4);
    }

    public static StaticGeometrySource buildSmooth(Vector3D[] verts, int[][] faces, Vector3D[] suppliedNormals) {
        final int n = verts.length;
        float[] norms;
        if (suppliedNormals != null) {
            norms = new float[n * 3];
            for (int i = 0; i < n; i++) {
                Vector3D sn = suppliedNormals[i];
                norms[i * 3] = sn.x;
                norms[i * 3 + 1] = sn.y;
                norms[i * 3 + 2] = sn.z;
            }
        } else {
            norms = computePerVertexNormals(verts, faces);
        }
        float[] out = new float[n * 6];
        for (int i = 0; i < n; ++i) {
            Vector3D v = verts[i];
            int o = i * 6;
            out[o] = v.x;
            out[o + 1] = v.y;
            out[o + 2] = v.z;
            out[o + 3] = norms[i * 3];
            out[o + 4] = norms[i * 3 + 1];
            out[o + 5] = norms[i * 3 + 2];
        }
        return new StaticGeometrySource(out, faces, 6 * 4);
    }

    private static float[] computePerVertexNormals(Vector3D[] verts, int[][] faces) {
        float[] normals = new float[verts.length * 3];
        for (int[] face : faces) {
            if (face == null || face.length < 3) {
                continue;
            }
            short[] tris = triangulateFaceEarClipping(face, verts);
            for (int i = 0; i < tris.length; i += 3) {
                Vector3D a = verts[tris[i]];
                Vector3D b = verts[tris[i + 1]];
                Vector3D c = verts[tris[i + 2]];
                float e1x = b.x - a.x;
                float e1y = b.y - a.y;
                float e1z = b.z - a.z;
                float e2x = c.x - a.x;
                float e2y = c.y - a.y;
                float e2z = c.z - a.z;
                float nx = e1y * e2z - e1z * e2y;
                float ny = e1z * e2x - e1x * e2z;
                float nz = e1x * e2y - e1y * e2x;
                addNormal(normals, tris[i], nx, ny, nz);
                addNormal(normals, tris[i + 1], nx, ny, nz);
                addNormal(normals, tris[i + 2], nx, ny, nz);
            }
        }
        for (int i = 0; i < verts.length; ++i) {
            float x = normals[i * 3];
            float y = normals[i * 3 + 1];
            float z = normals[i * 3 + 2];
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len > 1e-8f) {
                normals[i * 3] /= len;
                normals[i * 3 + 1] /= len;
                normals[i * 3 + 2] /= len;
            }
        }
        return normals;
    }

    private static void addNormal(float[] normals, int idx, float nx, float ny, float nz) {
        normals[idx * 3] += nx;
        normals[idx * 3 + 1] += ny;
        normals[idx * 3 + 2] += nz;
    }

    private static int putVertex(float[] out, int offset, Vector3D v, float nx, float ny, float nz) {
        out[offset++] = v.x;
        out[offset++] = v.y;
        out[offset++] = v.z;
        out[offset++] = nx;
        out[offset++] = ny;
        out[offset++] = nz;
        return offset;
    }
}
