package com.example.game3d.core.terrain;

/**
 * Renderer-neutral surface behavior authored by terrain generation.
 *
 * <p>The motor multiplier is stored explicitly so terrain generation and simulation cannot
 * silently disagree about the meaning of a visual profile.</p>
 */
public final class SurfaceProperties {
    public enum Kind {
        NORMAL,
        BOOST_RAMP,
        BOOST_RAMP_LAUNCH,
        LEGACY_BOOST
    }

    public static final SurfaceProperties NORMAL =
            new SurfaceProperties(Kind.NORMAL, 1.0);
    public static final SurfaceProperties BOOST_RAMP =
            new SurfaceProperties(Kind.BOOST_RAMP, 1.55);
    public static final SurfaceProperties BOOST_RAMP_LAUNCH =
            new SurfaceProperties(Kind.BOOST_RAMP_LAUNCH, 1.62);
    /** Compatibility value for existing hand-authored scenarios. */
    public static final SurfaceProperties LEGACY_BOOST =
            new SurfaceProperties(Kind.LEGACY_BOOST, 1.65);

    public final Kind kind;
    public final double motorSpeedMultiplier;

    public SurfaceProperties(Kind kind, double motorSpeedMultiplier) {
        if (kind == null) {
            throw new IllegalArgumentException("kind == null");
        }
        if (!(motorSpeedMultiplier > 0.0) || !Double.isFinite(motorSpeedMultiplier)) {
            throw new IllegalArgumentException(
                    "motorSpeedMultiplier must be finite and positive");
        }
        this.kind = kind;
        this.motorSpeedMultiplier = motorSpeedMultiplier;
    }

    public long deterministicFingerprint() {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, kind.ordinal());
        return mix(hash, Double.doubleToLongBits(motorSpeedMultiplier));
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SurfaceProperties)) {
            return false;
        }
        SurfaceProperties value = (SurfaceProperties) other;
        return kind == value.kind
                && Double.doubleToLongBits(motorSpeedMultiplier)
                == Double.doubleToLongBits(value.motorSpeedMultiplier);
    }

    @Override
    public int hashCode() {
        long bits = Double.doubleToLongBits(motorSpeedMultiplier);
        return 31 * kind.hashCode() + (int) (bits ^ (bits >>> 32));
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001b3L;
    }
}
