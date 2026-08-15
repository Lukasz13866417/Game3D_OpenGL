package com.example.game3d.authoring;

/** Handle for a captured one-shot structure awaiting atomic materialization. */
public final class QueuedStructure {
    private MaterializedStructure materialized;
    private RuntimeException failure;

    QueuedStructure() {
    }

    synchronized void complete(MaterializedStructure value) {
        if (materialized != null || failure != null || value == null) {
            throw new IllegalStateException("Structure result was completed more than once");
        }
        materialized = value;
    }

    synchronized void fail(RuntimeException value) {
        if (materialized != null || failure != null || value == null) {
            throw new IllegalStateException("Structure result was completed more than once");
        }
        failure = value;
    }

    public synchronized boolean isMaterialized() {
        return materialized != null;
    }

    public synchronized boolean isFailed() {
        return failure != null;
    }

    public synchronized MaterializedStructure materialized() {
        if (materialized == null) {
            if (failure != null) {
                throw new IllegalStateException("Structure materialization failed", failure);
            }
            throw new IllegalStateException("Structure has not been materialized yet");
        }
        return materialized;
    }
}
