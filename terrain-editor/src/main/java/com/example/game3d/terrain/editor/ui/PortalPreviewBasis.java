package com.example.game3d.terrain.editor.ui;

import com.example.game3d.core.math.Vec3;

/** Pure basis conversion from authored core coordinates to JavaFX preview coordinates. */
final class PortalPreviewBasis {
    private static final double EPSILON = 1.0e-12;

    final Vec3 right;
    final Vec3 up;
    final Vec3 forward;

    private PortalPreviewBasis(Vec3 right, Vec3 up, Vec3 forward) {
        this.right = right;
        this.up = up;
        this.forward = forward;
    }

    static PortalPreviewBasis from(Vec3 authoredForward, Vec3 authoredUp) {
        if (authoredForward == null || authoredUp == null) {
            throw new IllegalArgumentException("Portal forward and up are required");
        }
        Vec3 forward = toFx(authoredForward).normalized();
        if (forward.lengthSquared() < EPSILON) {
            forward = new Vec3(0.0, 0.0, -1.0);
        }
        Vec3 up = reject(toFx(authoredUp), forward).normalized();
        if (up.lengthSquared() < EPSILON) {
            Vec3 fallback = Math.abs(forward.dot(new Vec3(0.0, -1.0, 0.0))) < 0.9
                    ? new Vec3(0.0, -1.0, 0.0)
                    : new Vec3(1.0, 0.0, 0.0);
            up = reject(fallback, forward).normalized();
        }
        Vec3 right = up.cross(forward).normalized();
        // Recompute up so even slightly non-orthogonal authored axes yield a rigid transform.
        up = forward.cross(right).normalized();
        return new PortalPreviewBasis(right, up, forward);
    }

    private static Vec3 reject(Vec3 value, Vec3 axis) {
        return value.subtract(axis.multiply(value.dot(axis)));
    }

    private static Vec3 toFx(Vec3 core) {
        return new Vec3(core.x, -core.y, core.z);
    }
}
