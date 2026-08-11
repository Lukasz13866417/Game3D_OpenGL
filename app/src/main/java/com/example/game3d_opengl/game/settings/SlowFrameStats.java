package com.example.game3d_opengl.game.settings;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Locale;

import com.example.game3d_opengl.game.util.GameVersion;

public final class SlowFrameStats {
    public static final float SLOW_FRAME_THRESHOLD_MS = 12.0f;

    private static final int MAX_RECORDS = 2048;
    private static final Object LOCK = new Object();

    private static final ArrayDeque<Record> records = new ArrayDeque<>();
    private static long nextRecordId = 1L;
    private static int droppedRecordCount = 0;
    private static FrameSnapshot lastCompletedFrame =
            new FrameSnapshot("None", false, -1f, false, GcObservation.unavailable());
    private static PendingFrameSnapshot currentFrame;

    private SlowFrameStats() {}

    public static void beginFrame(String stageName) {
        if (!SlowFrameStatsSettings.isCaptureEnabled()) {
            return;
        }
        synchronized (LOCK) {
            currentFrame = new PendingFrameSnapshot(
                    safeStageName(stageName),
                    GcRuntimeStats.capture()
            );
        }
    }

    public static void markGameplayRunElapsed(float runElapsedMs) {
        if (!SlowFrameStatsSettings.isCaptureEnabled()) {
            return;
        }
        synchronized (LOCK) {
            if (currentFrame != null) {
                currentFrame.isGameplay = true;
                currentFrame.runElapsedMs = Math.max(0f, runElapsedMs);
            }
        }
    }

    public static void markTerrainGenerating() {
        if (!SlowFrameStatsSettings.isCaptureEnabled()) {
            return;
        }
        synchronized (LOCK) {
            if (currentFrame != null) {
                currentFrame.terrainGenerating = true;
            }
        }
    }

    public static void endFrame() {
        if (!SlowFrameStatsSettings.isCaptureEnabled()) {
            return;
        }
        synchronized (LOCK) {
            if (currentFrame == null) {
                lastCompletedFrame = new FrameSnapshot(
                        "None",
                        false,
                        -1f,
                        false,
                        GcObservation.unavailable()
                );
            } else {
                lastCompletedFrame = currentFrame.freeze(GcRuntimeStats.capture());
                currentFrame = null;
            }
        }
    }

    public static FrameSnapshot getLastCompletedFrameSnapshot() {
        synchronized (LOCK) {
            return lastCompletedFrame;
        }
    }

    public static void maybeCaptureCompletedSlowFrame(float frameDurationMs) {
        if (frameDurationMs <= SLOW_FRAME_THRESHOLD_MS || !SlowFrameStatsSettings.isCaptureEnabled()) {
            return;
        }
        synchronized (LOCK) {
            if (records.size() >= MAX_RECORDS) {
                records.removeFirst();
                droppedRecordCount++;
            }
            records.addLast(new Record(nextRecordId++, frameDurationMs, lastCompletedFrame));
        }
    }

    public static int getStoredRecordCount() {
        synchronized (LOCK) {
            return records.size();
        }
    }

    public static void dumpToStdout() {
        System.out.print(buildDump());
    }

    static String buildDump() {
        synchronized (LOCK) {
            StringBuilder out = new StringBuilder(256 + records.size() * 64);
            out.append(
                    "report_type,game_version,capture_enabled,slow_frame_threshold_ms,"
                            + "stored_entries_total,dropped_entries_total,record_id,frame_duration_ms,"
                            + "stage_name,frame_context,run_elapsed_since_start_ms,terrain_generating,"
                            + "gc_status,gc_count_delta,gc_time_delta_ms\n"
            );
            out.append("summary,");
            out.append(csv(GameVersion.displayString())).append(',');
            out.append(SlowFrameStatsSettings.isCaptureEnabled()).append(',');
            out.append(formatMs(SLOW_FRAME_THRESHOLD_MS)).append(',');
            out.append(records.size()).append(',');
            out.append(droppedRecordCount).append(",,,,,,,,,\n");
            Iterator<Record> iterator = records.iterator();
            while (iterator.hasNext()) {
                Record record = iterator.next();
                out.append("slow_frame,");
                out.append(csv(GameVersion.displayString())).append(',');
                out.append(SlowFrameStatsSettings.isCaptureEnabled()).append(',');
                out.append(formatMs(SLOW_FRAME_THRESHOLD_MS)).append(',');
                out.append(records.size()).append(',');
                out.append(droppedRecordCount).append(',');
                out.append(record.id).append(',');
                out.append(formatMs(record.frameDurationMs)).append(',');
                out.append(csv(record.stageName)).append(',');
                out.append(record.isGameplay ? "gameplay" : "outside_gameplay").append(',');
                if (record.isGameplay) {
                    out.append(formatMs(record.runElapsedMs));
                }
                out.append(',').append(record.terrainGenerating).append(',');
                out.append(record.gcObservation.statusLabel()).append(',');
                if (record.gcObservation.isAvailable()) {
                    out.append(record.gcObservation.getGcCountDelta());
                }
                out.append(',');
                if (record.gcObservation.isAvailable()) {
                    out.append(record.gcObservation.getGcTimeDeltaMs());
                }
                out.append('\n');
            }
            return out.toString();
        }
    }

