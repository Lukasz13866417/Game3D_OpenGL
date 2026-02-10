package com.example.game3d_opengl.game.stage.stage_api;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lock-free, fixed-size, single-producer single-consumer ring buffer for touch events.
 * Producer (UI thread) writes via enqueue calls, consumer (GL thread) reads via {@link #dequeue}.
 * No allocations after construction; events are stored in preallocated slots.
 * If the buffer is full, the oldest unread event is silently dropped.
 */
public final class TouchEventQueue {

    public static final int TYPE_DOWN = 0;
    public static final int TYPE_UP = 1;
    public static final int TYPE_MOVE = 2;

    /** Preallocated event slot. Fields are only valid between enqueue and dequeue. */
    public static final class Event {
        public int type;
        public float x1, y1;
        public float x2, y2; // only used for TYPE_MOVE
    }

    private final Event[] ring;
    private final int capacity;

    // Written by producer, read by consumer
    private final AtomicInteger writePos = new AtomicInteger(0);
    // Written by consumer, read by producer
    private final AtomicInteger readPos = new AtomicInteger(0);

    // Scratch event returned by dequeue (avoids allocation on consumer side)
    private final Event dequeueResult = new Event();

    public TouchEventQueue(int capacity) {
        if (capacity < 2) capacity = 2;
        this.capacity = capacity;
        this.ring = new Event[capacity];
        for (int i = 0; i < capacity; i++) {
            ring[i] = new Event();
        }
    }

    /**
     * Enqueue a touch-down or touch-up event. Called from the UI thread.
     */
    public void enqueueDownOrUp(int type, float x, float y) {
        int w = writePos.get();
        int r = readPos.get();
        int next = (w + 1) % capacity;
        if (next == r) {
            // Full: drop oldest by advancing read
            readPos.compareAndSet(r, (r + 1) % capacity);
        }
        Event e = ring[w];
        e.type = type;
        e.x1 = x;
        e.y1 = y;
        e.x2 = 0f;
        e.y2 = 0f;
        writePos.set(next);
    }

    /**
     * Enqueue a touch-move event. Called from the UI thread.
     */
    public void enqueueMove(float x1, float y1, float x2, float y2) {
        int w = writePos.get();
        int r = readPos.get();
        int next = (w + 1) % capacity;
        if (next == r) {
            readPos.compareAndSet(r, (r + 1) % capacity);
        }
        Event e = ring[w];
        e.type = TYPE_MOVE;
        e.x1 = x1;
        e.y1 = y1;
        e.x2 = x2;
        e.y2 = y2;
        writePos.set(next);
    }

    /**
     * Try to dequeue one event. Returns the shared Event instance if available, null if empty.
     * The returned Event is only valid until the next call to dequeue.
     * Called from the GL thread.
     */
    public Event dequeue() {
        int r = readPos.get();
        int w = writePos.get();
        if (r == w) return null;
        Event src = ring[r];
        dequeueResult.type = src.type;
        dequeueResult.x1 = src.x1;
        dequeueResult.y1 = src.y1;
        dequeueResult.x2 = src.x2;
        dequeueResult.y2 = src.y2;
        readPos.set((r + 1) % capacity);
        return dequeueResult;
    }

    /**
     * Returns true if the queue has no pending events.
     */
    public boolean isEmpty() {
        return readPos.get() == writePos.get();
    }
}
