package com.example.game3d_opengl.game.terrain.terrain_api.terrainutil;

import com.example.game3d_opengl.game.pooling.FixedPool;
import com.example.game3d_opengl.game.pooling.PooledResourcesOwner;
import com.example.game3d_opengl.game.pooling.PooledSlotLease;

public class OverflowingPreallocatedFloatBuffer extends PooledResourcesOwner {
    private static final int MAX_SIZE = 100_000;
    private static final int MAX_BUFFER_COUNT = 4;
    private static final FixedPool<float[]> POOL = new FixedPool<>(
            MAX_BUFFER_COUNT,
            () -> new float[MAX_SIZE],
            "No more available preallocated buffers."
    );

    private final PooledSlotLease<float[]> bufferLease;
    private final float[] myBuffer;

    // Tracks the “start” (front) of the buffer in myBuffer (cyclic index).
    private int head = 0;
    // Number of elements currently stored in the buffer.
    private int mySize = 0;

    private OverflowingPreallocatedFloatBuffer(PooledSlotLease<float[]> bufferLease) {
        super(null);
        this.bufferLease = bufferLease;
        this.myBuffer = bufferLease.get();
    }

    public static OverflowingPreallocatedFloatBuffer acquire() {
        return new OverflowingPreallocatedFloatBuffer(POOL.acquire());
    }

    /**
     * Releases this buffer slot so it can be reused by another instance.
     */
    public void free() {
        releasePooledResourcesRecursively();
    }

    /**
     * Appends one float value at the end of this buffer.
     * If the buffer is at capacity, overwrites (removes) the first element.
     */
    public void add(float x) {
        if (mySize < MAX_SIZE) {
            // There is space; put the element at the end.
            myBuffer[(head + mySize) % MAX_SIZE] = x;
            mySize++;
        } else {
            // Buffer is full; overwrite the oldest element (at 'head').
            myBuffer[head] = x;
            head = (head + 1) % MAX_SIZE; // Move head forward.
        }
    }

    /**
     * Empties this buffer (but does not release its slot).
     */
    public void clear() {
        head = 0;
        mySize = 0;
    }

    /**
     * Returns the number of floats currently in the buffer.
     */
    public int size() {
        return mySize;
    }

    /**
     * Returns the float at the specified index i, starting from the oldest element.
     */
    public float get(int i) {
        if (i < 0 || i >= mySize) {
            throw new IndexOutOfBoundsException("Index: " + i + ", Size: " + mySize);
        }
        return myBuffer[(head + i) % MAX_SIZE];
    }

    /**
     * Removes and returns the last float from the buffer.
     * This is the most recently added float.
     */
    public float pop() {
        if (mySize == 0) {
            throw new IllegalStateException("Cannot pop from an empty buffer.");
        }
        // The most recently added float is at index = (head + mySize - 1) modulo MAX_SIZE
        int idx = (head + mySize - 1) % MAX_SIZE;
        float value = myBuffer[idx];
        mySize--;
        return value;
    }

    @Override
    public void releasePooledResourcesRecursively() {
        clear();
        bufferLease.release();
    }
}
