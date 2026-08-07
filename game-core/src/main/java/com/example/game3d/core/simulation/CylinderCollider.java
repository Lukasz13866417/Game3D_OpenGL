package com.example.game3d.core.simulation;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainTriangle;

import java.util.ArrayList;
import java.util.List;

/** Analytic finite-cylinder support mapping and terrain contact generation. */
public strictfp final class CylinderCollider {
    private CylinderCollider() {
    }

    public static Vec3 support(Vec3 center, Vec3 axis, double halfLength,
                               double radius, Vec3 direction) {
        Vec3 unitAxis = axis.normalized();
        double along = direction.dot(unitAxis);
        Vec3 radial = direction.subtract(unitAxis.multiply(along));
        Vec3 radialSupport = radial.lengthSquared() < 1.0e-16
                ? Vec3.ZERO : radial.normalized().multiply(radius);
        double axialSupport = along > 0.0 ? halfLength : along < 0.0 ? -halfLength : 0.0;
        return center.add(unitAxis.multiply(axialSupport)).add(radialSupport);
    }

    public static Aabb bounds(Vec3 center, Vec3 axis, double halfLength, double radius) {
        Vec3 unitAxis = axis.normalized();
        double ex = Math.abs(unitAxis.x) * halfLength
                + radius * Math.sqrt(Math.max(0.0, 1.0 - unitAxis.x * unitAxis.x));
        double ey = Math.abs(unitAxis.y) * halfLength
                + radius * Math.sqrt(Math.max(0.0, 1.0 - unitAxis.y * unitAxis.y));
        double ez = Math.abs(unitAxis.z) * halfLength
                + radius * Math.sqrt(Math.max(0.0, 1.0 - unitAxis.z * unitAxis.z));
        return Aabb.around(center, ex, ey, ez);
    }

    static ContactCandidate contact(Vec3 center, Vec3 axis, PhysicsConfig config,
                                    TerrainTriangle triangle) {
        return contact(center, center, axis, config, triangle, 0b111);
    }

    static ContactCandidate contact(Vec3 previousCenter, Vec3 center, Vec3 axis,
                                    PhysicsConfig config, TerrainTriangle triangle) {
        return contact(previousCenter, center, axis, config, triangle, 0b111);
    }

    static ContactCandidate contact(Vec3 previousCenter, Vec3 center, Vec3 axis,
                                    PhysicsConfig config, TerrainTriangle triangle,
                                    int collisionBoundaryMask) {
        Vec3 normal = triangle.normal;
        if (normal.dot(Vec3.UP) < -0.001) {
            normal = normal.multiply(-1.0);
        }
        // Terrain patches are one-sided. A body already behind the authored face is not
        // teleported back through it; CCD/substeps are responsible for catching the crossing.
        if (center.subtract(triangle.a).dot(normal) < 0.0) {
            return null;
        }
        Vec3 previousDeepestPoint = support(previousCenter, axis,
                config.cylinderHalfLength, config.cylinderRadius, normal.multiply(-1.0));
        if (previousDeepestPoint.subtract(triangle.a).dot(normal)
                < -config.collisionSlop
                && center.subtract(previousCenter).dot(normal) > 0.0) {
            return null;
        }
        Vec3 direction = normal.multiply(-1.0);
        Vec3 radial = direction.subtract(axis.multiply(direction.dot(axis)));
        Vec3 radialOffset = radial.lengthSquared() < 1.0e-16
                ? Vec3.ZERO : radial.normalized().multiply(config.cylinderRadius);

        double[] axialSamples = {-config.cylinderHalfLength, 0.0, config.cylinderHalfLength};
        ContactCandidate best = null;
        for (double axialSample : axialSamples) {
            Vec3 point = center.add(axis.multiply(axialSample)).add(radialOffset);
            double separation = point.subtract(triangle.a).dot(normal);
            if (separation > config.collisionSlop) {
                continue;
            }
            Vec3 projected = point.subtract(normal.multiply(separation));
            if (!triangle.containsProjectedPoint(projected, 1.0e-5)) {
                continue;
            }
            double penetration = Math.max(0.0, -separation);
            if (best == null || penetration > best.penetration) {
                best = new ContactCandidate(triangle, projected, normal, penetration);
            }
        }
        return best == null
                ? boundaryContact(center, axis, config, triangle, normal,
                collisionBoundaryMask)
                : best;
    }

    private static ContactCandidate boundaryContact(
            Vec3 center, Vec3 axis, PhysicsConfig config,
            TerrainTriangle triangle, Vec3 faceNormal,
            int collisionBoundaryMask) {
        Vec3 unitAxis = axis.normalized();
        ContactCandidate best = null;
        if ((collisionBoundaryMask & 0b001) != 0) {
            best = edgeContact(center, unitAxis, config,
                    triangle, triangle.a, triangle.b, faceNormal);
        }
        if ((collisionBoundaryMask & 0b010) != 0) {
            best = better(best, edgeContact(center, unitAxis, config,
                    triangle, triangle.b, triangle.c, faceNormal));
        }
        if ((collisionBoundaryMask & 0b100) != 0) {
            best = better(best, edgeContact(center, unitAxis, config,
                    triangle, triangle.c, triangle.a, faceNormal));
        }
        return best;
    }

    private static ContactCandidate edgeContact(
            Vec3 center, Vec3 unitAxis, PhysicsConfig config,
            TerrainTriangle triangle, Vec3 edgeStart, Vec3 edgeEnd,
            Vec3 faceNormal) {
        Vec3 edge = edgeEnd.subtract(edgeStart);
        Vec3 relativeStart = edgeStart.subtract(center);
        double axialStart = relativeStart.dot(unitAxis);
        double axialDelta = edge.dot(unitAxis);
        double low = 0.0;
        double high = 1.0;
        if (Math.abs(axialDelta) < 1.0e-14) {
            if (axialStart < -config.cylinderHalfLength
                    || axialStart > config.cylinderHalfLength) {
                return null;
            }
        } else {
            double atNegativeCap =
                    (-config.cylinderHalfLength - axialStart) / axialDelta;
            double atPositiveCap =
                    (config.cylinderHalfLength - axialStart) / axialDelta;
            low = Math.max(low, Math.min(atNegativeCap, atPositiveCap));
            high = Math.min(high, Math.max(atNegativeCap, atPositiveCap));
            if (low > high) {
                return null;
            }
        }

        Vec3 radialStart = relativeStart.subtract(
                unitAxis.multiply(axialStart));
        Vec3 radialDelta = edge.subtract(
                unitAxis.multiply(axialDelta));
        double parameter;
        double radialDeltaLengthSquared = radialDelta.lengthSquared();
        if (radialDeltaLengthSquared < 1.0e-16) {
            parameter = low;
        } else {
            parameter = clamp(-radialStart.dot(radialDelta)
                    / radialDeltaLengthSquared, low, high);
        }
        Vec3 terrainPoint = edgeStart.add(edge.multiply(parameter));
        double axial = terrainPoint.subtract(center).dot(unitAxis);
        Vec3 axisPoint = center.add(unitAxis.multiply(axial));
        Vec3 outward = axisPoint.subtract(terrainPoint);
        double distance = outward.length();
        if (distance > config.cylinderRadius + config.collisionSlop) {
            return null;
        }
        Vec3 normal = distance < 1.0e-12
                ? faceNormal : outward.multiply(1.0 / distance);
        if (normal.dot(faceNormal) <= 0.0) {
            return null;
        }
        return new ContactCandidate(triangle, terrainPoint, normal,
                Math.max(0.0, config.cylinderRadius - distance));
    }

    private static ContactCandidate better(
            ContactCandidate current, ContactCandidate candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.penetration > current.penetration
                ? candidate : current;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static List<Vec3> rimSamples(Vec3 center, Vec3 axis, double halfLength,
                                        double radius, int segments) {
        if (segments < 3) {
            throw new IllegalArgumentException("At least three rim segments are required");
        }
        Vec3 helper = Math.abs(axis.y) < 0.9 ? Vec3.UP : new Vec3(1.0, 0.0, 0.0);
        Vec3 basisA = axis.cross(helper).normalized();
        Vec3 basisB = axis.cross(basisA).normalized();
        ArrayList<Vec3> points = new ArrayList<Vec3>(segments * 2);
        for (int cap = -1; cap <= 1; cap += 2) {
            Vec3 capCenter = center.add(axis.multiply(halfLength * cap));
            for (int i = 0; i < segments; i++) {
                double angle = i * Math.PI * 2.0 / segments;
                points.add(capCenter
                        .add(basisA.multiply(Math.cos(angle) * radius))
                        .add(basisB.multiply(Math.sin(angle) * radius)));
            }
        }
        return points;
    }

    static final class ContactCandidate {
        final TerrainTriangle triangle;
        final Vec3 point;
        final Vec3 normal;
        final double penetration;

        ContactCandidate(TerrainTriangle triangle, Vec3 point, Vec3 normal, double penetration) {
            this.triangle = triangle;
            this.point = point;
            this.normal = normal;
            this.penetration = penetration;
        }
    }
}
