package com.example.game3d_opengl.game.terrain_api.terrainutil;


import com.example.game3d_opengl.game.terrain_api.main.TileBuilder.SegmentHistory;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * All objects are pre-allocated once and then reused.
 * A call to add(Vector3D, Vector3D, Vector3D, Vector3D, Vector3D) updates the fields of the
 * next buffer slot instead of allocating a new helper instance. This eliminates per-row object
 * creation while retaining constant-time FIFO behaviour.
 */
public class OverflowingPreallocatedSegmentHistoryBuffer {

    private static final int MAX_SIZE = 100_000;
    private static final int MAX_BUFFER_COUNT = 2;

    private static final SegmentHistory[][] BUFFERS = new SegmentHistory[MAX_BUFFER_COUNT][MAX_SIZE];
    private static final boolean[] IS_TAKEN = new boolean[MAX_BUFFER_COUNT];

    static {
        // Pre-instantiate every GridRowHelper so they can be reused without allocation.
        for (int i = 0; i < MAX_BUFFER_COUNT; i++) {
            for (int j = 0; j < MAX_SIZE; j++) {
                BUFFERS[i][j] = new SegmentHistory();
            }
        }
    }

    private final SegmentHistory[] myBuffer;
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

    public OverflowingPreallocatedSegmentHistoryBuffer() {
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

    public SegmentHistory get(int i) {
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
        size--;
    }

    public void clear() {
        head = 0;
        size = 0;
    }

    public SegmentHistory pop(){
        SegmentHistory res = get(size()-1);
        removeLast();
        return res;
    }

    /**
     * Overwrite / initialise the next element of the buffer with provided values.
     * Acts like a push-back; overwrites the oldest entry when the buffer is full.
     */
    public void add(int leftCnt, int rightCnt,
                    float leftoverL, float leftoverR,
                    Vector3D nl, Vector3D nr) {
        int writeIdx;
        if (size < MAX_SIZE) {
            writeIdx = (head + size) % MAX_SIZE;
            size++;
        } else {
            writeIdx = head;
            head = (head + 1) % MAX_SIZE; // drop oldest
        }
        SegmentHistory helper = myBuffer[writeIdx];
        helper.set(leftCnt, rightCnt, leftoverL, leftoverR, nl, nr);
    }
}
