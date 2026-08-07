package com.example.game3d.core.simulation;

import com.example.game3d.core.math.Vec3;

public final class SimulationEvent {
    public enum Type {
        JUMP,
        LANDING_JUMP_ARMED,
        LAND,
        BOUNCE,
        BOUNCE_SUPPRESSED,
        SPIKE_HIT,
        FEATHER_COLLECTED,
        PLAYER_DIED,
        INVARIANT_FAILURE
    }

    public final Type type;
    public final long subjectId;
    public final String detail;
    /** Exact simulation time for the event, or -1 when not captured. */
    public final long timeNanos;
    /** World-space event position, or null when the event has no meaningful position. */
    public final Vec3 position;
    /** Event location within its fixed tick, in [0, 1], or NaN when not captured. */
    public final double tickFraction;

    public SimulationEvent(Type type, long subjectId, String detail) {
        this(type, subjectId, detail, -1L, null, Double.NaN);
    }

    public SimulationEvent(Type type, long subjectId, String detail,
                           long timeNanos, Vec3 position, double tickFraction) {
        if (!Double.isNaN(tickFraction)
                && (tickFraction < 0.0 || tickFraction > 1.0)) {
            throw new IllegalArgumentException("tickFraction must be in [0, 1]");
        }
        this.type = type;
        this.subjectId = subjectId;
        this.detail = detail;
        this.timeNanos = timeNanos;
        this.position = position;
        this.tickFraction = tickFraction;
    }
}
