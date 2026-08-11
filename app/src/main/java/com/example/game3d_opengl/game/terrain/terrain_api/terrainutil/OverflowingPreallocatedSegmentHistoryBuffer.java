package com.example.game3d_opengl.game.terrain.terrain_api.terrainutil;


import com.example.game3d_opengl.game.pooling.PooledResourcesOwner;
import com.example.game3d_opengl.game.pooling.PooledSlotLease;
import com.example.game3d_opengl.game.terrain.terrain_api.main.TileProfile;
import com.example.game3d_opengl.game.terrain.terrain_api.main.tilemanager.TileManager.SegmentHistory;

/**
 * All objects are pre-allocated once and then reused.
 * A call to add(...) updates the fields of the next buffer slot using raw float components
 * instead of allocating new Vector3D instances. This eliminates per-row object creation while
 * retaining constant-time FIFO behaviour.
 */
public class OverflowingPreallocatedSegmentHistoryBuffer extends PooledResourcesOwner {

    private static final int MAX_SIZE = 100_000;

    private final PooledSlotLease<SegmentHistory[]> bufferLease;
    private final SegmentHistory[] myBuffer;

    // Circular indices
    private int head = 0; // points to oldest element
    private int size = 0; // number of valid elements

    public OverflowingPreallocatedSegmentHistoryBuffer(PooledSlotLease<SegmentHistory[]> bufferLease) {
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
    public void add(boolean isEmpty, boolean isFirstLiftedUp,
                    int rowsAddedCnt,
                    float nLx, float nLy, float nLz,
                    float nRx, float nRy, float nRz,
                    float fLx, float fLy, float fLz,
                    float fRx, float fRy, float fRz,
                    float lastLx, float lastLy, float lastLz,
                    float lastRx, float lastRy, float lastRz,
                    float leftover,
                    float alphaL, float alphaR,
                    TileProfile tileProfile,
                    float brightnessMultiplier) {
        int writeIdx;
        if (size < MAX_SIZE) {
            writeIdx = (head + size) % MAX_SIZE;
            size++;
        } else {
            writeIdx = head;
            head = (head + 1) % MAX_SIZE; // drop oldest
        }
        SegmentHistory helper = myBuffer[writeIdx];
        helper.set(isEmpty, isFirstLiftedUp,
                rowsAddedCnt,
                nLx, nLy, nLz,
                nRx, nRy, nRz,
                fLx, fLy, fLz,
                fRx, fRy, fRz,
                lastLx, lastLy, lastLz,
                lastRx, lastRy, lastRz,
                leftover,
                alphaL, alphaR,
                tileProfile,
                brightnessMultiplier);
    }

    @Override
    public void releasePooledResourcesRecursively() {
        clear();
        bufferLease.release();
    }
}
