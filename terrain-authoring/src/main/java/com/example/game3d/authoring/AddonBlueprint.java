package com.example.game3d.authoring;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.AddonFootprint;
import com.example.game3d.core.terrain.addon.DeathSpike;
import com.example.game3d.core.terrain.addon.Portal;
import com.example.game3d.core.terrain.addon.Potion;

/** Immutable authored addon parameters; placement geometry is supplied by the grid interpreter. */
public abstract class AddonBlueprint {
    public enum Kind { DEATH_SPIKE, AIR_JUMP_POTION, PORTAL }

    public final String sourceId;
    public final Kind kind;

    AddonBlueprint(String sourceId, Kind kind) {
        if (sourceId == null || sourceId.isEmpty() || kind == null) {
            throw new IllegalArgumentException("Invalid addon blueprint identity");
        }
        this.sourceId = sourceId;
        this.kind = kind;
    }

    abstract Addon place(
            long id, long ownerSegmentId, PlacementFootprint footprint,
            Terrain.PlacementIds ids);

    public static AddonBlueprint deathSpike(String sourceId) {
        return new SpikeBlueprint(sourceId, Double.NaN, 0.025, Integer.MIN_VALUE, Double.NaN);
    }

    public static AddonBlueprint deathSpike(
            String sourceId, double authoredHeight, double baseOffset) {
        return new SpikeBlueprint(sourceId, authoredHeight, baseOffset, Integer.MIN_VALUE, Double.NaN);
    }

    public static AddonBlueprint deathSpike(
            String sourceId, double authoredHeight, double baseOffset,
            double collisionRadius) {
        if (!(collisionRadius > 0.0) || !Double.isFinite(collisionRadius)) {
            throw new IllegalArgumentException("Invalid spike collision radius");
        }
        return new SpikeBlueprint(sourceId, authoredHeight, baseOffset,
                Integer.MIN_VALUE, collisionRadius);
    }

    /** Canonical gameplay spike whose height hash uses the historical section-local index. */
    public static AddonBlueprint canonicalDeathSpike(
            String sourceId, int positionIndex, double collisionRadius) {
        return new SpikeBlueprint(
                sourceId, Double.NaN, 0.025, positionIndex, collisionRadius);
    }

    public static AddonBlueprint airJumpPotion(String sourceId) {
        return new PotionBlueprint(sourceId, 0.22, 0.56, "POTION_FEATHER");
    }

    public static AddonBlueprint airJumpPotion(
            String sourceId, double triggerRadius,
            double heightAboveSurface, String visualStyle) {
        return new PotionBlueprint(
                sourceId, triggerRadius, heightAboveSurface, visualStyle);
    }

    public static AddonBlueprint portal(
            String sourceId, String pairKey, Portal.Role role) {
        return new PortalBlueprint(sourceId, pairKey, role, 1.0, 1.0, "BEACON");
    }

    private static final class SpikeBlueprint extends AddonBlueprint {
        private final double height;
        private final double baseOffset;
        private final int positionIndex;
        private final double collisionRadius;

        SpikeBlueprint(String sourceId, double height, double baseOffset,
                int positionIndex, double collisionRadius) {
            super(sourceId, Kind.DEATH_SPIKE);
            if ((!Double.isNaN(height) && (!(height > 0.0) || !Double.isFinite(height)))
                    || baseOffset < 0.0 || !Double.isFinite(baseOffset)) {
                throw new IllegalArgumentException("Invalid spike dimensions");
            }
            this.height = height;
            this.baseOffset = baseOffset;
            this.positionIndex = positionIndex;
            this.collisionRadius = collisionRadius;
        }

        @Override
        Addon place(
                long id, long owner, PlacementFootprint fp, Terrain.PlacementIds ids) {
            double resolvedHeight = Double.isNaN(height)
                    ? 0.42 + 0.12 * unitHash(owner * 31L
                            + (positionIndex == Integer.MIN_VALUE
                            ? fp.declarationIndex : positionIndex))
                    : height;
            Vec3 apex = fp.center.add(fp.normal.multiply(resolvedHeight));
            double radius = Double.isNaN(collisionRadius)
                    ? Math.min(fp.acrossLength() * 0.5, fp.alongLength() * 0.5)
                    : collisionRadius;
            DeathSpike addon = new DeathSpike(
                    fp.nearLeft, fp.nearRight, fp.farLeft, fp.farRight,
                    apex, fp.normal, baseOffset, fp.center, radius, resolvedHeight);
            addon.place(id, owner, fp.toCoreFootprint());
            return addon;
        }
    }

    private static final class PotionBlueprint extends AddonBlueprint {
        private final double triggerRadius;
        private final double height;
        private final String style;

        PotionBlueprint(String sourceId, double triggerRadius, double height, String style) {
            super(sourceId, Kind.AIR_JUMP_POTION);
            if (!(triggerRadius > 0.0) || !Double.isFinite(triggerRadius)
                    || !Double.isFinite(height) || style == null || style.isEmpty()) {
                throw new IllegalArgumentException("Invalid potion parameters");
            }
            this.triggerRadius = triggerRadius;
            this.height = height;
            this.style = style;
        }

        @Override
        Addon place(
                long id, long owner, PlacementFootprint fp, Terrain.PlacementIds ids) {
            Potion addon = new Potion(
                    fp.center.add(fp.normal.multiply(height)), triggerRadius, style);
            addon.place(id, owner, fp.toCoreFootprint());
            return addon;
        }
    }

    private static final class PortalBlueprint extends AddonBlueprint {
        private final String pairKey;
        private final Portal.Role role;
        private final double width;
        private final double height;
        private final String style;

        PortalBlueprint(
                String sourceId, String pairKey, Portal.Role role,
                double width, double height, String style) {
            super(sourceId, Kind.PORTAL);
            if (pairKey == null || pairKey.isEmpty() || role == null) {
                throw new IllegalArgumentException("Invalid portal identity");
            }
            this.pairKey = pairKey;
            this.role = role;
            this.width = width;
            this.height = height;
            this.style = style;
        }

        @Override
        Addon place(
                long id, long owner, PlacementFootprint fp, Terrain.PlacementIds ids) {
            Portal addon = new Portal(
                    ids.portalPair(pairKey), role,
                    fp.center.add(Vec3.UP.multiply(2.22)), fp.horizontalForward, Vec3.UP,
                    width, height, style);
            addon.place(id, owner, fp.toCoreFootprint());
            return addon;
        }
    }

    private static double unitHash(long value) {
        long mixed = value;
        mixed ^= mixed >>> 30;
        mixed *= 0xbf58476d1ce4e5b9L;
        mixed ^= mixed >>> 27;
        mixed *= 0x94d049bb133111ebL;
        mixed ^= mixed >>> 31;
        return (mixed >>> 11) * 0x1.0p-53;
    }
}
