package com.example.game3d.authoring;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.addon.AddonFootprint;

/** Resolved, segment-local placement footprint derived from completed geometry. */
public final class PlacementFootprint {
    public final Vec3 nearLeft;
    public final Vec3 nearRight;
    public final Vec3 farLeft;
    public final Vec3 farRight;
    public final Vec3 center;
    public final Vec3 normal;
    public final Vec3 forward;
    public final Vec3 horizontalForward;
    public final int declarationIndex;

    PlacementFootprint(
            Vec3 nearLeft, Vec3 nearRight, Vec3 farLeft, Vec3 farRight,
            Vec3 center, Vec3 normal, Vec3 forward, Vec3 horizontalForward,
            int declarationIndex) {
        this.nearLeft = nearLeft;
        this.nearRight = nearRight;
        this.farLeft = farLeft;
        this.farRight = farRight;
        this.center = center;
        this.normal = normal;
        this.forward = forward;
        this.horizontalForward = horizontalForward;
        this.declarationIndex = declarationIndex;
    }

    public double acrossLength() {
        return nearRight.subtract(nearLeft).length();
    }

    public double alongLength() {
        return farLeft.subtract(nearLeft).length();
    }

    AddonFootprint toCoreFootprint() {
        return AddonFootprint.quadrilateral(
                nearLeft, nearRight, farLeft, farRight);
    }
}
