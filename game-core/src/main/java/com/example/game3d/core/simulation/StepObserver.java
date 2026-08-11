package com.example.game3d.core.simulation;

public interface StepObserver {
    /**
     * Sentinel for production callers that do not need per-step diagnostics. The engine uses this
     * exact instance to skip building queried-triangle, contact, motion, and {@link StepRecord}
     * data.
     */
    StepObserver NONE = new StepObserver() {
        @Override
        public void onStep(StepRecord record) {
        }
    };

    void onStep(StepRecord record);
}
