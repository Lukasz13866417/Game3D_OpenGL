package com.example.game3d.authoring;

/** Small per-materialization SplitMix64 source; no process-global random state. */
public final class DeterministicRandom {
    private long state;

    public DeterministicRandom(long seed) {
        state = seed;
    }

    public long nextLong() {
        long z = (state += 0x9e3779b97f4a7c15L);
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        return (int) Math.floorMod(nextLong(), (long) bound);
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }
}
