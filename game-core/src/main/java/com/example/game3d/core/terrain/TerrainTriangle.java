package com.example.game3d.core.terrain;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;

public strictfp final class TerrainTriangle {
    public final long id;
    public final Vec3 a;
    public final Vec3 b;
    public final Vec3 c;
    public final Vec3 normal;
    public final Aabb bounds;
    public final long ownerSegmentId;
    public final SurfaceProperties surface;
    /** Compatibility facade for old scenario assertions. New code should use {@link #surface}. */
    public final SurfaceMaterial material;

    public TerrainTriangle(long id, Vec3 a, Vec3 b, Vec3 c, SurfaceMaterial material) {
        this(id, -1L, a, b, c,
                material == null ? SurfaceProperties.NORMAL : material.properties(),
                material == null ? SurfaceMaterial.NORMAL : material);
    }

    public TerrainTriangle(
            long id, long ownerSegmentId, Vec3 a, Vec3 b, Vec3 c,
            SurfaceProperties surface) {
        this(id, ownerSegmentId, a, b, c, surface, legacyMaterial(surface));
    }

    private TerrainTriangle(
            long id, long ownerSegmentId, Vec3 a, Vec3 b, Vec3 c,
            SurfaceProperties surface, SurfaceMaterial material) {
        if (surface == null) {
            throw new IllegalArgumentException("surface == null");
        }
        this.id = id;
        this.ownerSegmentId = ownerSegmentId;
        this.a = a;
        this.b = b;
        this.c = c;
        Vec3 rawNormal = b.subtract(a).cross(c.subtract(a));
        if (rawNormal.lengthSquared() < 1.0e-16) {
            throw new IllegalArgumentException("Degenerate terrain triangle " + id);
        }
        this.normal = rawNormal.normalized();
        this.bounds = new Aabb(
                new Vec3(Math.min(a.x, Math.min(b.x, c.x)),
                        Math.min(a.y, Math.min(b.y, c.y)),
                        Math.min(a.z, Math.min(b.z, c.z))),
                new Vec3(Math.max(a.x, Math.max(b.x, c.x)),
                        Math.max(a.y, Math.max(b.y, c.y)),
                        Math.max(a.z, Math.max(b.z, c.z))));
        this.surface = surface;
        this.material = material;
    }

    public boolean containsProjectedPoint(Vec3 point, double tolerance) {
        Vec3 v0 = b.subtract(a);
        Vec3 v1 = c.subtract(a);
        Vec3 v2 = point.subtract(a);
        double d00 = v0.dot(v0);
        double d01 = v0.dot(v1);
        double d11 = v1.dot(v1);
        double d20 = v2.dot(v0);
        double d21 = v2.dot(v1);
        double denominator = d00 * d11 - d01 * d01;
        if (Math.abs(denominator) < 1.0e-16) {
            return false;
        }
        double v = (d11 * d20 - d01 * d21) / denominator;
        double w = (d00 * d21 - d01 * d20) / denominator;
        double u = 1.0 - v - w;
        return u >= -tolerance && v >= -tolerance && w >= -tolerance;
    }

    public long collisionFingerprint() {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, id);
        hash = mix(hash, ownerSegmentId);
        hash = mixVec(hash, a);
        hash = mixVec(hash, b);
        hash = mixVec(hash, c);
        return mix(hash, surface.deterministicFingerprint());
    }

    private static SurfaceMaterial legacyMaterial(SurfaceProperties surface) {
        return surface.kind == SurfaceProperties.Kind.NORMAL
                ? SurfaceMaterial.NORMAL : SurfaceMaterial.BOOST;
    }

    private static long mixVec(long hash, Vec3 value) {
        hash = mix(hash, Double.doubleToLongBits(value.x));
        hash = mix(hash, Double.doubleToLongBits(value.y));
        return mix(hash, Double.doubleToLongBits(value.z));
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001b3L;
    }
}
