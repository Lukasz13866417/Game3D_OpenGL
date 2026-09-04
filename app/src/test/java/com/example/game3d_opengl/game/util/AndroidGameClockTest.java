package com.example.game3d_opengl.game.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class AndroidGameClockTest {
    @Test
    public void systemNanoTimeConversionPreservesExact120HzCadenceAndEpoch() {
        long[] frameTimes = {
                9_123_456_789L,
                9_131_790_122L,
                9_140_123_456L,
                9_148_456_789L
        };

        long previous = AndroidGameClock.fromSystemNanoTime(frameTimes[0]);
        assertEquals(frameTimes[0], previous);
        for (int index = 1; index < frameTimes.length; index++) {
            long mapped = AndroidGameClock.fromSystemNanoTime(frameTimes[index]);
            assertEquals(frameTimes[index], mapped);
            assertEquals(frameTimes[index] - frameTimes[index - 1], mapped - previous);
            previous = mapped;
        }
        assertNotEquals(0L,
                AndroidGameClock.fromSystemNanoTime(frameTimes[1]) % 1_000_000L);
    }
}
