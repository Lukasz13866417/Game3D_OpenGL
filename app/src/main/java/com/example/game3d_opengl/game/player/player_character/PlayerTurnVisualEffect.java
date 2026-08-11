package com.example.game3d_opengl.game.player.player_character;

/**
 * Render-only steering accent for the player model.
 *
 * <p>The effect observes the canonical authoritative facing angle. It deliberately does not
 * observe touch coordinates, movement direction, or the model transform, so it cannot lead the
 * simulation or feed its own cosmetic offset back into the next frame.</p>
 */
final class PlayerTurnVisualEffect {
    /** Presentation-only tuning; neither value depends on player or forward movement speed. */
    static final float MAX_YAW_DEGREES = 6f;
    // One quarter of the former 240 deg/s full-scale rate: 4x response per yaw-rate unit.
    static final float FULL_YAW_RATE_DEGREES_PER_SECOND = 60f;
    static final float MIN_ACTIVE_YAW_RATE_DEGREES_PER_SECOND = 0.75f;
    static final float MEANINGFUL_YAW_CHANGE_DEGREES = 0.1875f;
    static final float REVERSAL_YAW_CHANGE_DEGREES = 0.375f;
    static final float QUIET_CONFIRM_MILLIS = 50f;
    static final float ACTIVE_SPRING_FREQUENCY_PER_SECOND = 55f;
    static final float RETURN_DELAY_MILLIS = 250f;
    static final float RETURN_SPRING_FREQUENCY_PER_SECOND = 14f;

    private static final double TWO_PI = Math.PI * 2.0;
    private static final double ZERO_SNAP_OFFSET_DEGREES = 0.01;
    private static final double ZERO_SNAP_VELOCITY_DEGREES_PER_SECOND = 0.05;

    private enum Phase {
        ACTIVE,
        DELAY,
        RETURNING
    }

    private boolean held;
    private boolean hasYawSample;
    private double lastYawRadians;
    private long lastYawSampleTimeNanos;
    private double pendingYawDeltaRadians;
    private double pendingYawMillis;
    private double unconfirmedYawMillis;
    private double yawOffsetDegrees;
    private double yawVelocityDegreesPerSecond;
    private double targetYawDegrees;
    private int activeTargetDirection;
    private double activeTargetMagnitudeDegrees;
    private double idleMillis;
    private Phase phase = Phase.DELAY;

    /** Starts a gameplay touch without deriving any strength from the touch itself. */
    void beginHold(double authoritativeYawRadians) {
        beginHold(authoritativeYawRadians, 0L);
    }

    void beginHold(
            double authoritativeYawRadians,
            long authoritativeSampleTimeNanos) {
        held = true;
        clearDynamics();
        if (Double.isFinite(authoritativeYawRadians)) {
            hasYawSample = true;
            lastYawRadians = authoritativeYawRadians;
            lastYawSampleTimeNanos = authoritativeSampleTimeNanos;
        }
    }

    /** Stops accepting turn samples and starts returning immediately, without the hold delay. */
    void endHold() {
        held = false;
        hasYawSample = false;
        lastYawRadians = 0.0;
        lastYawSampleTimeNanos = 0L;
        yawVelocityDegreesPerSecond = 0.0;
        beginReturn();
    }

    /**
     * Observes one canonical authoritative facing sample and advances the cosmetic response.
     *
     * <p>A positive logical yaw change produces a negative OpenGL model-yaw accent. The target
     * is based on angular velocity rather than a per-frame angle delta, keeping the strength
     * consistent at different render rates.</p>
     */
    void update(double authoritativeYawRadians, float dtMillis) {
        long elapsedNanos = Float.isFinite(dtMillis) && dtMillis > 0f
                ? Math.max(1L, (long) (dtMillis * 1_000_000.0))
                : 0L;
        long nextSampleTimeNanos = lastYawSampleTimeNanos
                > Long.MAX_VALUE - elapsedNanos
                ? Long.MAX_VALUE
                : lastYawSampleTimeNanos + elapsedNanos;
        update(authoritativeYawRadians, nextSampleTimeNanos, dtMillis);
    }

