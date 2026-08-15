package com.example.game3d.core.terrain.addon;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;

/** Renderer-neutral player shape used by static addon contact rules. */
public final class AddonContactContext {
    public final Vec3 center;
    public final Vec3 axis;
    public final double cylinderRadius;
    public final double cylinderHalfLength;
    public final Aabb bounds;

    public AddonContactContext(
            Vec3 center, Vec3 axis, double cylinderRadius,
            double cylinderHalfLength, Aabb bounds) {
        AddonFootprint.requireFinite(center, "center");
        AddonFootprint.requireFinite(axis, "axis");
        if (!(cylinderRadius > 0.0) || !(cylinderHalfLength > 0.0)
                || !Double.isFinite(cylinderRadius)
                || !Double.isFinite(cylinderHalfLength) || bounds == null) {
            throw new IllegalArgumentException("Invalid addon contact context");
        }
        this.center = center;
        this.axis = axis;
        this.cylinderRadius = cylinderRadius;
        this.cylinderHalfLength = cylinderHalfLength;
        this.bounds = bounds;
    }
}
