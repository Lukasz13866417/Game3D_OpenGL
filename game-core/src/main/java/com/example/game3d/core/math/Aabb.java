package com.example.game3d.core.math;

public strictfp final class Aabb {
    public final Vec3 min;
    public final Vec3 max;

    public Aabb(Vec3 min, Vec3 max) {
        if (min.x > max.x || min.y > max.y || min.z > max.z) {
            throw new IllegalArgumentException("Invalid AABB");
        }
        this.min = min;
        this.max = max;
    }

    public boolean intersects(Aabb other) {
        return min.x <= other.max.x && max.x >= other.min.x
                && min.y <= other.max.y && max.y >= other.min.y
                && min.z <= other.max.z && max.z >= other.min.z;
    }

    public boolean contains(Aabb other) {
        return min.x <= other.min.x && max.x >= other.max.x
                && min.y <= other.min.y && max.y >= other.max.y
                && min.z <= other.min.z && max.z >= other.max.z;
    }

    public Aabb expanded(double amount) {
        Vec3 delta = new Vec3(amount, amount, amount);
        return new Aabb(min.subtract(delta), max.add(delta));
    }

    public Aabb union(Aabb other) {
        return new Aabb(
                new Vec3(Math.min(min.x, other.min.x), Math.min(min.y, other.min.y),
                        Math.min(min.z, other.min.z)),
                new Vec3(Math.max(max.x, other.max.x), Math.max(max.y, other.max.y),
                        Math.max(max.z, other.max.z)));
    }

    public static Aabb around(Vec3 center, double xRadius, double yRadius, double zRadius) {
        return new Aabb(
                new Vec3(center.x - xRadius, center.y - yRadius, center.z - zRadius),
                new Vec3(center.x + xRadius, center.y + yRadius, center.z + zRadius));
    }
}
