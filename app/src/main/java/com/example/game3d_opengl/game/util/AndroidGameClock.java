package com.example.game3d_opengl.game.util;

import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;

/**
 * One explicit monotonic clock domain for Android input and simulation scheduling.
 *
 * <p>MotionEvent timestamps use uptime. Keeping controller epochs and lifecycle transitions in
 * that same domain avoids relying on undocumented relationships between Android clock APIs.
 */
public final class AndroidGameClock {
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    private AndroidGameClock() {
    }

    public static long nowNanos() {
        return SystemClock.uptimeMillis() * NANOS_PER_MILLISECOND;
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
}