    void update(
            double authoritativeYawRadians,
            long authoritativeSampleTimeNanos,
            float dtMillis) {
        double safeDtMillis =
                Float.isFinite(dtMillis) ? Math.max(0.0, dtMillis) : 0.0;
        if (safeDtMillis <= 0.0) {
            // Preserve the previous sample so a real elapsed interval can measure this change.
            return;
        }
        if (!held) {
            if (phase == Phase.RETURNING) {
                advanceIdle(safeDtMillis);
            }
            return;
        }
        if (!Double.isFinite(authoritativeYawRadians)) {
            return;
        }
        if (!hasYawSample) {
            hasYawSample = true;
            lastYawRadians = authoritativeYawRadians;
            lastYawSampleTimeNanos = authoritativeSampleTimeNanos;
            return;
        }

        if (authoritativeSampleTimeNanos > lastYawSampleTimeNanos) {
            double sampleDtMillis =
                    (authoritativeSampleTimeNanos - lastYawSampleTimeNanos)
                            / 1_000_000.0;
            double deltaYawRadians = Math.IEEEremainder(
                    authoritativeYawRadians - lastYawRadians,
                    TWO_PI
            );
            lastYawRadians = authoritativeYawRadians;
            lastYawSampleTimeNanos = authoritativeSampleTimeNanos;
            accumulateYawChange(deltaYawRadians, sampleDtMillis);
            if (applyMeaningfulYawChange()) {
                unconfirmedYawMillis = 0.0;
            } else {
                unconfirmedYawMillis += sampleDtMillis;
                if (unconfirmedYawMillis >= QUIET_CONFIRM_MILLIS) {
                    clearPendingYawChange();
                    unconfirmedYawMillis = 0.0;
                    beginDelay();
                }
            }
        }

        if (phase == Phase.ACTIVE) {
            integrateSpring(
                    safeDtMillis,
                    ACTIVE_SPRING_FREQUENCY_PER_SECOND
            );
        } else {
            advanceIdle(safeDtMillis);
        }
    }

    void reset() {
        held = false;
        clearDynamics();
    }

    private void clearDynamics() {
        hasYawSample = false;
        lastYawRadians = 0.0;
        lastYawSampleTimeNanos = 0L;
        pendingYawDeltaRadians = 0.0;
        pendingYawMillis = 0.0;
        unconfirmedYawMillis = 0.0;
        yawOffsetDegrees = 0.0;
        yawVelocityDegreesPerSecond = 0.0;
        targetYawDegrees = 0.0;
        activeTargetDirection = 0;
        activeTargetMagnitudeDegrees = 0.0;
        idleMillis = 0.0;
        phase = Phase.DELAY;
    }

    float yawOffsetDegrees() {
        return (float) yawOffsetDegrees;
    }

    private void accumulateYawChange(
            double deltaYawRadians,
            double dtMillis) {
        if (Math.abs(deltaYawRadians) > 0.0) {
            pendingYawDeltaRadians += deltaYawRadians;
            if (Math.abs(pendingYawDeltaRadians) < 1.0e-12) {
                pendingYawDeltaRadians = 0.0;
                pendingYawMillis = 0.0;
                return;
            }
        }
        if (pendingYawDeltaRadians != 0.0) {
            pendingYawMillis += dtMillis;
        }
    }

    private boolean applyMeaningfulYawChange() {
        if (pendingYawDeltaRadians == 0.0) {
            return false;
        }
        int candidateTargetDirection = pendingYawDeltaRadians > 0.0 ? -1 : 1;
        double requiredDegrees = activeTargetDirection != 0
                && candidateTargetDirection != activeTargetDirection
                ? REVERSAL_YAW_CHANGE_DEGREES
                : MEANINGFUL_YAW_CHANGE_DEGREES;
        if (Math.abs(Math.toDegrees(pendingYawDeltaRadians)) < requiredDegrees) {
            return false;
        }

        double elapsedMillis = pendingYawMillis;
        double yawRateDegreesPerSecond =
                Math.toDegrees(pendingYawDeltaRadians) * 1000.0 / elapsedMillis;
        clearPendingYawChange();
        if (!Double.isFinite(yawRateDegreesPerSecond)
                || Math.abs(yawRateDegreesPerSecond)
                < MIN_ACTIVE_YAW_RATE_DEGREES_PER_SECOND) {
            return false;
        }

        double candidateTargetDegrees = clamp(
                -yawRateDegreesPerSecond
                        / FULL_YAW_RATE_DEGREES_PER_SECOND
                        * MAX_YAW_DEGREES,
                -MAX_YAW_DEGREES,
                MAX_YAW_DEGREES
        );
        double candidateMagnitudeDegrees = Math.abs(candidateTargetDegrees);
        if (candidateTargetDirection == activeTargetDirection) {
            // Finger deceleration and small same-direction coordinate noise must not command
            // an inward target. Only a stronger turn may increase the active accent.
            activeTargetMagnitudeDegrees = Math.max(
                    activeTargetMagnitudeDegrees,
                    candidateMagnitudeDegrees
            );
        } else {
            activeTargetDirection = candidateTargetDirection;
            activeTargetMagnitudeDegrees = candidateMagnitudeDegrees;
        }
        targetYawDegrees =
                activeTargetDirection * activeTargetMagnitudeDegrees;
        idleMillis = 0.0;
        phase = Phase.ACTIVE;
        return true;
    }

