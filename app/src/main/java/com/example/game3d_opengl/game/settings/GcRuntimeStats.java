package com.example.game3d_opengl.game.settings;

import android.os.Debug;

final class GcRuntimeStats {
    private static final long UNAVAILABLE = Long.MIN_VALUE;
    private static final Provider DEFAULT_PROVIDER = new ArtRuntimeProvider();

    interface Provider {
        Sample capture();
    }

    static final class Sample {
        private final long gcCount;
        private final long gcTimeMs;

        Sample(long gcCount, long gcTimeMs) {
            this.gcCount = gcCount;
            this.gcTimeMs = gcTimeMs;
        }

        static Sample unavailable() {
            return new Sample(UNAVAILABLE, UNAVAILABLE);
        }

        boolean isAvailable() {
            return gcCount != UNAVAILABLE && gcTimeMs != UNAVAILABLE;
        }

        long getGcCount() {
            return gcCount;
        }

        long getGcTimeMs() {
            return gcTimeMs;
        }
    }

    private static volatile Provider provider = DEFAULT_PROVIDER;

    private GcRuntimeStats() {}

    static Sample capture() {
        try {
            Provider activeProvider = provider;
            if (activeProvider == null) {
                return Sample.unavailable();
            }
            Sample sample = activeProvider.capture();
            return sample != null ? sample : Sample.unavailable();
        } catch (Throwable ignored) {
            return Sample.unavailable();
        }
    }

    static void installProviderForTests(Provider testProvider) {
        provider = testProvider != null ? testProvider : DEFAULT_PROVIDER;
    }

    static void resetProviderForTests() {
        provider = DEFAULT_PROVIDER;
    }

    private static final class ArtRuntimeProvider implements Provider {
        @Override
        public Sample capture() {
            long gcCount = readLongStat("art.gc.gc-count");
            long gcTimeMs = readLongStat("art.gc.gc-time");
            if (gcCount == UNAVAILABLE || gcTimeMs == UNAVAILABLE) {
                return Sample.unavailable();
            }
            return new Sample(gcCount, gcTimeMs);
        }

        private static long readLongStat(String key) {
            try {
                String value = Debug.getRuntimeStat(key);
                if (value == null) {
                    return UNAVAILABLE;
                }
                String trimmed = value.trim();
                if (trimmed.isEmpty()) {
                    return UNAVAILABLE;
                }
                return Long.parseLong(trimmed);
            } catch (Throwable ignored) {
                return UNAVAILABLE;
            }
        }
    }
}
