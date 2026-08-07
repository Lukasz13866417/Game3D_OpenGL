package com.example.game3d_opengl.game.stage.stages.main;

final class GameplayPhaseTimeline {
    private final float initialPhaseDurationMs;
    private final float phaseDurationGrowthFactor;

    private int completedPhaseCount;
    private float remainingUntilNextPhaseMs;

    GameplayPhaseTimeline(float initialPhaseDurationMs, float phaseDurationGrowthFactor) {
        if (initialPhaseDurationMs <= 0f) {
            throw new IllegalArgumentException("initialPhaseDurationMs must be > 0");
        }
        if (phaseDurationGrowthFactor < 1f) {
            throw new IllegalArgumentException("phaseDurationGrowthFactor must be >= 1");
        }
        this.initialPhaseDurationMs = initialPhaseDurationMs;
        this.phaseDurationGrowthFactor = phaseDurationGrowthFactor;
        reset();
    }

    void reset() {
        completedPhaseCount = 0;
        remainingUntilNextPhaseMs = getPhaseDurationMsForIndex(0);
    }

    int advance(float dtMs) {
        float remainingDt = Math.max(0f, dtMs);
        int completedNow = 0;
        int guard = 0;

        while (remainingDt > 1e-5f && guard++ < 16) {
            if (remainingDt < remainingUntilNextPhaseMs) {
                remainingUntilNextPhaseMs -= remainingDt;
                break;
            }
            remainingDt -= remainingUntilNextPhaseMs;
            completedPhaseCount += 1;
            completedNow += 1;
            remainingUntilNextPhaseMs = getPhaseDurationMsForIndex(completedPhaseCount);
        }

        return completedNow;
    }

    int getCompletedPhaseCount() {
        return completedPhaseCount;
    }

    float getRemainingUntilNextPhaseMs() {
        return remainingUntilNextPhaseMs;
    }

    int fillUpcomingTransitionOffsets(float[] out, boolean includeImmediateZero) {
        if (out == null || out.length == 0) {
            return 0;
        }

        int idx = 0;
        if (includeImmediateZero) {
            out[idx++] = 0f;
            if (idx >= out.length) {
                return idx;
            }
        }

        float cumulative = remainingUntilNextPhaseMs;
        int futurePhaseIndex = completedPhaseCount + 1;
        while (idx < out.length) {
            out[idx++] = cumulative;
            cumulative += getPhaseDurationMsForIndex(futurePhaseIndex);
            futurePhaseIndex += 1;
        }
        return idx;
    }

    float getPhaseDurationMsForIndex(int phaseIndex) {
        int safeIndex = Math.max(0, phaseIndex);
        return (float) (initialPhaseDurationMs * Math.pow(phaseDurationGrowthFactor, safeIndex));
    }
}
