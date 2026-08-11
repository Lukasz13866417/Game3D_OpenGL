package com.example.game3d.core.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.PriorityQueue;

/**
 * Thread-safe timestamp queue. An event is applied at the first fixed-tick boundary at or after
 * its timestamp, never before it occurred. A late event is applied to the next available tick
 * rather than being discarded.
 */
public final class TimestampedInputQueue {
    private final PriorityQueue<PlayerInputEvent> events =
            new PriorityQueue<PlayerInputEvent>();

    public synchronized void enqueue(PlayerInputEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event == null");
        }
        events.add(event);
    }

    public synchronized FixedStepInput drain(long tickStartNanos, long tickEndNanos) {
        if (tickEndNanos <= tickStartNanos) {
            throw new IllegalArgumentException("Invalid tick interval");
        }
        ArrayList<PlayerInputEvent> drained = new ArrayList<PlayerInputEvent>();
        // The simulation processes input before integrating [tickStart, tickEnd). Consuming an
        // event from inside that interval would make physics anticipate future input.
        while (!events.isEmpty() && events.peek().timeNanos <= tickStartNanos) {
            drained.add(events.remove());
        }
        return drained.isEmpty() ? FixedStepInput.EMPTY : new FixedStepInput(drained);
    }

    public synchronized int size() {
        return events.size();
    }

    public synchronized void clear() {
        events.clear();
    }
}
