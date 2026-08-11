package com.example.game3d.core.terrain;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;

public abstract class TerrainFeature {
    public enum Kind {
        SPIKE,
        FEATHER
    }

    public final long id;
    public final long ownerSegmentId;
    public final Kind kind;
    public final Vec3 center;
    public final Aabb bounds;

    TerrainFeature(long id, Kind kind, Vec3 center, Aabb bounds) {
        this(id, -1L, kind, center, bounds);
    }

    TerrainFeature(long id, long ownerSegmentId, Kind kind, Vec3 center, Aabb bounds) {
        this.id = id;
        this.ownerSegmentId = ownerSegmentId;
        this.kind = kind;
        this.center = center;
        this.bounds = bounds;
    }

    public static final class Spike extends TerrainFeature {
        public final double radius;
        public final double height;

        public Spike(long id, Vec3 baseCenter, double radius, double height) {
            this(id, -1L, baseCenter, radius, height);
        }

        public Spike(
                long id, long ownerSegmentId, Vec3 baseCenter,
                double radius, double height) {
            super(id, ownerSegmentId, Kind.SPIKE, baseCenter,
                    new Aabb(
                            new Vec3(baseCenter.x - radius, baseCenter.y, baseCenter.z - radius),
                            new Vec3(baseCenter.x + radius, baseCenter.y + height,
                                    baseCenter.z + radius)));
            this.radius = radius;
            this.height = height;
        }
    }

    public static final class Feather extends TerrainFeature {
        public final double triggerRadius;

        public Feather(long id, Vec3 center, double triggerRadius) {
            this(id, -1L, center, triggerRadius);
        }

        public Feather(long id, long ownerSegmentId, Vec3 center, double triggerRadius) {
            super(id, ownerSegmentId, Kind.FEATHER, center,
                    Aabb.around(center, triggerRadius, triggerRadius, triggerRadius));
            this.triggerRadius = triggerRadius;
        }
    }
}
