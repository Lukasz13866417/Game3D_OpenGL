package com.example.game3d_opengl.game.terrain.terrainutil.execbuffer;

public class PreallocatedCommandBuffer implements CommandBuffer {
    private static final int MAX_SIZE = 10_000;
    private static final int MAX_BUFFER_COUNT = 2;
    private static final float[][] BUFFERS = new float[MAX_BUFFER_COUNT][MAX_SIZE];
    private static final boolean[] isTaken = new boolean[MAX_BUFFER_COUNT];

    private static final float SEPARATOR = 0.069420f;

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
    private int mySize = 0;  // Number of floats stored in the buffer.
    private int readPos = 0; // Next command's starting index.

    public PreallocatedCommandBuffer() {
        int slot = findFreeSlot();
        if (slot == -1) {
            throw new IllegalStateException("No more available preallocated buffers.");
        }
        mySlot = slot;
        myBuffer = BUFFERS[mySlot];
        isTaken[mySlot] = true;
    }

    /**
     * Releases this buffer slot.
     */
    public void free() {
        isTaken[mySlot] = false;
    }

    @Override
    public void addCommand(float... args) {
        for (float arg : args) {
            if (mySize == MAX_SIZE) {
                throw new IllegalStateException("Buffer is full – cannot add command.");
            }
            int writePos = (readPos + mySize) % MAX_SIZE;
            myBuffer[writePos] = arg;
            mySize++;
        }
        if (mySize == MAX_SIZE) {
            throw new IllegalStateException("Buffer is full – cannot add separator.");
        }
        int sepPos = (readPos + mySize) % MAX_SIZE;
        myBuffer[sepPos] = SEPARATOR;
        mySize++;
    }

    @Override
    public void executeFirstCommand(CommandExecutor executor) {
        if (mySize == 0) {
            throw new IllegalStateException("No commands to execute");
        }

        int index = readPos;
        int count = 0;

        while (count < mySize) {
            if (myBuffer[index] == SEPARATOR) {
                break;
            }
            index = (index + 1) % MAX_SIZE;
            count++;
        }

        int consumed;
        if (count < mySize) {
            executor.execute(myBuffer, readPos, count);
            consumed = count + 1;
        } else {
            executor.execute(myBuffer, readPos, count);
            consumed = count;
        }

        readPos = (readPos + consumed) % MAX_SIZE;
        mySize -= consumed;
    }

    @Override
    public boolean hasAnyCommands() {
        if (mySize == 0) {
            return false;
        }

        int index = readPos;
        int count = 0;
        while (count < mySize) {
            if (myBuffer[index] == SEPARATOR) {
                return true;
            }
            index = (index + 1) % MAX_SIZE;
            count++;
        }
        return false;
    }
}
