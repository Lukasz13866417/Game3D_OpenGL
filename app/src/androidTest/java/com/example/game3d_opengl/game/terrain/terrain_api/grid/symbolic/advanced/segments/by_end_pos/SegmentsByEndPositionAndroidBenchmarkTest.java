package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos;

import androidx.benchmark.junit4.BenchmarkRule;
import androidx.benchmark.junit4.BenchmarkRuleKt;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.PartialSegmentHandler;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

import kotlin.Unit;

@RunWith(AndroidJUnit4.class)
public class SegmentsByEndPositionAndroidBenchmarkTest {

    @Rule
    public BenchmarkRule benchmarkRule = new BenchmarkRule();

    // Keep this intentionally lighter for quick smoke runs.
    private static final int N_ROWS = 700;
    // Keep width strip-like to mirror terrain shape.
    private static final int N_COLS = 6;
    private static final int QUERY_COUNT = 1200;
    private static final long SEED = 20260204L;
    private static final int[][] QUERIES =
            buildUniqueCellReserveWorkload(N_ROWS, N_COLS, QUERY_COUNT, SEED);

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

    private void runCase(EndPosTreeKind kind, boolean vertical) {
        BenchmarkRuleKt.measureRepeated(benchmarkRule, scope -> {
            final PartialSegmentHandler[] handlerHolder = new PartialSegmentHandler[1];
            scope.runWithTimingDisabled(() -> {
                handlerHolder[0] = new PartialSegmentHandler(N_ROWS, N_COLS, vertical, kind);
                return Unit.INSTANCE;
            });
            for (int[] q : QUERIES) {
                handlerHolder[0].reserve(q[0], q[1], 1);
            }
            scope.runWithTimingDisabled(() -> {
                handlerHolder[0].destroy();
                handlerHolder[0] = null;
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });
    }

    @Test
    public void treeSet_horizontal() {
        runCase(EndPosTreeKind.TREE_SET, false);
    }

    @Test
    public void pooledRbTree_horizontal() {
        runCase(EndPosTreeKind.POOLED_RB_TREE, false);
    }

    @Test
    public void pooledTreap_horizontal() {
        runCase(EndPosTreeKind.POOLED_TREAP, false);
    }

    @Test
    public void treeSet_vertical() {
        runCase(EndPosTreeKind.TREE_SET, true);
    }

    @Test
    public void pooledRbTree_vertical() {
        runCase(EndPosTreeKind.POOLED_RB_TREE, true);
    }

    @Test
    public void pooledTreap_vertical() {
        runCase(EndPosTreeKind.POOLED_TREAP, true);
    }
}
