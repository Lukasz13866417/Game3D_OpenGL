package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.cell_pair;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class CellPairQuerySegtreeTest {

    // ================================================================
    //  countGoodPairs — basic
    // ================================================================

    @Test
    public void countGoodPairs_fresh_grid_d0() {
        CellPairQuerySegtree t = new CellPairQuerySegtree(3, 4);
        assertEquals(66, t.countGoodPairs(0));
        t.destroy();
    }

    @Test
    public void countGoodPairs_fresh_grid_d1() {
        CellPairQuerySegtree t = new CellPairQuerySegtree(2, 2);
        assertEquals(3, t.countGoodPairs(1));
        t.destroy();
    }

    @Test
    public void countGoodPairs_d_too_large_returns_zero() {
        CellPairQuerySegtree t = new CellPairQuerySegtree(2, 2);
        assertEquals(0, t.countGoodPairs(3));
        assertEquals(0, t.countGoodPairs(10));
        t.destroy();
    }

    @Test
    public void countGoodPairs_after_reserve() {
        CellPairQuerySegtree t = new CellPairQuerySegtree(2, 3);
        assertEquals(15, t.countGoodPairs(0));
        t.reserve(1, 1);
        assertEquals(10, t.countGoodPairs(0));
        t.reserve(2, 3);
        assertEquals(6, t.countGoodPairs(0));
        t.destroy();
    }

    @Test
    public void countGoodPairs_d_equals_F_minus_2() {
        // F=5, d=3 => gap=5-3-1=1, total=1*2/2=1
        CellPairQuerySegtree t = new CellPairQuerySegtree(1, 5);
        assertEquals(1, t.countGoodPairs(3));
        t.destroy();
    }

    @Test
    public void countGoodPairs_d_equals_F_minus_1() {
        // F=5, d=4 => gap=0 => 0
        CellPairQuerySegtree t = new CellPairQuerySegtree(1, 5);
        assertEquals(0, t.countGoodPairs(4));
        t.destroy();
    }

    @Test
    public void countGoodPairs_1x1_grid() {
        CellPairQuerySegtree t = new CellPairQuerySegtree(1, 1);
        assertEquals(0, t.countGoodPairs(0));
        t.destroy();
    }

    @Test
    public void countGoodPairs_1x2_grid() {
        CellPairQuerySegtree t = new CellPairQuerySegtree(1, 2);
        // F=2, d=0 => 1 pair
        assertEquals(1, t.countGoodPairs(0));
        // d=1 => gap=2-1-1=0 => 0
        assertEquals(0, t.countGoodPairs(1));
        t.destroy();
    }

    // ================================================================
    //  countPairsInInterval — static formula
    // ================================================================

    @Test
    public void countPairsInInterval_all_contribute() {
        assertEquals(12, CellPairQuerySegtree.countPairsInInterval(3, 5, 2));
    }

    @Test
    public void countPairsInInterval_partial_contribute() {
        assertEquals(3, CellPairQuerySegtree.countPairsInInterval(4, 0, 1));
    }

    @Test
    public void countPairsInInterval_none_contribute() {
        assertEquals(0, CellPairQuerySegtree.countPairsInInterval(2, 0, 5));
    }

    @Test
    public void countPairsInInterval_cnt_zero() {
        assertEquals(0, CellPairQuerySegtree.countPairsInInterval(0, 10, 1));
    }

    @Test
    public void countPairsInInterval_d_zero() {
        // cnt=3, prefix=0, d=0. Ranks 1,2,3. Contributions: 0, 1, 2 => 3
        assertEquals(3, CellPairQuerySegtree.countPairsInInterval(3, 0, 0));
    }

    @Test
    public void countPairsInInterval_single_cell_with_large_prefix() {
        // cnt=1, prefix=10, d=3. Rank 11. Contribution: max(0, 10-3)=7
        assertEquals(7, CellPairQuerySegtree.countPairsInInterval(1, 10, 3));
    }

    @Test
    public void countPairsInInterval_boundary_t_equals_cnt() {
        // cnt=3, prefix=0, d=3. t=3, effective=0 => 0
        assertEquals(0, CellPairQuerySegtree.countPairsInInterval(3, 0, 3));
    }

    @Test
    public void countPairsInInterval_boundary_t_equals_cnt_minus_1() {
        // cnt=3, prefix=0, d=2. t=2, effective=1, base=0 => 0*0 + 1*0/2 = 0
        assertEquals(0, CellPairQuerySegtree.countPairsInInterval(3, 0, 2));
    }

    @Test
    public void countPairsInInterval_first_cell_barely_contributes() {
        // cnt=3, prefix=1, d=1. t=0, effective=3, base=0.
        // Contributions: max(0,0)=0, max(0,1)=1, max(0,2)=2 => 3
        assertEquals(3, CellPairQuerySegtree.countPairsInInterval(3, 1, 1));
    }

    // ================================================================
    //  findKthPairAndReserve — small deterministic
    // ================================================================

    @Test
    public void findKthPair_2x2_d1() {
        CellPairQuerySegtree t = new CellPairQuerySegtree(2, 2);
        assertEquals(3, t.countGoodPairs(1));
        GridSegment[] p1 = t.findKthPairAndReserve(1, 1);
        assertEquals(new GridSegment(1, 1, 1), p1[0]);
        assertEquals(new GridSegment(2, 1, 1), p1[1]);
        assertEquals(0, t.countGoodPairs(1));
        t.destroy();
    }

    @Test
    public void findKthPair_2x2_d1_k2() {
        CellPairQuerySegtree t = new CellPairQuerySegtree(2, 2);
        GridSegment[] p2 = t.findKthPairAndReserve(2, 1);
        assertEquals(new GridSegment(1, 1, 1), p2[0]);
        assertEquals(new GridSegment(2, 2, 1), p2[1]);
        t.destroy();
    }

    @Test
    public void findKthPair_2x2_d1_k3() {
        CellPairQuerySegtree t = new CellPairQuerySegtree(2, 2);
        GridSegment[] p3 = t.findKthPairAndReserve(3, 1);
        assertEquals(new GridSegment(1, 2, 1), p3[0]);
        assertEquals(new GridSegment(2, 2, 1), p3[1]);
        t.destroy();
    }

    @Test
    public void findKthPair_d0_all_pairs_1x4() {
        // Codes: 0,1,2,3. Pairs ordered by (y,x):
        //   k=1: (0,1), k=2: (0,2), k=3: (1,2), k=4: (0,3), k=5: (1,3), k=6: (2,3)
        CellPairQuerySegtree t = new CellPairQuerySegtree(1, 4);
        assertEquals(6, t.countGoodPairs(0));

        GridSegment[] p1 = t.findKthPairAndReserve(1, 0);
        assertEquals(new GridSegment(1, 1, 1), p1[0]);
        assertEquals(new GridSegment(1, 2, 1), p1[1]);
        assertEquals(1, t.countGoodPairs(0));
        t.destroy();
    }

    @Test
    public void findKthPair_d0_k6_1x4() {
        // k=6 => pair (2,3) => (1,3) and (1,4)
        CellPairQuerySegtree t = new CellPairQuerySegtree(1, 4);
        GridSegment[] p6 = t.findKthPairAndReserve(6, 0);
        assertEquals(new GridSegment(1, 3, 1), p6[0]);
        assertEquals(new GridSegment(1, 4, 1), p6[1]);
        t.destroy();
    }

    @Test
    public void findKthPair_d0_k4_1x4() {
        // k=4 => pair (0,3) => (1,1) and (1,4)
        CellPairQuerySegtree t = new CellPairQuerySegtree(1, 4);
        GridSegment[] p4 = t.findKthPairAndReserve(4, 0);
        assertEquals(new GridSegment(1, 1, 1), p4[0]);
        assertEquals(new GridSegment(1, 4, 1), p4[1]);
        t.destroy();
    }

    @Test
    public void findKthPair_after_external_reserve() {
        CellPairQuerySegtree t = new CellPairQuerySegtree(1, 5);
        t.reserve(1, 3);
        assertEquals(3, t.countGoodPairs(1));
        GridSegment[] p1 = t.findKthPairAndReserve(1, 1);
        assertEquals(new GridSegment(1, 1, 1), p1[0]);
        assertEquals(new GridSegment(1, 4, 1), p1[1]);
        t.destroy();
    }

    @Test
    public void findKthPair_after_external_reserve_k2() {
        // Free: {0,1,3,4}. d=1. Valid: (0,3), (0,4), (1,4).
        // k=2 => (0,4) => entrance (1,1), exit (1,5)
        CellPairQuerySegtree t = new CellPairQuerySegtree(1, 5);
        t.reserve(1, 3);
        GridSegment[] p2 = t.findKthPairAndReserve(2, 1);
        assertEquals(new GridSegment(1, 1, 1), p2[0]);
        assertEquals(new GridSegment(1, 5, 1), p2[1]);
        t.destroy();
    }

    @Test
    public void findKthPair_after_external_reserve_k3() {
        // Free: {0,1,3,4}. d=1. Valid: (0,3), (0,4), (1,4).
        // k=3 => (1,4) => entrance (1,2), exit (1,5)
        CellPairQuerySegtree t = new CellPairQuerySegtree(1, 5);
        t.reserve(1, 3);
        GridSegment[] p3 = t.findKthPairAndReserve(3, 1);
        assertEquals(new GridSegment(1, 2, 1), p3[0]);
        assertEquals(new GridSegment(1, 5, 1), p3[1]);
        t.destroy();
    }

    @Test
    public void findKthPair_2d_grid_3x3_d2() {
        // 3x3 grid, 9 cells, all free. d=2.
        // F=9, gap=9-2-1=6, total=6*7/2=21.
        CellPairQuerySegtree t = new CellPairQuerySegtree(3, 3);
        assertEquals(21, t.countGoodPairs(2));
        // k=1 => smallest y with contribution. y with rank 4 (code 3) contributes 1 pair.
        //   x = rank 1 (code 0) => entrance (1,1), exit (2,1)
        GridSegment[] p1 = t.findKthPairAndReserve(1, 2);
        assertEquals(new GridSegment(1, 1, 1), p1[0]);
        assertEquals(new GridSegment(2, 1, 1), p1[1]);
        t.destroy();
    }

    @Test
    public void findKthPair_sequential_pairs_exhaust_grid() {
        // 1x6 grid, d=0. F=6, total=15. Reserve multiple pairs.
        CellPairQuerySegtree t = new CellPairQuerySegtree(1, 6);
        assertEquals(15, t.countGoodPairs(0));

        GridSegment[] p1 = t.findKthPairAndReserve(1, 0); // (0,1)
        assertEquals(6, t.countGoodPairs(0)); // F=4 => 6

        GridSegment[] p2 = t.findKthPairAndReserve(1, 0);
        assertEquals(1, t.countGoodPairs(0)); // F=2 => 1

        GridSegment[] p3 = t.findKthPairAndReserve(1, 0);
        assertEquals(0, t.countGoodPairs(0)); // F=0 => 0

        // All 6 cells reserved in 3 pairs
        // Each pair's entrance code < exit code
        assertTrue(code(p1[0], 6) < code(p1[1], 6));
        assertTrue(code(p2[0], 6) < code(p2[1], 6));
        assertTrue(code(p3[0], 6) < code(p3[1], 6));
        t.destroy();
    }

    // ================================================================
    //  destroy and reuse
    // ================================================================

    @Test
    public void destroy_allows_reuse() {
        CellPairQuerySegtree t1 = new CellPairQuerySegtree(3, 3);
        t1.reserve(1, 1);
        t1.reserve(2, 2);
        t1.destroy();

        CellPairQuerySegtree t2 = new CellPairQuerySegtree(4, 4);
        assertEquals(16L * 15 / 2, t2.countGoodPairs(0));
        t2.destroy();
    }

    // ================================================================
    //  Edge cases
    // ================================================================

    @Test
    public void single_pair_available() {
        CellPairQuerySegtree t = new CellPairQuerySegtree(1, 3);
        assertEquals(1, t.countGoodPairs(1));
        GridSegment[] p = t.findKthPairAndReserve(1, 1);
        assertEquals(new GridSegment(1, 1, 1), p[0]);
        assertEquals(new GridSegment(1, 3, 1), p[1]);
        t.destroy();
    }

    @Test
    public void larger_grid_count_consistency() {
        CellPairQuerySegtree t = new CellPairQuerySegtree(5, 5);
        assertEquals(231, t.countGoodPairs(3));
        t.destroy();
    }

    // ================================================================
    //  Randomized tests — brute-force oracle comparison
    // ================================================================

    /**
     * Brute-force oracle: enumerates all valid pairs on a boolean free-cell array,
     * ordered by (yCode, xCode).
     */
    private static List<int[]> bruteForcePairs(boolean[] free, int d) {
        int N = free.length;
        // prefix[i] = number of free cells in [0..i]
        int[] prefix = new int[N];
        prefix[0] = free[0] ? 1 : 0;
        for (int i = 1; i < N; i++) {
            prefix[i] = prefix[i - 1] + (free[i] ? 1 : 0);
        }
        List<int[]> pairs = new ArrayList<>();
        // Ordered by (y, x)
        for (int y = 0; y < N; y++) {
            if (!free[y]) continue;
            for (int x = 0; x < y; x++) {
                if (!free[x]) continue;
                // free cells strictly between x and y = prefix[y-1] - prefix[x]
                int between = prefix[y - 1] - prefix[x];
                if (between >= d) {
                    pairs.add(new int[]{x, y});
                }
            }
        }
        return pairs;
    }

    private static long bruteForceCount(boolean[] free, int d) {
        return bruteForcePairs(free, d).size();
    }

    @Test
    public void randomized_countGoodPairs_matches_brute_force() {
        Random rng = new Random(42);
        for (int trial = 0; trial < 200; trial++) {
            int nRows = rng.nextInt(5) + 1;
            int nCols = rng.nextInt(5) + 1;
            int N = nRows * nCols;
            int d = rng.nextInt(N + 2); // d can be larger than N

            boolean[] free = new boolean[N];
            CellPairQuerySegtree t = new CellPairQuerySegtree(nRows, nCols);

            // Reserve some random cells
            for (int i = 0; i < N; i++) {
                free[i] = true;
            }
            int numReserve = rng.nextInt(N + 1);
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < N; i++) indices.add(i);
            java.util.Collections.shuffle(indices, rng);
            for (int i = 0; i < numReserve; i++) {
                int code = indices.get(i);
                free[code] = false;
                int row = code / nCols + 1;
                int col = code % nCols + 1;
                t.reserve(row, col);
            }

            long expected = bruteForceCount(free, d);
            long actual = t.countGoodPairs(d);
            assertEquals("Trial " + trial + ": nRows=" + nRows + " nCols=" + nCols
                    + " d=" + d + " reserved=" + numReserve, expected, actual);
            t.destroy();
        }
    }

    @Test
    public void randomized_findKthPair_matches_brute_force() {
        Random rng = new Random(123);
        for (int trial = 0; trial < 200; trial++) {
            int nRows = rng.nextInt(5) + 1;
            int nCols = rng.nextInt(5) + 1;
            int N = nRows * nCols;
            int d = rng.nextInt(Math.max(1, N - 1));

            boolean[] free = new boolean[N];
            CellPairQuerySegtree t = new CellPairQuerySegtree(nRows, nCols);

            for (int i = 0; i < N; i++) free[i] = true;

            // Reserve a few random cells (but not too many so pairs remain)
            int maxReserve = Math.max(0, N - 4);
            int numReserve = maxReserve > 0 ? rng.nextInt(maxReserve) : 0;
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < N; i++) indices.add(i);
            java.util.Collections.shuffle(indices, rng);
            for (int i = 0; i < numReserve; i++) {
                int code = indices.get(i);
                free[code] = false;
                t.reserve(code / nCols + 1, code % nCols + 1);
            }

            List<int[]> expectedPairs = bruteForcePairs(free, d);
            long count = t.countGoodPairs(d);
            assertEquals(expectedPairs.size(), (int) count);

            if (count == 0) {
                t.destroy();
                continue;
            }

            // Pick a random k and verify
            int k = rng.nextInt((int) count) + 1;
            int[] expectedPair = expectedPairs.get(k - 1);
            GridSegment[] actual = t.findKthPairAndReserve(k, d);

            int actualXCode = (actual[0].row - 1) * nCols + (actual[0].col - 1);
            int actualYCode = (actual[1].row - 1) * nCols + (actual[1].col - 1);

            assertEquals("Trial " + trial + ": k=" + k + " d=" + d
                            + " expected x=" + expectedPair[0] + " y=" + expectedPair[1],
                    expectedPair[0], actualXCode);
            assertEquals("Trial " + trial + ": k=" + k + " d=" + d
                            + " expected x=" + expectedPair[0] + " y=" + expectedPair[1],
                    expectedPair[1], actualYCode);

            // Verify both cells are now reserved (count reflects it)
            free[expectedPair[0]] = false;
            free[expectedPair[1]] = false;
            long expectedAfter = bruteForceCount(free, d);
            assertEquals(expectedAfter, t.countGoodPairs(d));

            t.destroy();
        }
    }

    @Test
    public void randomized_multiple_sequential_pairs() {
        // Reserve multiple pairs sequentially and verify each one
        Random rng = new Random(999);
        for (int trial = 0; trial < 50; trial++) {
            int nRows = rng.nextInt(4) + 2;
            int nCols = rng.nextInt(4) + 2;
            int N = nRows * nCols;
            int d = rng.nextInt(Math.max(1, N / 3));

            boolean[] free = new boolean[N];
            CellPairQuerySegtree t = new CellPairQuerySegtree(nRows, nCols);
            for (int i = 0; i < N; i++) free[i] = true;

            int pairsReserved = 0;
            while (true) {
                List<int[]> expectedPairs = bruteForcePairs(free, d);
                long count = t.countGoodPairs(d);
                assertEquals("Trial " + trial + " after " + pairsReserved + " pairs",
                        expectedPairs.size(), (int) count);

                if (count == 0) break;

                int k = rng.nextInt((int) count) + 1;
                int[] expectedPair = expectedPairs.get(k - 1);
                GridSegment[] actual = t.findKthPairAndReserve(k, d);

                int actualXCode = (actual[0].row - 1) * nCols + (actual[0].col - 1);
                int actualYCode = (actual[1].row - 1) * nCols + (actual[1].col - 1);
                assertEquals(expectedPair[0], actualXCode);
                assertEquals(expectedPair[1], actualYCode);

                free[expectedPair[0]] = false;
                free[expectedPair[1]] = false;
                pairsReserved++;

                if (pairsReserved >= 5) break; // cap per trial
            }
            t.destroy();
        }
    }

    @Test
    public void randomized_interleaved_reserves_and_pairs() {
        // Interleave single-cell reserves with pair queries
        Random rng = new Random(7777);
        for (int trial = 0; trial < 50; trial++) {
            int nRows = rng.nextInt(4) + 2;
            int nCols = rng.nextInt(4) + 2;
            int N = nRows * nCols;
            int d = rng.nextInt(Math.max(1, N / 2));

            boolean[] free = new boolean[N];
            CellPairQuerySegtree t = new CellPairQuerySegtree(nRows, nCols);
            for (int i = 0; i < N; i++) free[i] = true;

            List<Integer> freeList = new ArrayList<>();
            for (int i = 0; i < N; i++) freeList.add(i);
            java.util.Collections.shuffle(freeList, rng);
            int freeIdx = 0;

            for (int op = 0; op < 8; op++) {
                if (rng.nextBoolean() && freeIdx < freeList.size()) {
                    // Reserve a single cell
                    int code = freeList.get(freeIdx++);
                    if (free[code]) {
                        free[code] = false;
                        t.reserve(code / nCols + 1, code % nCols + 1);
                    }
                } else {
                    // Try a pair query
                    List<int[]> expected = bruteForcePairs(free, d);
                    long count = t.countGoodPairs(d);
                    assertEquals(expected.size(), (int) count);
                    if (count > 0) {
                        int k = rng.nextInt((int) count) + 1;
                        int[] ep = expected.get(k - 1);
                        GridSegment[] actual = t.findKthPairAndReserve(k, d);
                        assertEquals(ep[0], (actual[0].row - 1) * nCols + (actual[0].col - 1));
                        assertEquals(ep[1], (actual[1].row - 1) * nCols + (actual[1].col - 1));
                        free[ep[0]] = false;
                        free[ep[1]] = false;
                    }
                }
            }
            t.destroy();
        }
    }

    // ================================================================
    //  Helper
    // ================================================================

    private static int code(GridSegment seg, int nCols) {
        return (seg.row - 1) * nCols + (seg.col - 1);
    }
}
