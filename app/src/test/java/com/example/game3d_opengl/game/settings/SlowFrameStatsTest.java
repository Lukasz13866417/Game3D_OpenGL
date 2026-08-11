package com.example.game3d_opengl.game.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.game3d_opengl.game.util.GameVersion;

import org.junit.After;
import org.junit.Test;

public class SlowFrameStatsTest {
    @After
    public void tearDown() {
        GameSettingsPersistence.clearForTests();
        GcRuntimeStats.resetProviderForTests();
        SlowFrameStats.resetForTests();
    }

    @Test
    public void maybeCaptureCompletedSlowFrame_records_gameplay_metadata_in_dump() {
        SlowFrameStatsSettings.setCaptureEnabled(true);
        SequenceProvider provider = new SequenceProvider(
                new GcRuntimeStats.Sample(10L, 100L),
                new GcRuntimeStats.Sample(11L, 108L)
        );
        GcRuntimeStats.installProviderForTests(provider);

        SlowFrameStats.beginFrame("GameplayStage");
        SlowFrameStats.markGameplayRunElapsed(456.25f);
        SlowFrameStats.markTerrainGenerating();
        SlowFrameStats.endFrame();
        SlowFrameStats.maybeCaptureCompletedSlowFrame(16.75f);

        String dump = SlowFrameStats.buildDump();
        assertTrue(dump.startsWith(
                "report_type,game_version,capture_enabled,slow_frame_threshold_ms,"
        ));
        assertTrue(dump.contains("summary,\"" + GameVersion.displayString() + "\",true,12.000,1,0"));
        assertTrue(dump.contains(
                "slow_frame,\"" + GameVersion.displayString()
                        + "\",true,12.000,1,0,1,16.750,\"GameplayStage\",gameplay,456.250,true,detected,1,8"
        ));
        assertEquals(2, provider.nextIndex);
    }

    @Test
    public void maybeCaptureCompletedSlowFrame_ignores_frames_when_capture_disabled() {
        SlowFrameStatsSettings.setCaptureEnabled(false);
        CountingProvider provider = new CountingProvider();
        GcRuntimeStats.installProviderForTests(provider);
        SlowFrameStats.FrameSnapshot snapshotBeforeFrame =
                SlowFrameStats.getLastCompletedFrameSnapshot();

        SlowFrameStats.beginFrame("LoadingStage");
        SlowFrameStats.markTerrainGenerating();
        SlowFrameStats.endFrame();
        SlowFrameStats.maybeCaptureCompletedSlowFrame(18f);

        assertEquals(0, SlowFrameStats.getStoredRecordCount());
        assertEquals(0, provider.captureCount);
        assertSame(snapshotBeforeFrame,
                SlowFrameStats.getLastCompletedFrameSnapshot());
    }

    private static final class SequenceProvider implements GcRuntimeStats.Provider {
        private final GcRuntimeStats.Sample[] samples;
        private int nextIndex = 0;

        private SequenceProvider(GcRuntimeStats.Sample... samples) {
            this.samples = samples;
        }

        @Override
        public GcRuntimeStats.Sample capture() {
            if (samples == null || samples.length == 0) {
                return GcRuntimeStats.Sample.unavailable();
            }
            int index = Math.min(nextIndex, samples.length - 1);
            nextIndex++;
            return samples[index];
        }
    }

    private static final class CountingProvider implements GcRuntimeStats.Provider {
        private int captureCount;

        @Override
        public GcRuntimeStats.Sample capture() {
            captureCount++;
            return new GcRuntimeStats.Sample(captureCount, captureCount);
        }
    }
}
