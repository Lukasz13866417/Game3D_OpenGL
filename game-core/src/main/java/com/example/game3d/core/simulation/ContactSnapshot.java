package com.example.game3d.core.simulation;

import com.example.game3d.core.math.Vec3;

public final class ContactSnapshot {
    public enum TimingQuality {
        SUBSTEP_ESTIMATE,
        SWEPT_TOI,
        OVERLAP_RECOVERY
    }

    public final long triangleId;
    public final Vec3 point;
    public final Vec3 normal;
    public final double penetration;
    public final double normalImpulse;
    /** Endpoint that would have been reached without this contact, when captured. */
    public final Vec3 detectedCenter;
    /** Canonical nonpenetrating center at which the contact was resolved, when captured. */
    public final Vec3 resolvedCenter;
    /** Positive for a gap and negative for penetration. */
    public final double signedSeparation;
    public final double tickFraction;
    public final Vec3 preVelocity;
    public final Vec3 postVelocity;
    public final double preAngularVelocity;
    public final double postAngularVelocity;
    public final String feature;
    public final int castIterations;
    public final TimingQuality timingQuality;

    public ContactSnapshot(long triangleId, Vec3 point, Vec3 normal,
                           double penetration, double normalImpulse) {
        this(triangleId, point, normal, penetration, normalImpulse,
                null, null, -penetration, Double.NaN, null, null,
                Double.NaN, Double.NaN,
                "UNKNOWN", 0, TimingQuality.SUBSTEP_ESTIMATE);
    }

    public ContactSnapshot(long triangleId, Vec3 point, Vec3 normal,
                           double penetration, double normalImpulse,
                           Vec3 detectedCenter, Vec3 resolvedCenter,
                           double signedSeparation, double tickFraction,
                           Vec3 preVelocity, Vec3 postVelocity,
                           double preAngularVelocity, double postAngularVelocity,
                           String feature, int castIterations,
                           TimingQuality timingQuality) {
        this.triangleId = triangleId;
        this.point = point;
        this.normal = normal;
        this.penetration = penetration;
        this.normalImpulse = normalImpulse;
        this.detectedCenter = detectedCenter;
        this.resolvedCenter = resolvedCenter;
        this.signedSeparation = signedSeparation;
        this.tickFraction = tickFraction;
        this.preVelocity = preVelocity;
        this.postVelocity = postVelocity;
        this.preAngularVelocity = preAngularVelocity;
        this.postAngularVelocity = postAngularVelocity;
        this.feature = feature;
        this.castIterations = castIterations;
        this.timingQuality = timingQuality;
    }
}
