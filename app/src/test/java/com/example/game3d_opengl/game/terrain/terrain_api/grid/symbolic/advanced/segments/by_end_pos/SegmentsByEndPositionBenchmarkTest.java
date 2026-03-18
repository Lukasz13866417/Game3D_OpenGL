package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.PartialSegmentHandler;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertTrue;

public class SegmentsByEndPositionBenchmarkTest {
    private static final int BENCH_N_ROWS = 1500;
    // Keep width strip-like to mirror terrain shape.
    private static final int BENCH_N_COLS = 6;
    private static final int BENCH_QUERY_COUNT = 3000;
    private static final long BENCH_SEED = 20260204L;

    private static int[][] buildUniqueCellReserveWorkload(
            int nRows, int nCols, int queryCount, long seed) {
        int total = nRows * nCols;
        int[] order = new int[total];
        for (int i = 0; i < total; ++i) {
            order[i] = i;
        }
        Random rng = new Random(seed);
        for (int i = total - 1; i > 0; --i) {
            int j = rng.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }

        int use = Math.min(queryCount, total);
        int[][] queries = new int[use][2];
        for (int i = 0; i < use; ++i) {
            int code = order[i];
            queries[i][0] = code / nCols + 1; // row (1-indexed)
            queries[i][1] = code % nCols + 1; // col (1-indexed)
        }
        return queries;
    }

    private static long runOne(
            EndPosTreeKind kind, boolean vertical, int nRows, int nCols, int[][] queries) {
        PartialSegmentHandler handler = new PartialSegmentHandler(nRows, nCols, vertical, kind);
        // Start timing only after handler construction; this excludes node-pool setup time.
        long startNs = System.nanoTime();
        for (int[] q : queries) {
            handler.reserve(q[0], q[1], 1);
        }
        long elapsedNs = System.nanoTime() - startNs;
        handler.destroy();
        return elapsedNs;
    }

    @Test
    public void benchmark_balanced_bst_backends_with_injection() {
        int nRows = BENCH_N_ROWS;
        int nCols = BENCH_N_COLS;
        int queryCount = BENCH_QUERY_COUNT;
        int warmupRounds = 6;
        int measuredRounds = 4;
        int[][] queries = buildUniqueCellReserveWorkload(nRows, nCols, queryCount, BENCH_SEED);

        for (boolean vertical : new boolean[]{false, true}) {
            String orientation = vertical ? "vertical" : "horizontal";
            System.out.println("=== by_end_pos benchmark (" + orientation + ") ===");
            for (EndPosTreeKind kind : EndPosTreeKind.values()) {
                for (int i = 0; i < warmupRounds; ++i) {
                    runOne(kind, vertical, nRows, nCols, queries);
                }
                long totalNs = 0L;
                for (int i = 0; i < measuredRounds; ++i) {
                    totalNs += runOne(kind, vertical, nRows, nCols, queries);
                }
                double avgMs = (totalNs / (double) measuredRounds) / 1_000_000.0;
                double qps = (queries.length * 1_000_000_000.0) / (totalNs / (double) measuredRounds);
                System.out.printf(
                        "  %-16s avg=%.3f ms  qps=%.1f%n",
                        kind.name(),
                        avgMs,
                        qps
                );
                assertTrue("elapsed should be positive for " + kind, totalNs > 0L);
            }
        }
    }
}

