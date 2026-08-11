package com.example.game3d.core.simulation;

/**
 * Converts arbitrary render elapsed time into authoritative 120 Hz ticks without ever enlarging
 * the physics timestep or discarding accumulated time.
 */
public final class FixedStepAccumulator {
    public interface TickCallback {
        void onTick(long tickStartNanos, long tickEndNanos);
    }

    public static final class AdvanceResult {
        public final int executedTicks;
        public final boolean overrun;
        public final long retainedNanos;
        public final double renderAlpha;

        AdvanceResult(int executedTicks, boolean overrun,
                      long retainedNanos, double renderAlpha) {
            this.executedTicks = executedTicks;
            this.overrun = overrun;
            this.retainedNanos = retainedNanos;
            this.renderAlpha = renderAlpha;
        }
    }

    private final int maxCatchUpTicks;
    private long accumulatorNanos;
    private long simulationTimeNanos;

    public FixedStepAccumulator(int maxCatchUpTicks) {
        if (maxCatchUpTicks < 1) {
            throw new IllegalArgumentException("maxCatchUpTicks must be positive");
        }
        this.maxCatchUpTicks = maxCatchUpTicks;
    }

    public AdvanceResult advance(long elapsedNanos, TickCallback callback) {
        if (elapsedNanos < 0L) {
            elapsedNanos = 0L;
        }
        accumulatorNanos = saturatingAdd(accumulatorNanos, elapsedNanos);
        int ticks = 0;
        while (accumulatorNanos >= PhysicsConfig.FIXED_DT_NANOS
                && ticks < maxCatchUpTicks) {
            long start = simulationTimeNanos;
            simulationTimeNanos += PhysicsConfig.FIXED_DT_NANOS;
            accumulatorNanos -= PhysicsConfig.FIXED_DT_NANOS;
            callback.onTick(start, simulationTimeNanos);
            ticks++;
        }
        boolean overrun = accumulatorNanos >= PhysicsConfig.FIXED_DT_NANOS;
        double alpha = Math.min(1.0,
                (double) accumulatorNanos / (double) PhysicsConfig.FIXED_DT_NANOS);
        return new AdvanceResult(ticks, overrun, accumulatorNanos, alpha);
    }

    public long simulationTimeNanos() {
        return simulationTimeNanos;
    }

    public long retainedNanos() {
        return accumulatorNanos;
    }

    public void reset() {
        accumulatorNanos = 0L;
        simulationTimeNanos = 0L;
    }

    private static long saturatingAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
