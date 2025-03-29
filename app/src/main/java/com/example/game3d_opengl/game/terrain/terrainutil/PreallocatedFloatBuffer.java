package com.example.game3d_opengl.game.terrain.terrainutil;

public class PreallocatedFloatBuffer {
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
    private int mySize = 0;  // Number of floats currently stored.

    public PreallocatedFloatBuffer() {
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
     * Appends one float value at the end of this buffer,
     * or throws an exception if the buffer is already full.
     */
    public void add(float x) {
        if (mySize == MAX_SIZE) {
            throw new IllegalStateException("Buffer is full - cannot add more floats.");
        }
        myBuffer[mySize] = x;
        mySize++;
    }

    /**
     * Empties this buffer (but does not release its slot).
     * Future adds will begin again at index zero.
     */
    public void clear() {
        mySize = 0;
    }

    /**
     * Returns the number of floats currently in the buffer.
     */
    public int size() {
        return mySize;
    }

    /**
     * Returns the float at the specified index.
     */
    public float get(int i) {
        if (i < 0 || i >= mySize) {
            throw new IndexOutOfBoundsException("Index: " + i + ", Size: " + mySize);
        }
        return myBuffer[i];
    }

    /**
     * Removes and returns the last float from the buffer, or throws an exception if empty.
     */
    public float pop() {
        if (mySize == 0) {
            throw new IllegalStateException("Cannot pop from an empty buffer.");
        }
        mySize--;
        return myBuffer[mySize];
    }
}
