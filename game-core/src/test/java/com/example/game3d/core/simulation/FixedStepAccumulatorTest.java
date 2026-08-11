package com.example.game3d.core.simulation;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FixedStepAccumulatorTest {
    @Test
    public void differentRenderSchedulesProduceSameTickCount() {
        assertEquals(runSchedule(120, 1_000_000_000L / 120L),
                runSchedule(30, 1_000_000_000L / 30L));
        assertEquals(120, runSchedule(120, 1_000_000_000L / 120L));
    }

    @Test
    public void overrunRetainsTimeInsteadOfSkippingOrUsingLargeDt() {
        FixedStepAccumulator accumulator = new FixedStepAccumulator(8);
        AtomicInteger ticks = new AtomicInteger();
        FixedStepAccumulator.AdvanceResult result = accumulator.advance(
                PhysicsConfig.FIXED_DT_NANOS * 20L,
                (start, end) -> ticks.incrementAndGet());
        assertEquals(8, ticks.get());
        assertTrue(result.overrun);
        assertEquals(PhysicsConfig.FIXED_DT_NANOS * 12L, result.retainedNanos);
    }

    @Test
    public void retainedOverrunCatchesUpOnLaterFramesWithoutSkippingTicks() {
        FixedStepAccumulator accumulator = new FixedStepAccumulator(3);
        AtomicInteger ticks = new AtomicInteger();
        accumulator.advance(PhysicsConfig.FIXED_DT_NANOS * 7L,
                (start, end) -> ticks.incrementAndGet());

        FixedStepAccumulator.AdvanceResult second = accumulator.advance(0L,
                (start, end) -> ticks.incrementAndGet());
        FixedStepAccumulator.AdvanceResult third = accumulator.advance(0L,
                (start, end) -> ticks.incrementAndGet());

        assertEquals(7, ticks.get());
        assertTrue(second.overrun);
        assertEquals(0L, third.retainedNanos);
    }

    @Test
    public void callbacksReceiveContiguousFixedIntervals() {
        FixedStepAccumulator accumulator = new FixedStepAccumulator(8);
        List<Long> boundaries = new ArrayList<Long>();

        accumulator.advance(PhysicsConfig.FIXED_DT_NANOS * 3L,
                (start, end) -> {
                    boundaries.add(start);
                    boundaries.add(end);
                });

        assertEquals(Long.valueOf(0L), boundaries.get(0));
        assertEquals(Long.valueOf(PhysicsConfig.FIXED_DT_NANOS), boundaries.get(1));
        assertEquals(boundaries.get(1), boundaries.get(2));
        assertEquals(Long.valueOf(PhysicsConfig.FIXED_DT_NANOS * 3L),
                boundaries.get(boundaries.size() - 1));
    }

    @Test
    public void resetClearsSimulationAndRetainedTime() {
        FixedStepAccumulator accumulator = new FixedStepAccumulator(1);
        accumulator.advance(PhysicsConfig.FIXED_DT_NANOS * 4L, (start, end) -> {
        });

        accumulator.reset();

        assertEquals(0L, accumulator.simulationTimeNanos());
        assertEquals(0L, accumulator.retainedNanos());
    }

    private static int runSchedule(int frames, long frameNanos) {
        FixedStepAccumulator accumulator = new FixedStepAccumulator(16);
        AtomicInteger ticks = new AtomicInteger();
        for (int i = 0; i < frames; i++) {
            accumulator.advance(frameNanos, (start, end) -> ticks.incrementAndGet());
        }
        return ticks.get();
    }
}
