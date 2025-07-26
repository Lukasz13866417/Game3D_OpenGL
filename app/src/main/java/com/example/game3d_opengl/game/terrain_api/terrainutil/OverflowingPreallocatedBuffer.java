package com.example.game3d_opengl.game.terrain_api.terrainutil;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Supplier;

public class OverflowingPreallocatedBuffer<T> {
    private static final int MAX_SIZE = 100_000;
    private static final int MAX_BUFFER_COUNT = 2;

    /**
     * For each element type T we lazily build an array of shape [MAX_BUFFER_COUNT][MAX_SIZE],
     * pre-filling every slot via the given Supplier<T>, and track which slots are taken.
     */
    private static final Map<Class<?>, Object> BUFFERS_MAP   = new HashMap<>();
    private static final Map<Class<?>, boolean[]> IS_TAKEN_MAP = new HashMap<>();

    /** Ensure that we have built the buffers for this type, instantiating every element. */
    private static synchronized <T> void ensureBuffers(Class<T> clazz, Supplier<T> supplier) {
        if (!BUFFERS_MAP.containsKey(clazz)) {
            @SuppressWarnings("unchecked")
            T[][] buffers = (T[][]) Array.newInstance(clazz, MAX_BUFFER_COUNT, MAX_SIZE);
            for (int i = 0; i < MAX_BUFFER_COUNT; i++) {
                for (int j = 0; j < MAX_SIZE; j++) {
                    buffers[i][j] = supplier.get();
                }
            }
            BUFFERS_MAP.put(clazz, buffers);
            IS_TAKEN_MAP.put(clazz, new boolean[MAX_BUFFER_COUNT]);
        }
    }

    /** Find an unused slot in the pool, or –1 if all are in use. */
    private static int findFreeSlot(boolean[] isTaken) {
        for (int i = 0; i < MAX_BUFFER_COUNT; i++) {
            if (!isTaken[i]) {
                return i;
            }
        }
        return -1;
    }

    private final T[] myBuffer;
    private final boolean[] isTakenForType;
    private final int mySlot;

    // cyclic head index and number of elements currently stored
    private int head = 0;
    private int size = 0;

    /**
     * @param clazz    the Class object for T (needed for array creation)
     * @param supplier a zero-arg factory that returns a fresh T each time (used to pre-fill all slots)
     * 
     * Example:
     *   new OverflowingPreallocatedBuffer<MyType>(
     *       MyType.class, 
     *       MyType::new
     *   );
     */
    @SuppressWarnings("unchecked")
    public OverflowingPreallocatedBuffer(Class<T> clazz, Supplier<T> supplier) {
        ensureBuffers(clazz, supplier);
        T[][] buffers     = (T[][]) BUFFERS_MAP.get(clazz);
        boolean[] isTaken = IS_TAKEN_MAP.get(clazz);

        int slot = findFreeSlot(isTaken);
        if (slot == -1) {
            throw new IllegalStateException(
                "No more available preallocated buffers for type " + clazz.getName()
            );
        }

        this.myBuffer        = buffers[slot];
        this.isTakenForType  = isTaken;
        this.mySlot          = slot;
        isTaken[slot]        = true;
    }

    /** Release this slot back into the shared pool so another instance can reuse it. */
    public void free() {
        isTakenForType[mySlot] = false;
    }

    /** Append x to the “end” of the buffer; if full, overwrite the oldest element. */
    public void add(T x) {
        if (size < MAX_SIZE) {
            myBuffer[(head + size) % MAX_SIZE] = x;
            size++;
        } else {
            myBuffer[head] = x;
            head = (head + 1) % MAX_SIZE;
        }
    }

    /** Number of elements currently stored. */
    public int size() {
        return size;
    }

    /** Get the i-th element, counting from the oldest (0 ≤ i < size()). */
    public T get(int i) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException("Index: " + i + ", Size: " + size);
        }
        return myBuffer[(head + i) % MAX_SIZE];
    }

    /** Remove and return the most-recently added element. */
    public T pop() {
        if (size == 0) {
            throw new IllegalStateException("Cannot pop from an empty buffer.");
        }
        int idx = (head + size - 1) % MAX_SIZE;
        T value = myBuffer[idx];
        myBuffer[idx] = null;
        size--;
        return value;
    }

    /** Empty the buffer (does not release its slot). */
    public void clear() {
        int sz = size();
        for(int i=0;i<sz;++i){
            myBuffer[(head + i) % MAX_SIZE] = null;
        }
        head = 0;
        size = 0;
    }
}
