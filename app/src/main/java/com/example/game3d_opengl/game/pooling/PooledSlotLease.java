package com.example.game3d_opengl.game.pooling;

public final class PooledSlotLease<T> implements PooledLease {
    private final T resource;
    private final Runnable releaseAction;
    private boolean released = false;

    public PooledSlotLease(T resource, Runnable releaseAction) {
        this.resource = resource;
        this.releaseAction = releaseAction;
    }

    public T get() {
        return resource;
    }

    @Override
    public void release() {
        if (released) {
            return;
        }
        released = true;
        releaseAction.run();
    }
}
