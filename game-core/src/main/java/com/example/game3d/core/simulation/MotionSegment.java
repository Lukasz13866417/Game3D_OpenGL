package com.example.game3d.core.simulation;

import com.example.game3d.core.math.Vec3;

/**
 * One resolved piece of authoritative body motion inside a fixed simulation tick.
 *
 * <p>Events decorate this path; diagnostic probe/depenetration positions must never be inserted
 * into it.</p>
 */
public final class MotionSegment {
    public enum Phase {
        FREE_FLIGHT,
        SUPPORTED,
        POST_IMPACT
    }

    public final double startFraction;
    public final double endFraction;
    public final Vec3 startPosition;
    public final Vec3 endPosition;
    public final Phase phase;

    public MotionSegment(double startFraction, double endFraction,
                         Vec3 startPosition, Vec3 endPosition, Phase phase) {
        if (startFraction < 0.0 || endFraction < startFraction || endFraction > 1.0) {
            throw new IllegalArgumentException("Motion fractions must be ordered in [0, 1]");
        }
        if (startPosition == null || endPosition == null || phase == null) {
            throw new IllegalArgumentException("Motion segment fields cannot be null");
        }
        this.startFraction = startFraction;
        this.endFraction = endFraction;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.phase = phase;
    }
}
