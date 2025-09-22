package com.example.game3d_opengl.rendering.util3d;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MeshHelpers {

    private static final float EPS = 1e-6f;

    /**
     * Triangulates a single planar polygon face (given as vertex indices into verts)
     * using ear clipping after projecting to the dominant 2D plane.
     * Returns short indices into the original vertex array, CCW winding.
     */
    public static short[] triangulateFaceEarClipping(int[] face, Vector3D[] verts) {
        int n = face.length;
        if (n == 3) {
            return new short[]{(short) face[0], (short) face[1], (short) face[2]};
        }

        // Build 3D points of the face
        Vector3D[] poly3 = new Vector3D[n];
        for (int i = 0; i < n; i++) poly3[i] = verts[face[i]];

        // Compute face normal (Newell's method)
        float[] normal = new float[]{0f, 0f, 0f};
        for (int i = 0; i < n; i++) {
            Vector3D a = poly3[i];
            Vector3D b = poly3[(i + 1) % n];
            normal[0] += (a.y - b.y) * (a.z + b.z);
            normal[1] += (a.z - b.z) * (a.x + b.x);
            normal[2] += (a.x - b.x) * (a.y + b.y);
        }
        // Select dominant axis to drop
        int dropAxis = 0; // 0->x, 1->y, 2->z
        float ax = Math.abs(normal[0]);
        float ay = Math.abs(normal[1]);
        float az = Math.abs(normal[2]);
        if (ay > ax && ay >= az) dropAxis = 1;
        else if (az > ax && az > ay) dropAxis = 2;

        // Project to 2D by dropping dominant axis
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            Vector3D p = poly3[i];
            switch (dropAxis) {
                case 0:
                    xs[i] = p.y;
                    ys[i] = p.z;
                    break; // drop X -> use (y,z)
                case 1:
                    xs[i] = p.x;
                    ys[i] = p.z;
                    break; // drop Y -> use (x,z)
                default:
                    xs[i] = p.x;
                    ys[i] = p.y;
                    break; // drop Z -> use (x,y)
            }
        }

        // Ensure CCW orientation for ear clipping
        float area2 = polygonArea2D(xs, ys);
        int[] order;
        order = new int[n];
        if (area2 < 0) {
            for (int i = 0; i < n; i++) order[i] = n - 1 - i; // reverse
        } else {
            for (int i = 0; i < n; i++) order[i] = i;
        }

        List<Integer> poly = new ArrayList<>(n);
        for (int i = 0; i < n; i++) poly.add(order[i]);

        short[] out = new short[(n - 2) * 3];
        int w = 0;
        int guard = 0; // prevent infinite loops on degenerate faces
        while (poly.size() > 3 && guard < 10000) {
            boolean cut = false;
            for (int i = 0; i < poly.size(); i++) {
                int i0 = poly.get((i - 1 + poly.size()) % poly.size());
                int i1 = poly.get(i);
                int i2 = poly.get((i + 1) % poly.size());

                if (!isConvex(i0, i1, i2, xs, ys)) continue;
                if (containsPoint(i0, i1, i2, xs, ys, poly)) continue; // ear must be empty

                // Emit triangle in CCW order
                out[w++] = (short) face[i0];
                out[w++] = (short) face[i1];
                out[w++] = (short) face[i2];

                poly.remove(i); // clip ear (remove i1)
                cut = true;
                break;
            }
            if (!cut) {
                // Fallback: if we can't find an ear (degeneracy), do a fan from the first
                // remaining vertex to ensure progress
                if (poly.size() >= 3) {
                    int base = poly.get(0);
                    for (int k = 1; k < poly.size() - 1; k++) {
                        int b = poly.get(k);
                        int c = poly.get(k + 1);
                        out[w++] = (short) face[base];
                        out[w++] = (short) face[b];
                        out[w++] = (short) face[c];
                    }
                    poly.clear();
                }
            }
            guard++;
        }
        if (poly.size() == 3) {
            int a = poly.get(0), b = poly.get(1), c = poly.get(2);
            out[w++] = (short) face[a];
            out[w++] = (short) face[b];
            out[w++] = (short) face[c];
        }
        if (w != out.length) {
            // In rare degeneracies, we may have written fewer triangles; truncate if needed
            out = Arrays.copyOf(out, w);
        }
        return out;
    }

    public static float polygonArea2D(float[] xs, float[] ys) {
        float a = 0f;
        for (int i = 0; i < xs.length; i++) {
            int j = (i + 1) % xs.length;
            a += xs[i] * ys[j] - xs[j] * ys[i];
        }
        return 0.5f * a;
    }

    public static boolean isConvex(int i0, int i1, int i2, float[] xs, float[] ys) {
        float ax = xs[i1] - xs[i0];
        float ay = ys[i1] - ys[i0];
        float bx = xs[i2] - xs[i1];
        float by = ys[i2] - ys[i1];
        float cross = ax * by - ay * bx; // z-component (since in 2D)
        return cross > EPS; // strictly CCW
    }

    public static boolean containsPoint(int i0, int i1, int i2, float[] xs, float[] ys, List<Integer> poly) {
        float ax = xs[i0], ay = ys[i0];
        float bx = xs[i1], by = ys[i1];
        float cx = xs[i2], cy = ys[i2];
        for (int idx : poly) {
            if (idx == i0 || idx == i1 || idx == i2) continue;
            float px = xs[idx], py = ys[idx];
            if (pointInTriangle(px, py, ax, ay, bx, by, cx, cy)) return true;
        }
        return false;
    }

    public static boolean pointInTriangle(float px, float py,
                                           float ax, float ay,
                                           float bx, float by,
                                           float cx, float cy) {
        // Barycentric technique
        float v0x = cx - ax, v0y = cy - ay;
        float v1x = bx - ax, v1y = by - ay;
        float v2x = px - ax, v2y = py - ay;

        float dot00 = v0x * v0x + v0y * v0y;
        float dot01 = v0x * v1x + v0y * v1y;
        float dot02 = v0x * v2x + v0y * v2y;
        float dot11 = v1x * v1x + v1y * v1y;
        float dot12 = v1x * v2x + v1y * v2y;

        float invDen = dot00 * dot11 - dot01 * dot01;
        if (Math.abs(invDen) < EPS) return false; // degenerate
        invDen = 1.0f / invDen;
        float u = (dot11 * dot02 - dot01 * dot12) * invDen;
        float v = (dot00 * dot12 - dot01 * dot02) * invDen;
        // Strict interior check avoids stealing adjacent vertices
        return u > EPS && v > EPS && (u + v) < 1.0f - EPS;
    }
}
