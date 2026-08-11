package com.example.game3d_opengl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MyGLRendererTest {
    @Test
    public void repeatedSlowFrameWarningsAreLimitedToOncePerSecond() {
        long firstLogNanos = 5_000_000_000L;

        assertTrue(MyGLRenderer.shouldLogSlowFrameWarning(
                Long.MIN_VALUE, firstLogNanos));
        assertFalse(MyGLRenderer.shouldLogSlowFrameWarning(
                firstLogNanos,
                firstLogNanos
                        + MyGLRenderer.SLOW_FRAME_LOG_INTERVAL_NANOS
                        - 1L));
        assertTrue(MyGLRenderer.shouldLogSlowFrameWarning(
                firstLogNanos,
                firstLogNanos
                        + MyGLRenderer.SLOW_FRAME_LOG_INTERVAL_NANOS));
        assertTrue(MyGLRenderer.shouldLogSlowFrameWarning(
                firstLogNanos, firstLogNanos - 1L));
    }

    @Test
    public void skipped120HzSlotsAreDerivedFromRawUncappedVsyncTime() {
        assertEquals(0, MyGLRenderer.skipped120HzSlots(8_333_333L));
        assertEquals(1, MyGLRenderer.skipped120HzSlots(16_666_666L));
        assertEquals(2, MyGLRenderer.skipped120HzSlots(24_999_999L));
        assertEquals(4, MyGLRenderer.skipped120HzSlots(41_666_665L));
    }
}
