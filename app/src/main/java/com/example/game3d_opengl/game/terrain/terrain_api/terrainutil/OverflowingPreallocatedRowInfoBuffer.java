package com.example.game3d_opengl.game.terrain.terrain_api.terrainutil;

import com.example.game3d_opengl.game.pooling.PooledResourcesOwner;
import com.example.game3d_opengl.game.pooling.PooledSlotLease;
import com.example.game3d_opengl.game.terrain.terrain_api.main.tilemanager.TileManager.GridRowInfo;

/**
 * All objects are pre-allocated once and then reused.
 * A call to add(...) updates the fields of the next buffer slot using raw float components
 * instead of allocating new Vector3D instances. This eliminates per-row object creation while
 * retaining constant-time FIFO behaviour.
 */
public class OverflowingPreallocatedRowInfoBuffer extends PooledResourcesOwner {

    private static final int MAX_SIZE = 100_000;

    private final PooledSlotLease<GridRowInfo[]> bufferLease;
    private final GridRowInfo[] myBuffer;

    // Circular indices
    private int head = 0; // points to oldest element
    private int size = 0; // number of valid elements

    public OverflowingPreallocatedRowInfoBuffer(PooledSlotLease<GridRowInfo[]> bufferLease) {
        super(null);
        this.bufferLease = bufferLease;
        this.myBuffer = bufferLease.get();
    }

    /** Release this buffer slot so it can be reused by another instance. */
    public void free() {
        releasePooledResourcesRecursively();
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

    @Override
    public void releasePooledResourcesRecursively() {
        clear();
        bufferLease.release();
    }
}
