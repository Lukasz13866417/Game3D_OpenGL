package com.example.game3d.core.math;

import java.util.Locale;

/** Immutable vector used by the platform-independent simulation. */
public strictfp final class Vec3 {
    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);
    public static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        if (!isFinite(x) || !isFinite(y) || !isFinite(z)) {
            throw new IllegalArgumentException("Vector components must be finite");
        }
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 subtract(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 multiply(double scalar) {
        return new Vec3(x * scalar, y * scalar, z * scalar);
    }

    public Vec3 divide(double scalar) {
        if (Math.abs(scalar) < 1.0e-15) {
            throw new IllegalArgumentException("Cannot divide vector by zero");
        }
        return multiply(1.0 / scalar);
    }

    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }

    public double lengthSquared() {
        return dot(this);
    }

    public double length() {
        return Math.sqrt(lengthSquared());
    }

    public Vec3 normalized() {
        double length = length();
        return length < 1.0e-12 ? ZERO : divide(length);
    }

    public Vec3 withY(double value) {
        return new Vec3(x, value, z);
    }

    public Vec3 horizontalNormalized() {
        double length = Math.sqrt(x * x + z * z);
        return length < 1.0e-12 ? ZERO : new Vec3(x / length, 0.0, z / length);
    }

    public static Vec3 lerp(Vec3 from, Vec3 to, double alpha) {
        return from.add(to.subtract(from).multiply(alpha));
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "(%.6f, %.6f, %.6f)", x, y, z);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vec3)) {
            return false;
        }
        Vec3 value = (Vec3) other;
        return x == value.x && y == value.y && z == value.z;
    }

    @Override
    public int hashCode() {
        long xBits = Double.doubleToLongBits(x == 0.0 ? 0.0 : x);
        long yBits = Double.doubleToLongBits(y == 0.0 ? 0.0 : y);
        long zBits = Double.doubleToLongBits(z == 0.0 ? 0.0 : z);
        int result = (int) (xBits ^ (xBits >>> 32));
        result = 31 * result + (int) (yBits ^ (yBits >>> 32));
        result = 31 * result + (int) (zBits ^ (zBits >>> 32));
        return result;
    }
}