    private void clearPendingYawChange() {
        pendingYawDeltaRadians = 0.0;
        pendingYawMillis = 0.0;
    }

    private void beginDelay() {
        if (phase != Phase.ACTIVE) {
            return;
        }
        targetYawDegrees = yawOffsetDegrees;
        yawVelocityDegreesPerSecond = 0.0;
        idleMillis = 0.0;
        phase = Phase.DELAY;
    }

    private void beginReturn() {
        targetYawDegrees = 0.0;
        activeTargetDirection = 0;
        activeTargetMagnitudeDegrees = 0.0;
        clearPendingYawChange();
        unconfirmedYawMillis = 0.0;
        phase = Phase.RETURNING;
    }

    private void advanceIdle(double dtMillis) {
        double remainingMillis = dtMillis;
        if (phase == Phase.DELAY) {
            double heldMillis = Math.min(
                    remainingMillis,
                    RETURN_DELAY_MILLIS - idleMillis
            );
            idleMillis += heldMillis;
            remainingMillis -= heldMillis;
            if (idleMillis >= RETURN_DELAY_MILLIS) {
                beginReturn();
            }
        }
        if (phase == Phase.RETURNING && remainingMillis > 0.0) {
            targetYawDegrees = 0.0;
            integrateSpring(
                    remainingMillis,
                    RETURN_SPRING_FREQUENCY_PER_SECOND
            );
            idleMillis += remainingMillis;
        }

        if (targetYawDegrees == 0.0
                && Math.abs(yawOffsetDegrees)
                < ZERO_SNAP_OFFSET_DEGREES
                && Math.abs(yawVelocityDegreesPerSecond)
                < ZERO_SNAP_VELOCITY_DEGREES_PER_SECOND) {
            yawOffsetDegrees = 0.0;
            yawVelocityDegreesPerSecond = 0.0;
            activeTargetDirection = 0;
            activeTargetMagnitudeDegrees = 0.0;
        }
    }

    /**
     * Exact critically damped spring integration for a constant target over this interval.
     * Unlike an Euler step, this remains stable for long or uneven render frames.
     */
    private void integrateSpring(
            double dtMillis,
            double angularFrequencyPerSecond) {
        if (dtMillis <= 0.0) {
            return;
        }
        double dtSeconds = dtMillis / 1000.0;
        double displacement = yawOffsetDegrees - targetYawDegrees;
        double coefficient = yawVelocityDegreesPerSecond
                + angularFrequencyPerSecond * displacement;
        double decay = Math.exp(
                -angularFrequencyPerSecond * dtSeconds);
        yawOffsetDegrees = targetYawDegrees
                + (displacement + coefficient * dtSeconds) * decay;
        yawVelocityDegreesPerSecond =
                (yawVelocityDegreesPerSecond
                        - angularFrequencyPerSecond
                        * coefficient
                        * dtSeconds)
                        * decay;

        if (!Double.isFinite(yawOffsetDegrees)
                || !Double.isFinite(yawVelocityDegreesPerSecond)) {
            yawOffsetDegrees = targetYawDegrees;
            yawVelocityDegreesPerSecond = 0.0;
            return;
        }
        if (yawOffsetDegrees > MAX_YAW_DEGREES) {
            yawOffsetDegrees = MAX_YAW_DEGREES;
            if (yawVelocityDegreesPerSecond > 0.0) {
                yawVelocityDegreesPerSecond = 0.0;
            }
        } else if (yawOffsetDegrees < -MAX_YAW_DEGREES) {
            yawOffsetDegrees = -MAX_YAW_DEGREES;
            if (yawVelocityDegreesPerSecond < 0.0) {
                yawVelocityDegreesPerSecond = 0.0;
            }
        }
    }

    private static double clamp(
            double value,
            double minimum,
            double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
