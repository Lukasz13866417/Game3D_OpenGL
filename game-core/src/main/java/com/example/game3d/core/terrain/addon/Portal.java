package com.example.game3d.core.terrain.addon;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;

/** One visual-only endpoint of a portal pair. */
public final class Portal extends Addon {
    public enum Role {
        ENTRANCE(0, "ENTRANCE"),
        EXIT(1, "EXIT");

        public final int stableCode;
        public final String jsonTag;

        Role(int stableCode, String jsonTag) {
            this.stableCode = stableCode;
            this.jsonTag = jsonTag;
        }
    }

    public final long pairId;
    public final Role role;
    public final Vec3 center;
    public final Vec3 forward;
    public final Vec3 up;
    public final double width;
    public final double height;
    public final String visualStyleId;
    private final Aabb broadPhaseBounds;

    public Portal(
            long pairId, Role role, Vec3 center, Vec3 forward, Vec3 up,
            double width, double height, String visualStyleId) {
        super(Kind.PORTAL);
        if (pairId < 1L || role == null) {
            throw new IllegalArgumentException("Invalid portal identity");
        }
        AddonFootprint.requireFinite(center, "center");
        AddonFootprint.requireFinite(forward, "forward");
        AddonFootprint.requireFinite(up, "up");
        if (!(width > 0.0) || !Double.isFinite(width)
                || !(height > 0.0) || !Double.isFinite(height)) {
            throw new IllegalArgumentException("Portal dimensions must be positive");
        }
        if (visualStyleId == null || visualStyleId.isEmpty()) {
            throw new IllegalArgumentException("visualStyleId is empty");
        }
        this.pairId = pairId;
        this.role = role;
        this.center = center;
        this.forward = forward;
        this.up = up;
        this.width = width;
        this.height = height;
        this.visualStyleId = visualStyleId;
        double radius = Math.max(width, height) * 0.5;
        this.broadPhaseBounds = Aabb.around(center, radius, radius, radius);
    }

    @Override
    public Aabb broadPhaseBounds() {
        requireSealed();
        return broadPhaseBounds;
    }

    @Override
    public ContactPhase contactPhase() {
        return ContactPhase.NONE;
    }

    @Override
    public void evaluateContact(AddonContactContext context, AddonEffectSink effectSink) {
        requireSealed();
        if (context == null || effectSink == null) {
            throw new IllegalArgumentException("Contact arguments cannot be null");
        }
        // Portals intentionally remain visual-only.
    }

    @Override
    public long deterministicDigest() {
        long hash = mix(commonDigest(), pairId);
        hash = mix(hash, role.stableCode);
        hash = mixVec(hash, center);
        hash = mixVec(hash, forward);
        hash = mixVec(hash, up);
        hash = mix(hash, Double.doubleToLongBits(width));
        hash = mix(hash, Double.doubleToLongBits(height));
        return mixString(hash, visualStyleId);
    }
}
