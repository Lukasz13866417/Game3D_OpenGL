package com.example.game3d_opengl.game.terrain.terrain_api.terrainutil;

import com.example.game3d_opengl.game.terrain.terrain_api.main.TileManager.GridRowInfo;

/**
 * All objects are pre-allocated once and then reused.
 * A call to add(...) updates the fields of the next buffer slot using raw float components
 * instead of allocating new Vector3D instances. This eliminates per-row object creation while
 * retaining constant-time FIFO behaviour.
 */
public class OverflowingPreallocatedRowInfoBuffer {

    private static final int MAX_SIZE = 100_000;
    private static final int MAX_BUFFER_COUNT = 2;

    private static final GridRowInfo[][] BUFFERS = new GridRowInfo[MAX_BUFFER_COUNT][MAX_SIZE];
    private static final boolean[] IS_TAKEN = new boolean[MAX_BUFFER_COUNT];

    static {
        // Pre-instantiate every GridRowHelper so they can be reused without allocation.
        for (int i = 0; i < MAX_BUFFER_COUNT; i++) {
            for (int j = 0; j < MAX_SIZE; j++) {
                BUFFERS[i][j] = new GridRowInfo();
            }
        }
    }

    private final GridRowInfo[] myBuffer;
    private final int mySlot;

    // Circular indices
    private int head = 0; // points to oldest element
    private int size = 0; // number of valid elements

    private static int findFreeSlot() {
        for (int i = 0; i < MAX_BUFFER_COUNT; i++) {
            if (!IS_TAKEN[i]) return i;
        }
        return -1;
    }

    public OverflowingPreallocatedRowInfoBuffer() {
        int slot = findFreeSlot();
        if (slot == -1) {
            throw new IllegalStateException("No more available preallocated row-info buffers.");
        }
        this.mySlot = slot;
        this.myBuffer = BUFFERS[slot];
        IS_TAKEN[slot] = true;
    }

    /** Release this buffer slot so it can be reused by another instance. */
    public void free() {
        IS_TAKEN[mySlot] = false;
    }

    public int size() {
        return size;
    }

    public GridRowInfo get(int i) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException("Index: " + i + ", Size: " + size);
        }
        return myBuffer[(head + i) % MAX_SIZE];
    }

    public void removeLast() {
        if (size == 0) {
            throw new IllegalStateException("Cannot pop from an empty buffer.");
        }
        int idx = (head + size - 1) % MAX_SIZE;
        GridRowInfo res = myBuffer[idx];
        size--;
    }

    public GridRowInfo pop(){
        GridRowInfo res = get(size()-1);
        removeLast();
        return res;
    }


    public void clear() {
        head = 0;
        size = 0;
    }

    /**
     * Overwrite / initialise the next element of the buffer with provided values.
     * Acts like a push-back; overwrites the oldest entry when the buffer is full.
     */
    public void add(long tileID,
                    float LSx, float LSy, float LSz,
                    float RSx, float RSy, float RSz,
                    float LS_lastx, float LS_lasty, float LS_lastz,
                    float RS_lastx, float RS_lasty, float RS_lastz) {
        int writeIdx;
        if (size < MAX_SIZE) {
            writeIdx = (head + size) % MAX_SIZE;
            size++;
        } else {
            writeIdx = head;
            head = (head + 1) % MAX_SIZE; // drop oldest
        }
        GridRowInfo helper = myBuffer[writeIdx];
        helper.set(tileID,
                LSx, LSy, LSz,
                RSx, RSy, RSz,
                LS_lastx, LS_lasty, LS_lastz,
                RS_lastx, RS_lasty, RS_lastz);
    }
}