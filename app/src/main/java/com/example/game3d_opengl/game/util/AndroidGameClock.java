package com.example.game3d_opengl.game.util;

import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;

/**
 * One explicit, nanosecond-precision monotonic clock domain for Android input and simulation
 * scheduling.
 *
 * <p>{@link MotionEvent} timestamps use uptime. On Android, {@link System#nanoTime()} and
 * Choreographer timestamps use that same monotonic uptime time base with nanosecond precision.
 * Keeping the value in that native domain avoids both millisecond quantization and an artificial
 * epoch offset between input and simulation.</p>
 */
public final class AndroidGameClock {
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    private AndroidGameClock() {
    }

    public static long nowNanos() {
        return System.nanoTime();
    }

    /**
     * Maps a Choreographer/System.nanoTime timestamp into the MotionEvent uptime domain.
     *
     * <p>Unlike converting through {@code uptimeMillis()}, this retains the timestamp's
     * nanosecond cadence. In particular, 120 Hz is not reduced to a repeating 8/9 ms pattern.</p>
     */
    public static long fromSystemNanoTime(long monotonicTimeNanos) {
        return monotonicTimeNanos;
    }

    public static long eventTimeNanos(MotionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event == null");
        }
        if (Build.VERSION.SDK_INT >= 34) {
            return event.getEventTimeNanos();
        }
        return event.getEventTime() * NANOS_PER_MILLISECOND;
    }

    public static long historicalEventTimeNanos(MotionEvent event, int historyPosition) {
        if (event == null) {
            throw new IllegalArgumentException("event == null");
        }
        if (historyPosition < 0 || historyPosition >= event.getHistorySize()) {
            throw new IllegalArgumentException("Invalid history position " + historyPosition);
        }
        if (Build.VERSION.SDK_INT >= 34) {
            return event.getHistoricalEventTimeNanos(historyPosition);
        }
        return event.getHistoricalEventTime(historyPosition) * NANOS_PER_MILLISECOND;
    }
}
