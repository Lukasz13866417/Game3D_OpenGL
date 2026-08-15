package com.example.game3d.terrain.io.model;

import java.util.Objects;

/** One explicit, independently editable tile command. Angles are authored in degrees. */
public final class TileRecord {
    private final String sourceId;
    private final boolean solid;
    private final double turnDeltaDegrees;
    private final double absoluteSlopeDegrees;
    private final double liftBefore;
    private final String surfaceKind;
    private final double alpha;
    private final double brightness;
    private final Double resolvedTurnDeltaRadians;
    private final Double resolvedAbsoluteSlopeRadians;

    public TileRecord(
            String sourceId,
            boolean solid,
            double turnDeltaDegrees,
            double absoluteSlopeDegrees,
            double liftBefore,
            String surfaceKind,
            double alpha,
            double brightness) {
        this(sourceId, solid, turnDeltaDegrees, absoluteSlopeDegrees,
                liftBefore, surfaceKind, alpha, brightness, null, null);
    }

    public TileRecord(
            String sourceId,
            boolean solid,
            double turnDeltaDegrees,
            double absoluteSlopeDegrees,
            double liftBefore,
            String surfaceKind,
            double alpha,
            double brightness,
            Double resolvedTurnDeltaRadians,
            Double resolvedAbsoluteSlopeRadians) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.solid = solid;
        this.turnDeltaDegrees = turnDeltaDegrees;
        this.absoluteSlopeDegrees = absoluteSlopeDegrees;
        this.liftBefore = liftBefore;
        this.surfaceKind = Objects.requireNonNull(surfaceKind, "surfaceKind");
        this.alpha = alpha;
        this.brightness = brightness;
        this.resolvedTurnDeltaRadians = resolvedTurnDeltaRadians;
        this.resolvedAbsoluteSlopeRadians = resolvedAbsoluteSlopeRadians;
    }

    public String sourceId() { return sourceId; }
    public boolean solid() { return solid; }
    public double turnDeltaDegrees() { return turnDeltaDegrees; }
    public double absoluteSlopeDegrees() { return absoluteSlopeDegrees; }
    public double liftBefore() { return liftBefore; }
    public String surfaceKind() { return surfaceKind; }
    public double alpha() { return alpha; }
    public double brightness() { return brightness; }
    /** Optional authoritative command emitted by a materialized Java-provider import. */
    public Double resolvedTurnDeltaRadians() { return resolvedTurnDeltaRadians; }
    /** Optional authoritative command emitted by a materialized Java-provider import. */
    public Double resolvedAbsoluteSlopeRadians() { return resolvedAbsoluteSlopeRadians; }

    public TileRecord withValues(
            double turnDelta, double slope, double lift, double newAlpha, double newBrightness) {
        Double retainedTurn = Double.doubleToLongBits(turnDelta)
                == Double.doubleToLongBits(turnDeltaDegrees)
                ? resolvedTurnDeltaRadians : null;
        Double retainedSlope = Double.doubleToLongBits(slope)
                == Double.doubleToLongBits(absoluteSlopeDegrees)
                ? resolvedAbsoluteSlopeRadians : null;
        return new TileRecord(sourceId, solid, turnDelta, slope, lift, surfaceKind,
                newAlpha, newBrightness, retainedTurn, retainedSlope);
    }

    public TileRecord duplicate(String newSourceId) {
        return new TileRecord(newSourceId, solid, turnDeltaDegrees, absoluteSlopeDegrees,
                liftBefore, surfaceKind, alpha, brightness,
                resolvedTurnDeltaRadians, resolvedAbsoluteSlopeRadians);
    }

    @Override public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof TileRecord)) return false;
        TileRecord that = (TileRecord) value;
        return solid == that.solid
                && Double.compare(turnDeltaDegrees, that.turnDeltaDegrees) == 0
                && Double.compare(absoluteSlopeDegrees, that.absoluteSlopeDegrees) == 0
                && Double.compare(liftBefore, that.liftBefore) == 0
                && Double.compare(alpha, that.alpha) == 0
                && Double.compare(brightness, that.brightness) == 0
                && sourceId.equals(that.sourceId)
                && surfaceKind.equals(that.surfaceKind)
                && Objects.equals(resolvedTurnDeltaRadians, that.resolvedTurnDeltaRadians)
                && Objects.equals(resolvedAbsoluteSlopeRadians,
                that.resolvedAbsoluteSlopeRadians);
    }

    @Override public int hashCode() {
        return Objects.hash(sourceId, solid, turnDeltaDegrees, absoluteSlopeDegrees,
                liftBefore, surfaceKind, alpha, brightness,
                resolvedTurnDeltaRadians, resolvedAbsoluteSlopeRadians);
    }
}
