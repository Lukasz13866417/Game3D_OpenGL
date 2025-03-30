package com.example.game3d_opengl.game.terrain_api.terrainutil;

public class OverflowingPreallocatedFloatBuffer {
    private static final int MAX_SIZE = 100_000;
    private static final int MAX_BUFFER_COUNT = 2;
    private static final float[][] BUFFERS = new float[MAX_BUFFER_COUNT][MAX_SIZE];
    private static final boolean[] isTaken = new boolean[MAX_BUFFER_COUNT];

    private static int findFreeSlot() {
        for (int i = 0; i < MAX_BUFFER_COUNT; i++) {
            if (!isTaken[i]) {
                return i;
            }
        }
        return -1;
    }

    private final int mySlot;
    private final float[] myBuffer;

    // Tracks the “start” (front) of the buffer in myBuffer (cyclic index).
    private int head = 0;
    // Number of elements currently stored in the buffer.
    private int mySize = 0;

    public OverflowingPreallocatedFloatBuffer() {
        int slot = findFreeSlot();
        if (slot == -1) {
            throw new IllegalStateException("No more available preallocated buffers.");
        }
        mySlot = slot;
        myBuffer = BUFFERS[mySlot];
        isTaken[mySlot] = true;
    }

    /**
     * Releases this buffer slot so it can be reused by another instance.
     */
    public void free() {
        isTaken[mySlot] = false;
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
}
