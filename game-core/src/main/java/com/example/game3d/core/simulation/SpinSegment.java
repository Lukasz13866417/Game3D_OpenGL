package com.example.game3d.core.simulation;

import com.example.game3d.core.math.Vec3;

/** One authoritative piece of axle rotation inside a fixed simulation tick. */
public final class SpinSegment {
    public enum Mode {
        AIR_MOTOR,
        SUPPORTED_ROLL,
        LANDING_SNAP
    }

    public final double startFraction;
    public final double endFraction;
    public final Mode mode;
    public final double deltaRadians;
    public final double startAngularVelocity;
    public final double endAngularVelocity;
    public final double signedDistance;
    public final long supportTriangleId;
    public final Vec3 supportNormal;

    public SpinSegment(
            double startFraction, double endFraction, Mode mode,
            double deltaRadians,
            double startAngularVelocity, double endAngularVelocity,
            double signedDistance,
            long supportTriangleId, Vec3 supportNormal) {
        if (startFraction < 0.0 || endFraction < startFraction || endFraction > 1.0) {
            throw new IllegalArgumentException("Spin fractions must be ordered in [0, 1]");
        }
        if (mode == null || supportNormal == null) {
            throw new IllegalArgumentException("Spin segment fields cannot be null");
        }
        this.startFraction = startFraction;
        this.endFraction = endFraction;
        this.mode = mode;
        this.deltaRadians = deltaRadians;
        this.startAngularVelocity = startAngularVelocity;
        this.endAngularVelocity = endAngularVelocity;
        this.signedDistance = signedDistance;
        this.supportTriangleId = supportTriangleId;
        this.supportNormal = supportNormal;
    }
}
