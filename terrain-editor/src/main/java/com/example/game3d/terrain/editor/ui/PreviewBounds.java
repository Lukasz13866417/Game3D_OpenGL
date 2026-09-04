package com.example.game3d.terrain.editor.ui;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.AddonFootprint;

/** Finite JavaFX-space bounds used for deterministic preview framing. */
final class PreviewBounds {
    private final Vec3 minimum;
    private final Vec3 maximum;

    private PreviewBounds(Vec3 minimum, Vec3 maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    static PreviewBounds empty() {
        return new PreviewBounds(null, null);
    }

    static PreviewBounds from(TerrainSnapshot snapshot) {
        Builder bounds = new Builder();
        if (snapshot == null) return bounds.build();
        for (TerrainSegment segment : snapshot.segments) {
            bounds.includeWorld(segment.nearLeft);
            bounds.includeWorld(segment.nearRight);
            bounds.includeWorld(segment.farLeft);
            bounds.includeWorld(segment.farRight);
            for (Addon addon : segment.addons) {
                AddonFootprint footprint = addon.footprint();
                bounds.includeWorld(footprint.nearLeft);
                bounds.includeWorld(footprint.nearRight);
                bounds.includeWorld(footprint.farLeft);
                bounds.includeWorld(footprint.farRight);
                bounds.includeWorldAabb(addon.broadPhaseBounds().min,
                        addon.broadPhaseBounds().max);
            }
        }
        return bounds.build();
    }

    static PreviewBounds aroundWorld(Vec3... values) {
        Builder builder = new Builder();
        for (Vec3 value : values) builder.includeWorld(value);
        return builder.build();
    }

    boolean isEmpty() {
        return minimum == null;
    }

    Vec3 minimum() {
        return minimum;
    }

    Vec3 maximum() {
        return maximum;
    }

    Vec3 center() {
        return isEmpty() ? Vec3.ZERO : minimum.add(maximum).multiply(.5);
    }

    double radius() {
        if (isEmpty()) return 1.0;
        return Math.max(.05, maximum.subtract(minimum).length() * .5);
    }

    PreviewBounds union(PreviewBounds other) {
        if (other == null || other.isEmpty()) return this;
        if (isEmpty()) return other;
        return new PreviewBounds(new Vec3(
                Math.min(minimum.x, other.minimum.x),
                Math.min(minimum.y, other.minimum.y),
                Math.min(minimum.z, other.minimum.z)), new Vec3(
                Math.max(maximum.x, other.maximum.x),
                Math.max(maximum.y, other.maximum.y),
                Math.max(maximum.z, other.maximum.z)));
    }

    boolean contains(Vec3 value, double margin) {
        if (isEmpty() || value == null) return false;
        double padding = radius() * Math.max(0.0, margin);
        return value.x >= minimum.x - padding && value.x <= maximum.x + padding
                && value.y >= minimum.y - padding && value.y <= maximum.y + padding
                && value.z >= minimum.z - padding && value.z <= maximum.z + padding;
    }

    static Vec3 toFx(Vec3 world) {
        return new Vec3(world.x, -world.y, world.z);
    }

    static final class Builder {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        void includeWorld(Vec3 value) {
            if (value != null) includeFx(toFx(value));
        }

        void includeWorldAabb(Vec3 worldMinimum, Vec3 worldMaximum) {
            includeWorld(worldMinimum);
            includeWorld(worldMaximum);
        }

        void includeFx(Vec3 value) {
            if (value == null) return;
            minX = Math.min(minX, value.x);
            minY = Math.min(minY, value.y);
            minZ = Math.min(minZ, value.z);
            maxX = Math.max(maxX, value.x);
            maxY = Math.max(maxY, value.y);
            maxZ = Math.max(maxZ, value.z);
        }

        PreviewBounds build() {
            if (minX == Double.POSITIVE_INFINITY) return PreviewBounds.empty();
            return new PreviewBounds(new Vec3(minX, minY, minZ),
                    new Vec3(maxX, maxY, maxZ));
        }
    }
}
