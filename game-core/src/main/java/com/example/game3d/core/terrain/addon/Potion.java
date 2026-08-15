package com.example.game3d.core.terrain.addon;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;

/** Static collectible that grants exactly one persistent air-jump charge. */
public final class Potion extends Addon {
    public static final int AIR_JUMP_CHARGES = 1;

    public final Vec3 center;
    public final double triggerRadius;
    public final String visualStyleId;
    private final Aabb broadPhaseBounds;

    public Potion(Vec3 center, double triggerRadius, String visualStyleId) {
        super(Kind.AIR_JUMP_POTION);
        AddonFootprint.requireFinite(center, "center");
        if (!(triggerRadius > 0.0) || !Double.isFinite(triggerRadius)) {
            throw new IllegalArgumentException("triggerRadius must be finite and positive");
        }
        if (visualStyleId == null || visualStyleId.isEmpty()) {
            throw new IllegalArgumentException("visualStyleId is empty");
        }
        this.center = center;
        this.triggerRadius = triggerRadius;
        this.visualStyleId = visualStyleId;
        this.broadPhaseBounds = Aabb.around(
                center, triggerRadius, triggerRadius, triggerRadius);
    }

    @Override
    public Aabb broadPhaseBounds() {
        requireSealed();
        return broadPhaseBounds;
    }

    @Override
    public ContactPhase contactPhase() {
        return ContactPhase.PICKUP;
    }

    @Override
    public void evaluateContact(AddonContactContext context, AddonEffectSink effectSink) {
        requireSealed();
        if (context == null || effectSink == null) {
            throw new IllegalArgumentException("Contact arguments cannot be null");
        }
        Vec3 offset = center.subtract(context.center);
        double axial = clamp(offset.dot(context.axis),
                -context.cylinderHalfLength, context.cylinderHalfLength);
        Vec3 nearestAxisPoint = context.center.add(context.axis.multiply(axial));
        double triggerDistance = context.cylinderRadius + triggerRadius;
        if (center.subtract(nearestAxisPoint).lengthSquared()
                <= triggerDistance * triggerDistance) {
            effectSink.grantAirJump(id(), AIR_JUMP_CHARGES);
        }
    }

    @Override
    public long deterministicDigest() {
        long hash = mixVec(commonDigest(), center);
        hash = mix(hash, Double.doubleToLongBits(triggerRadius));
        return mixString(hash, visualStyleId);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
