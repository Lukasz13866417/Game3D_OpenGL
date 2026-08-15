package com.example.game3d.core.terrain.addon;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;

/** Immutable placed spike geometry and canonical collision contract. */
public final class DeathSpike extends Addon {
    public final Vec3 nearLeft;
    public final Vec3 nearRight;
    public final Vec3 farLeft;
    public final Vec3 farRight;
    public final Vec3 apex;
    public final Vec3 outwardNormal;
    public final double baseOffset;
    public final Vec3 collisionBaseCenter;
    public final double collisionRadius;
    public final double collisionHeight;
    private final Aabb broadPhaseBounds;

    public DeathSpike(
            Vec3 nearLeft, Vec3 nearRight, Vec3 farLeft, Vec3 farRight,
            Vec3 apex, Vec3 outwardNormal, double baseOffset,
            Vec3 collisionBaseCenter, double collisionRadius,
            double collisionHeight) {
        super(Kind.DEATH_SPIKE);
        AddonFootprint.requireFinite(nearLeft, "nearLeft");
        AddonFootprint.requireFinite(nearRight, "nearRight");
        AddonFootprint.requireFinite(farLeft, "farLeft");
        AddonFootprint.requireFinite(farRight, "farRight");
        AddonFootprint.requireFinite(apex, "apex");
        AddonFootprint.requireFinite(outwardNormal, "outwardNormal");
        AddonFootprint.requireFinite(collisionBaseCenter, "collisionBaseCenter");
        if (!Double.isFinite(baseOffset) || baseOffset < 0.0
                || !(collisionRadius > 0.0) || !Double.isFinite(collisionRadius)
                || !(collisionHeight > 0.0) || !Double.isFinite(collisionHeight)) {
            throw new IllegalArgumentException("Invalid spike dimensions");
        }
        this.nearLeft = nearLeft;
        this.nearRight = nearRight;
        this.farLeft = farLeft;
        this.farRight = farRight;
        this.apex = apex;
        this.outwardNormal = outwardNormal;
        this.baseOffset = baseOffset;
        this.collisionBaseCenter = collisionBaseCenter;
        this.collisionRadius = collisionRadius;
        this.collisionHeight = collisionHeight;
        this.broadPhaseBounds = new Aabb(
                new Vec3(collisionBaseCenter.x - collisionRadius,
                        collisionBaseCenter.y,
                        collisionBaseCenter.z - collisionRadius),
                new Vec3(collisionBaseCenter.x + collisionRadius,
                        collisionBaseCenter.y + collisionHeight,
                        collisionBaseCenter.z + collisionRadius));
    }

    @Override
    protected void validatePlacement(AddonFootprint footprint) {
        if (!same(nearLeft, footprint.nearLeft)
                || !same(nearRight, footprint.nearRight)
                || !same(farLeft, footprint.farLeft)
                || !same(farRight, footprint.farRight)) {
            throw new IllegalArgumentException("Spike placement footprint differs from geometry");
        }
    }

    @Override
    public Aabb broadPhaseBounds() {
        requireSealed();
        return broadPhaseBounds;
    }

    @Override
    public ContactPhase contactPhase() {
        return ContactPhase.HAZARD;
    }

    @Override
    public void evaluateContact(AddonContactContext context, AddonEffectSink effectSink) {
        requireSealed();
        if (context == null || effectSink == null) {
            throw new IllegalArgumentException("Contact arguments cannot be null");
        }
        double dx = context.center.x - collisionBaseCenter.x;
        double dz = context.center.z - collisionBaseCenter.z;
        double horizontalRadius = collisionRadius + context.cylinderRadius
                + context.cylinderHalfLength;
        if (dx * dx + dz * dz <= horizontalRadius * horizontalRadius
                && context.bounds.min.y <= collisionBaseCenter.y + collisionHeight
                && context.bounds.max.y >= collisionBaseCenter.y) {
            effectSink.hitHazard(id());
        }
    }

    @Override
    public long deterministicDigest() {
        long hash = commonDigest();
        hash = mixVec(hash, nearLeft);
        hash = mixVec(hash, nearRight);
        hash = mixVec(hash, farLeft);
        hash = mixVec(hash, farRight);
        hash = mixVec(hash, apex);
        hash = mixVec(hash, outwardNormal);
        hash = mix(hash, Double.doubleToLongBits(baseOffset));
        hash = mixVec(hash, collisionBaseCenter);
        hash = mix(hash, Double.doubleToLongBits(collisionRadius));
        return mix(hash, Double.doubleToLongBits(collisionHeight));
    }

    private static boolean same(Vec3 left, Vec3 right) {
        return Double.doubleToLongBits(left.x) == Double.doubleToLongBits(right.x)
                && Double.doubleToLongBits(left.y) == Double.doubleToLongBits(right.y)
                && Double.doubleToLongBits(left.z) == Double.doubleToLongBits(right.z);
    }
}
