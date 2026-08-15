package com.example.game3d.core.terrain.addon;

import com.example.game3d.core.math.Aabb;

/**
 * Static authored addon definition. Placement is a one-shot sealing operation.
 *
 * <p>Authoring frontends resolve their commands into a fresh concrete addon first, then call
 * {@link #place(long, long, AddonFootprint)} during atomic materialization. Concrete geometry is
 * constructor-owned and immutable; the placement step supplies only canonical identity,
 * ownership, and the already-resolved footprint.</p>
 */
public abstract class Addon {
    public enum Kind {
        DEATH_SPIKE(0, "DEATH_SPIKE"),
        AIR_JUMP_POTION(1, "AIR_JUMP_POTION"),
        PORTAL(2, "PORTAL");

        public final int stableCode;
        public final String jsonTag;

        Kind(int stableCode, String jsonTag) {
            this.stableCode = stableCode;
            this.jsonTag = jsonTag;
        }
    }

    public enum ContactPhase {
        HAZARD,
        PICKUP,
        NONE
    }

    public final Kind kind;
    private long id;
    private long ownerSegmentId = -1L;
    private AddonFootprint footprint;
    private volatile boolean sealed;

    protected Addon(Kind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("kind == null");
        }
        this.kind = kind;
    }

    public final synchronized Addon place(
            long id, long ownerSegmentId, AddonFootprint footprint) {
        if (sealed) {
            throw new IllegalStateException("Addon has already been placed");
        }
        if (id < 1L || ownerSegmentId < 0L || footprint == null) {
            throw new IllegalArgumentException("Invalid addon placement");
        }
        validatePlacement(footprint);
        this.id = id;
        this.ownerSegmentId = ownerSegmentId;
        this.footprint = footprint;
        this.sealed = true;
        return this;
    }

    protected void validatePlacement(AddonFootprint footprint) {
    }

    public final long id() {
        requireSealed();
        return id;
    }

    public final long ownerSegmentId() {
        requireSealed();
        return ownerSegmentId;
    }

    public final AddonFootprint footprint() {
        requireSealed();
        return footprint;
    }

    public final boolean isSealed() {
        return sealed;
    }

    public abstract Aabb broadPhaseBounds();

    public abstract ContactPhase contactPhase();

    public abstract void evaluateContact(
            AddonContactContext context, AddonEffectSink effectSink);

    public abstract long deterministicDigest();

    protected final long commonDigest() {
        requireSealed();
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, id);
        hash = mix(hash, ownerSegmentId);
        return mix(hash, kind.stableCode);
    }

    protected final void requireSealed() {
        if (!sealed) {
            throw new IllegalStateException("Addon has not been placed");
        }
    }

    protected static long mixVec(long hash, com.example.game3d.core.math.Vec3 value) {
        hash = mix(hash, Double.doubleToLongBits(value.x));
        hash = mix(hash, Double.doubleToLongBits(value.y));
        return mix(hash, Double.doubleToLongBits(value.z));
    }

    protected static long mixString(long hash, String value) {
        for (int i = 0; i < value.length(); i++) {
            hash = mix(hash, value.charAt(i));
        }
        return hash;
    }

    protected static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001b3L;
    }
}
