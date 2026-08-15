package com.example.game3d.core.terrain.addon;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;

/** Immutable resolved placement footprint for an addon. */
public final class AddonFootprint {
    public final Vec3 nearLeft;
    public final Vec3 nearRight;
    public final Vec3 farLeft;
    public final Vec3 farRight;
    public final Aabb bounds;

    private AddonFootprint(
            Vec3 nearLeft, Vec3 nearRight, Vec3 farLeft, Vec3 farRight,
            Aabb bounds) {
        this.nearLeft = nearLeft;
        this.nearRight = nearRight;
        this.farLeft = farLeft;
        this.farRight = farRight;
        this.bounds = bounds;
    }

    public static AddonFootprint quadrilateral(
            Vec3 nearLeft, Vec3 nearRight, Vec3 farLeft, Vec3 farRight) {
        requireFinite(nearLeft, "nearLeft");
        requireFinite(nearRight, "nearRight");
        requireFinite(farLeft, "farLeft");
        requireFinite(farRight, "farRight");
        Vec3 firstNormal = nearRight.subtract(nearLeft).cross(farRight.subtract(nearLeft));
        Vec3 secondNormal = farRight.subtract(nearLeft).cross(farLeft.subtract(nearLeft));
        if (firstNormal.lengthSquared() < 1.0e-16
                || secondNormal.lengthSquared() < 1.0e-16) {
            throw new IllegalArgumentException("Degenerate addon footprint");
        }
        return new AddonFootprint(nearLeft, nearRight, farLeft, farRight,
                boundsOf(nearLeft, nearRight, farLeft, farRight));
    }

    /** Creates a non-degenerate placement volume for point-like addons. */
    public static AddonFootprint around(
            Vec3 center, double halfX, double halfY, double halfZ) {
        requireFinite(center, "center");
        if (!(halfX > 0.0) || !(halfY > 0.0) || !(halfZ > 0.0)
                || !Double.isFinite(halfX) || !Double.isFinite(halfY)
                || !Double.isFinite(halfZ)) {
            throw new IllegalArgumentException("Footprint extents must be finite and positive");
        }
        Vec3 nearLeft = new Vec3(center.x - halfX, center.y, center.z + halfZ);
        Vec3 nearRight = new Vec3(center.x + halfX, center.y, center.z + halfZ);
        Vec3 farLeft = new Vec3(center.x - halfX, center.y, center.z - halfZ);
        Vec3 farRight = new Vec3(center.x + halfX, center.y, center.z - halfZ);
        return new AddonFootprint(nearLeft, nearRight, farLeft, farRight,
                Aabb.around(center, halfX, halfY, halfZ));
    }

    private static Aabb boundsOf(Vec3 a, Vec3 b, Vec3 c, Vec3 d) {
        double minX = Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x));
        double minY = Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y));
        double minZ = Math.min(Math.min(a.z, b.z), Math.min(c.z, d.z));
        double maxX = Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x));
        double maxY = Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y));
        double maxZ = Math.max(Math.max(a.z, b.z), Math.max(c.z, d.z));
        return new Aabb(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
    }

    static void requireFinite(Vec3 value, String name) {
        if (value == null || !Double.isFinite(value.x)
                || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
