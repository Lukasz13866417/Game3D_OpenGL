package com.example.game3d.core.terrain;

import com.example.game3d.core.math.Vec3;

/** Immutable feature authored by terrain generation and shared by simulation and presentation. */
public abstract class TerrainFeatureSpec {
    public enum Kind {
        SPIKE,
        AIR_JUMP_COLLECTIBLE,
        PORTAL
    }

    public final long id;
    public final long ownerSegmentId;
    public final Kind kind;

    TerrainFeatureSpec(long id, long ownerSegmentId, Kind kind) {
        if (id < 0L || ownerSegmentId < 0L) {
            throw new IllegalArgumentException("Feature and owner IDs must be non-negative");
        }
        this.id = id;
        this.ownerSegmentId = ownerSegmentId;
        this.kind = kind;
    }

    public abstract long deterministicDigest();

    public static final class Spike extends TerrainFeatureSpec {
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

        public Spike(
                long id,
                long ownerSegmentId,
                Vec3 nearLeft,
                Vec3 nearRight,
                Vec3 farLeft,
                Vec3 farRight,
                Vec3 apex,
                Vec3 outwardNormal,
                double baseOffset,
                Vec3 collisionBaseCenter,
                double collisionRadius,
                double collisionHeight) {
            super(id, ownerSegmentId, Kind.SPIKE);
            requireFinite(nearLeft, "nearLeft");
            requireFinite(nearRight, "nearRight");
            requireFinite(farLeft, "farLeft");
            requireFinite(farRight, "farRight");
            requireFinite(apex, "apex");
            requireFinite(outwardNormal, "outwardNormal");
            requireFinite(collisionBaseCenter, "collisionBaseCenter");
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
        }

        @Override
        public long deterministicDigest() {
            long hash = commonDigest(this);
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
    }

    public static final class AirJumpCollectible extends TerrainFeatureSpec {
        public final Vec3 center;
        public final double triggerRadius;
        public final String visualKind;

        public AirJumpCollectible(
                long id, long ownerSegmentId, Vec3 center,
                double triggerRadius, String visualKind) {
            super(id, ownerSegmentId, Kind.AIR_JUMP_COLLECTIBLE);
            requireFinite(center, "center");
            if (!(triggerRadius > 0.0) || !Double.isFinite(triggerRadius)) {
                throw new IllegalArgumentException("triggerRadius must be finite and positive");
            }
            if (visualKind == null || visualKind.isEmpty()) {
                throw new IllegalArgumentException("visualKind is empty");
            }
            this.center = center;
            this.triggerRadius = triggerRadius;
            this.visualKind = visualKind;
        }

        @Override
        public long deterministicDigest() {
            long hash = mixVec(commonDigest(this), center);
            hash = mix(hash, Double.doubleToLongBits(triggerRadius));
            return mixString(hash, visualKind);
        }
    }

    public static final class Portal extends TerrainFeatureSpec {
        public enum Role {
            ENTRANCE,
            EXIT
        }

        public final long pairId;
        public final Role role;
        public final Vec3 center;
        public final Vec3 forward;
        public final Vec3 up;
        public final double width;
        public final double height;
        public final String visualStyleId;

        public Portal(
                long id, long ownerSegmentId, long pairId, Role role,
                Vec3 center, Vec3 forward, Vec3 up,
                double width, double height, String visualStyleId) {
            super(id, ownerSegmentId, Kind.PORTAL);
            if (pairId < 0L || role == null) {
                throw new IllegalArgumentException("Invalid portal identity");
            }
            requireFinite(center, "center");
            requireFinite(forward, "forward");
            requireFinite(up, "up");
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
        }

        @Override
        public long deterministicDigest() {
            long hash = mix(commonDigest(this), pairId);
            hash = mix(hash, role.ordinal());
            hash = mixVec(hash, center);
            hash = mixVec(hash, forward);
            hash = mixVec(hash, up);
            hash = mix(hash, Double.doubleToLongBits(width));
            hash = mix(hash, Double.doubleToLongBits(height));
            return mixString(hash, visualStyleId);
        }
    }

    private static long commonDigest(TerrainFeatureSpec feature) {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, feature.id);
        hash = mix(hash, feature.ownerSegmentId);
        return mix(hash, feature.kind.ordinal());
    }

    private static void requireFinite(Vec3 value, String name) {
        if (value == null
                || !Double.isFinite(value.x)
                || !Double.isFinite(value.y)
                || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static long mixVec(long hash, Vec3 value) {
        hash = mix(hash, Double.doubleToLongBits(value.x));
        hash = mix(hash, Double.doubleToLongBits(value.y));
        return mix(hash, Double.doubleToLongBits(value.z));
    }

    private static long mixString(long hash, String value) {
        for (int i = 0; i < value.length(); i++) {
            hash = mix(hash, value.charAt(i));
        }
        return hash;
    }

    static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001b3L;
    }
}
