package com.example.game3d_opengl.game.pooling;

import java.util.ArrayList;
import java.util.function.Supplier;

public final class FixedPool<T> {
    private final Object lock = new Object();
    private final ArrayList<T> resources;
    private final boolean[] taken;
    private final String exhaustedMessage;

    public FixedPool(int poolSize, Supplier<T> resourceFactory, String exhaustedMessage) {
        this.resources = new ArrayList<>(poolSize);
        this.taken = new boolean[poolSize];
        this.exhaustedMessage = exhaustedMessage;
        for (int i = 0; i < poolSize; ++i) {
            resources.add(resourceFactory.get());
        }
    }

    public PooledSlotLease<T> acquire() {
        synchronized (lock) {
            for (int i = 0; i < taken.length; ++i) {
                if (taken[i]) {
                    continue;
                }
                taken[i] = true;
                final int slot = i;
                return new PooledSlotLease<>(resources.get(i), () -> releaseSlot(slot));
            }
        }
        throw new IllegalStateException(exhaustedMessage);
    }

    private void releaseSlot(int slot) {
        synchronized (lock) {
            taken[slot] = false;
        }
    }
}
