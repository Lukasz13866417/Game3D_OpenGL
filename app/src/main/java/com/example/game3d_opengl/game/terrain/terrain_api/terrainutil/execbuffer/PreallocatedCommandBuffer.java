package com.example.game3d_opengl.game.terrain.terrain_api.terrainutil.execbuffer;

import com.example.game3d_opengl.game.pooling.PooledResourcesOwner;
import com.example.game3d_opengl.game.pooling.PooledSlotLease;

public class PreallocatedCommandBuffer extends PooledResourcesOwner implements CommandBuffer {
    private static final int MAX_SIZE = 20_000;

    private final PooledSlotLease<float[]> bufferLease;
    private final float[] myBuffer;
    private int mySize = 0;     // number of floats stored in the buffer
    private int readPos = 0;    // index of the next command in the ring buffer

    public PreallocatedCommandBuffer(PooledSlotLease<float[]> bufferLease) {
        super(null);
        this.bufferLease = bufferLease;
        this.myBuffer = bufferLease.get();
    }

    /**
     * Releases this buffer slot for reuse by another instance.
     */
    public void free() {
        releasePooledResourcesRecursively();
    }

    /**
     * Resets ring-buffer read/write state after acquiring an already-leased slot.
     */
    public void resetAfterAcquire() {
        mySize = 0;
        readPos = 0;
    }

    @Override
    public void releasePooledResourcesRecursively() {
        mySize = 0;
        readPos = 0;
        bufferLease.release();
    }

    /**
     * Adds a command to the buffer in the form:
     *   [commandCode, argCount, arg1, arg2, ..., argN].
     *
     * We interpret args[0] as 'commandCode'.
     * All subsequent floats (if any) are considered 'arguments.'
     *
     * So, if the caller does: addCommand(123f, 10f, 20f),
     * we store:
     *   [ 123f, 2, 10f, 20f ]
     *
     * The ring buffer approach is used here. If we don't have enough space,
     * we throw an exception.
     */
    @Override
    public void addCommand(float... args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("At least one float (the command code) is required.");
        }

        // The command code is the first float
        float commandCode = args[0];
        // The number of arguments is everything after the first float
        int argCount = args.length - 1;

        // We need space for exactly (2 + argCount) floats:
        //   [commandCode, argCount, arg1, arg2, ..., argN]
        int total = 2 + argCount;
        if (mySize + total > MAX_SIZE) {
            throw new IllegalStateException("Buffer is full – cannot add this command.");
        }

        // Write commandCode
        int writePos = (readPos + mySize) % MAX_SIZE;
        myBuffer[writePos] = commandCode;
        mySize++;

        // Write argCount
        writePos = (readPos + mySize) % MAX_SIZE;
        myBuffer[writePos] = argCount;
        mySize++;

        // Write the actual arguments
        for (int i = 1; i < args.length; i++) {
            writePos = (readPos + mySize) % MAX_SIZE;
            myBuffer[writePos] = args[i];
            mySize++;
        }
    }

    /**
     * Executes the first command by:
     *  - reading commandCode and argumentCount
     *  - reading that many argument floats
     *  - calling executor.execute(...) with that entire chunk
     *  - removing the command from the buffer
     * If there are no complete commands, throws an exception.
     */
    @Override
    public void executeFirstCommand(CommandExecutor executor) {
        if (!hasAnyCommands()) {
            throw new IllegalStateException("No complete commands to execute.");
        }

        int countIndex = (readPos + 1) % MAX_SIZE;
        int argCount = (int) myBuffer[countIndex];

        // total floats = 2 + argCount
        int total = 2 + argCount;

        // We'll hand these floats [commandCode, argCount, arg1, ..., argCount] to the executor
        executor.execute(myBuffer, readPos, total);

        // Now remove these floats from the ring buffer
        readPos = (readPos + total) % MAX_SIZE;
        mySize -= total;
    }

    /**
     * Returns true if there is at least one complete command in the buffer.
     * A complete command requires at least 2 floats: [commandCode, argCount],
     * plus 'argCount' floats of arguments. So we need mySize >= (2 + argCount).
     */
    @Override
    public boolean hasAnyCommands() {
        if (mySize < 2) {
            return false; // not enough data for even [cmdCode, argCount]
        }

        // Safely read the command code (though we don't truly need it here).
        float commandCode = myBuffer[readPos];

        // Read the argumentCount
        int countIndex = (readPos + 1) % MAX_SIZE;
        int argCount = (int) myBuffer[countIndex];
        if (argCount < 0) {
            // If for some reason someone wrote a negative argCount,
            // you might decide to handle that or treat as incomplete.
            return false;
        }

        // total needed to have a complete command
        int total = 2 + argCount;
        return (mySize >= total);
    }

}