    static void resetForTests() {
        synchronized (LOCK) {
            records.clear();
            nextRecordId = 1L;
            droppedRecordCount = 0;
            lastCompletedFrame =
                    new FrameSnapshot("None", false, -1f, false, GcObservation.unavailable());
            currentFrame = null;
        }
    }

    private static String formatMs(float value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private static String csv(String value) {
        String safeValue = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safeValue + "\"";
    }

    private static String safeStageName(String stageName) {
        if (stageName == null || stageName.trim().isEmpty()) {
            return "None";
        }
        return stageName;
    }

    public static final class FrameSnapshot {
        private final String stageName;
        private final boolean isGameplay;
        private final float runElapsedMs;
        private final boolean terrainGenerating;
        private final GcObservation gcObservation;

        private FrameSnapshot(
                String stageName,
                boolean isGameplay,
                float runElapsedMs,
                boolean terrainGenerating,
                GcObservation gcObservation
        ) {
            this.stageName = stageName;
            this.isGameplay = isGameplay;
            this.runElapsedMs = runElapsedMs;
            this.terrainGenerating = terrainGenerating;
            this.gcObservation = gcObservation;
        }

        public String getStageName() {
            return stageName;
        }

        public boolean isGameplay() {
            return isGameplay;
        }

        public float getRunElapsedMs() {
            return runElapsedMs;
        }

        public boolean isTerrainGenerating() {
            return terrainGenerating;
        }

        public boolean isGcStatsAvailable() {
            return gcObservation.isAvailable();
        }

        public boolean isGcDetected() {
            return gcObservation.isDetected();
        }

        public long getGcCountDelta() {
            return gcObservation.getGcCountDelta();
        }

        public long getGcTimeDeltaMs() {
            return gcObservation.getGcTimeDeltaMs();
        }
    }

    private static final class PendingFrameSnapshot {
        private final String stageName;
        private final GcRuntimeStats.Sample gcStartSample;
        private boolean isGameplay = false;
        private float runElapsedMs = -1f;
        private boolean terrainGenerating = false;

        private PendingFrameSnapshot(String stageName, GcRuntimeStats.Sample gcStartSample) {
            this.stageName = stageName;
            this.gcStartSample = gcStartSample;
        }

        private FrameSnapshot freeze(GcRuntimeStats.Sample gcEndSample) {
            return new FrameSnapshot(
                    stageName,
                    isGameplay,
                    runElapsedMs,
                    terrainGenerating,
                    GcObservation.from(gcStartSample, gcEndSample)
            );
        }
    }

    private static final class Record {
        private final long id;
        private final float frameDurationMs;
        private final String stageName;
        private final boolean isGameplay;
        private final float runElapsedMs;
        private final boolean terrainGenerating;
        private final GcObservation gcObservation;

        private Record(long id, float frameDurationMs, FrameSnapshot snapshot) {
            this.id = id;
            this.frameDurationMs = frameDurationMs;
            this.stageName = snapshot.getStageName();
            this.isGameplay = snapshot.isGameplay();
            this.runElapsedMs = snapshot.getRunElapsedMs();
            this.terrainGenerating = snapshot.isTerrainGenerating();
            this.gcObservation = snapshot.gcObservation;
        }
    }

    private static final class GcObservation {
        private final boolean available;
        private final boolean detected;
        private final long gcCountDelta;
        private final long gcTimeDeltaMs;

        private GcObservation(
                boolean available,
                boolean detected,
                long gcCountDelta,
                long gcTimeDeltaMs
        ) {
            this.available = available;
            this.detected = detected;
            this.gcCountDelta = gcCountDelta;
            this.gcTimeDeltaMs = gcTimeDeltaMs;
        }

        private static GcObservation unavailable() {
            return new GcObservation(false, false, 0L, 0L);
        }

        private static GcObservation from(
                GcRuntimeStats.Sample start,
                GcRuntimeStats.Sample end
        ) {
            if (start == null || end == null || !start.isAvailable() || !end.isAvailable()) {
                return unavailable();
            }
            long gcCountDelta = Math.max(0L, end.getGcCount() - start.getGcCount());
            long gcTimeDeltaMs = Math.max(0L, end.getGcTimeMs() - start.getGcTimeMs());
            return new GcObservation(
                    true,
                    gcCountDelta > 0L || gcTimeDeltaMs > 0L,
                    gcCountDelta,
                    gcTimeDeltaMs
            );
        }

        private boolean isAvailable() {
            return available;
        }

        private boolean isDetected() {
            return detected;
        }

        private long getGcCountDelta() {
            return gcCountDelta;
        }

        private long getGcTimeDeltaMs() {
            return gcTimeDeltaMs;
        }

        private String statusLabel() {
            if (!available) {
                return "unavailable";
            }
            return detected ? "detected" : "not_detected";
        }
    }
}
