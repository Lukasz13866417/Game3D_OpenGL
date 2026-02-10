package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

public class AdvancedGridCreatorTest {

    // ================================================================
    //  Helper: brute-force pair counting on a boolean[] free array
    // ================================================================

    private static long bruteForcePortalPairCount(boolean[] free, int d) {
        int N = free.length;
        int[] prefix = new int[N];
        prefix[0] = free[0] ? 1 : 0;
        for (int i = 1; i < N; i++) {
            prefix[i] = prefix[i - 1] + (free[i] ? 1 : 0);
        }
        long count = 0;
        for (int y = 0; y < N; y++) {
            if (!free[y]) continue;
            for (int x = 0; x < y; x++) {
                if (!free[x]) continue;
                int between = prefix[y - 1] - prefix[x];
                if (between >= d) count++;
            }
        }
        return count;
    }

    private static int code(int row, int col, int nCols) {
        return (row - 1) * nCols + (col - 1);
    }

    private static void markReserved(boolean[] free, int row, int col, int nCols) {
        free[code(row, col, nCols)] = false;
    }

    // ================================================================
    //  Basic portal pair operations
    // ================================================================

    @Test
    public void countPortalPairs_fresh_grid() {
        AdvancedGridCreator g = new AdvancedGridCreator(5, 5);
        // F=25, d=0 => 25*24/2 = 300
        assertEquals(300, g.countPortalPairs(0));
        // d=3 => gap=25-3-1=21 => 21*22/2 = 231
        assertEquals(231, g.countPortalPairs(3));
        g.destroy();
    }

    @Test
    public void reserveRandomPortalPair_returns_valid_pair() {
        AdvancedGridCreator g = new AdvancedGridCreator(6, 6);
        int d = 3;
        long before = g.countPortalPairs(d);
        assertTrue(before > 0);

        GridSegment[] pair = g.reserveRandomPortalPair(d);
        GridSegment entrance = pair[0];
        GridSegment exit = pair[1];

        // Both are single cells
        assertEquals(1, entrance.length);
        assertEquals(1, exit.length);

        // Exit has a larger row-major code than entrance
        int entranceCode = code(entrance.row, entrance.col, 6);
        int exitCode = code(exit.row, exit.col, 6);
        assertTrue("exit code must be > entrance code", exitCode > entranceCode);

        // Bounds check
        assertTrue(entrance.row >= 1 && entrance.row <= 6);
        assertTrue(entrance.col >= 1 && entrance.col <= 6);
        assertTrue(exit.row >= 1 && exit.row <= 6);
        assertTrue(exit.col >= 1 && exit.col <= 6);

        // Count must have decreased (we removed 2 free cells)
        long after = g.countPortalPairs(d);
        assertTrue("pair count should decrease after reserving", after < before);

        g.destroy();
    }

    @Test(expected = IllegalArgumentException.class)
    public void reserveRandomPortalPair_throws_when_no_pairs() {
        // 1x2 grid, d=1 => gap=2-1-1=0 => no pairs
        AdvancedGridCreator g = new AdvancedGridCreator(1, 2);
        try {
            g.reserveRandomPortalPair(1);
        } finally {
            g.destroy();
        }
    }

    // ================================================================
    //  Portal pairs after vertical/horizontal reserves
    // ================================================================

    @Test
    public void countPortalPairs_decreases_after_reserveVertical() {
        AdvancedGridCreator g = new AdvancedGridCreator(5, 5);
        int d = 2;
        long count1 = g.countPortalPairs(d);

        g.reserveVertical(1, 1, 3); // reserves (1,1), (2,1), (3,1)
        long count2 = g.countPortalPairs(d);
        assertTrue("count should decrease after vertical reserve", count2 < count1);

        g.destroy();
    }

    @Test
    public void countPortalPairs_decreases_after_reserveHorizontal() {
        AdvancedGridCreator g = new AdvancedGridCreator(5, 5);
        int d = 2;
        long count1 = g.countPortalPairs(d);

        g.reserveHorizontal(2, 1, 4); // reserves (2,1), (2,2), (2,3), (2,4)
        long count2 = g.countPortalPairs(d);
        assertTrue("count should decrease after horizontal reserve", count2 < count1);

        g.destroy();
    }

    @Test
    public void countPortalPairs_matches_brute_force_after_reserves() {
        int nRows = 4, nCols = 5;
        int N = nRows * nCols;
        boolean[] free = new boolean[N];
        for (int i = 0; i < N; i++) free[i] = true;

        AdvancedGridCreator g = new AdvancedGridCreator(nRows, nCols);

        // Reserve a vertical segment
        g.reserveVertical(1, 2, 3);
        for (int r = 1; r <= 3; r++) markReserved(free, r, 2, nCols);

        // Reserve a horizontal segment
        g.reserveHorizontal(4, 1, 4);
        for (int c = 1; c <= 4; c++) markReserved(free, 4, c, nCols);

        for (int d = 0; d <= 10; d++) {
            assertEquals("d=" + d, bruteForcePortalPairCount(free, d), g.countPortalPairs(d));
        }

        g.destroy();
    }

    // ================================================================
    //  Portal pair after reserveRandomFitting*
    // ================================================================

    @Test
    public void portal_pair_after_random_vertical_reserves() {
        int nRows = 8, nCols = 6;
        int N = nRows * nCols;
        boolean[] free = new boolean[N];
        for (int i = 0; i < N; i++) free[i] = true;

        AdvancedGridCreator g = new AdvancedGridCreator(nRows, nCols);

        // Reserve some random single cells
        for (int i = 0; i < 10; i++) {
            GridSegment seg = g.reserveRandomFittingVertical(1);
            markReserved(free, seg.row, seg.col, nCols);
        }

        int d = 3;
        assertEquals(bruteForcePortalPairCount(free, d), g.countPortalPairs(d));

        // Should still be able to reserve a portal pair
        if (g.countPortalPairs(d) > 0) {
            GridSegment[] pair = g.reserveRandomPortalPair(d);
            markReserved(free, pair[0].row, pair[0].col, nCols);
            markReserved(free, pair[1].row, pair[1].col, nCols);
            assertEquals(bruteForcePortalPairCount(free, d), g.countPortalPairs(d));
        }

        g.destroy();
    }

    @Test
    public void portal_pair_after_random_horizontal_reserves() {
        int nRows = 6, nCols = 8;
        int N = nRows * nCols;
        boolean[] free = new boolean[N];
        for (int i = 0; i < N; i++) free[i] = true;

        AdvancedGridCreator g = new AdvancedGridCreator(nRows, nCols);

        for (int i = 0; i < 5; i++) {
            GridSegment seg = g.reserveRandomFittingHorizontal(2);
            for (int c = seg.col; c < seg.col + seg.length; c++) {
                markReserved(free, seg.row, c, nCols);
            }
        }

        int d = 2;
        assertEquals(bruteForcePortalPairCount(free, d), g.countPortalPairs(d));

        if (g.countPortalPairs(d) > 0) {
            GridSegment[] pair = g.reserveRandomPortalPair(d);
            markReserved(free, pair[0].row, pair[0].col, nCols);
            markReserved(free, pair[1].row, pair[1].col, nCols);
            assertEquals(bruteForcePortalPairCount(free, d), g.countPortalPairs(d));
        }

        g.destroy();
    }

    @Test
    public void portal_pair_after_reserveKRandomFields() {
        int nRows = 7, nCols = 5;
        int N = nRows * nCols;
        boolean[] free = new boolean[N];
        for (int i = 0; i < N; i++) free[i] = true;

        AdvancedGridCreator g = new AdvancedGridCreator(nRows, nCols);

        GridSegment[] fields = g.reserveKRandomFields(8);
        for (GridSegment seg : fields) {
            markReserved(free, seg.row, seg.col, nCols);
        }

        int d = 4;
        assertEquals(bruteForcePortalPairCount(free, d), g.countPortalPairs(d));

        if (g.countPortalPairs(d) > 0) {
            GridSegment[] pair = g.reserveRandomPortalPair(d);
            markReserved(free, pair[0].row, pair[0].col, nCols);
            markReserved(free, pair[1].row, pair[1].col, nCols);
            assertEquals(bruteForcePortalPairCount(free, d), g.countPortalPairs(d));
        }

        g.destroy();
    }

    // ================================================================
    //  Portal pair cells are blocked in vertical/horizontal handlers
    //  (subsequent random reserves never pick the same cells)
    // ================================================================

    @Test
    public void portal_pair_cells_blocked_for_future_reserves() {
        int nRows = 6, nCols = 6;
        AdvancedGridCreator g = new AdvancedGridCreator(nRows, nCols);

        GridSegment[] pair = g.reserveRandomPortalPair(2);
        Set<String> portalCells = new HashSet<>();
        portalCells.add(pair[0].row + "," + pair[0].col);
        portalCells.add(pair[1].row + "," + pair[1].col);

        // Reserve all remaining cells one by one; none should be a portal cell
        int remaining = nRows * nCols - 2;
        for (int i = 0; i < remaining; i++) {
            GridSegment seg = g.reserveRandomFittingVertical(1);
            String key = seg.row + "," + seg.col;
            assertFalse("Portal cell " + key + " was reused", portalCells.contains(key));
        }

        g.destroy();
    }

    // ================================================================
    //  Parent propagation
    // ================================================================

    @Test
    public void portal_pair_propagates_to_parent() {
        int parentRows = 20, parentCols = 5;
        AdvancedGridCreator parent = new AdvancedGridCreator(parentRows, parentCols);
        long parentCountBefore = parent.countPortalPairs(0);

        GridCreatorWrapper wrapper = new GridCreatorWrapper();
        wrapper.content = parent;

        int childRows = 6, childCols = 5, offset = 3;
        AdvancedGridCreator child = new AdvancedGridCreator(childRows, childCols, wrapper, offset);

        GridSegment[] pair = child.reserveRandomPortalPair(2);

        // Parent's pair count should have decreased (2 cells were propagated)
        long parentCountAfter = parent.countPortalPairs(0);
        assertTrue("parent count should decrease after child portal pair",
                parentCountAfter < parentCountBefore);

        child.destroy();
        parent.destroy();
    }

    // ================================================================
    //  Multiple portal pairs
    // ================================================================

    @Test
    public void multiple_portal_pairs_on_same_grid() {
        int nRows = 10, nCols = 10;
        int N = nRows * nCols;
        boolean[] free = new boolean[N];
        for (int i = 0; i < N; i++) free[i] = true;

        AdvancedGridCreator g = new AdvancedGridCreator(nRows, nCols);

        int d = 5;
        Set<String> allPortalCells = new HashSet<>();

        for (int p = 0; p < 5; p++) {
            long count = g.countPortalPairs(d);
            assertEquals("Pair " + p, bruteForcePortalPairCount(free, d), count);
            if (count == 0) break;

            GridSegment[] pair = g.reserveRandomPortalPair(d);

            // Verify no cell reuse
            String k0 = pair[0].row + "," + pair[0].col;
            String k1 = pair[1].row + "," + pair[1].col;
            assertFalse("Entrance cell reused: " + k0, allPortalCells.contains(k0));
            assertFalse("Exit cell reused: " + k1, allPortalCells.contains(k1));
            assertNotEquals("Entrance and exit must differ", k0, k1);
            allPortalCells.add(k0);
            allPortalCells.add(k1);

            // Exit code > entrance code
            assertTrue(code(pair[1].row, pair[1].col, nCols)
                    > code(pair[0].row, pair[0].col, nCols));

            markReserved(free, pair[0].row, pair[0].col, nCols);
            markReserved(free, pair[1].row, pair[1].col, nCols);
        }

        g.destroy();
    }

    // ================================================================
    //  Randomized: mixed operations with brute-force oracle
    // ================================================================

    @Test
    public void randomized_mixed_operations_match_brute_force() {
        Random rng = new Random(314159);
        for (int trial = 0; trial < 30; trial++) {
            int nRows = rng.nextInt(5) + 3;  // 3..7
            int nCols = rng.nextInt(5) + 3;  // 3..7
            int N = nRows * nCols;
            int d = rng.nextInt(Math.max(1, N / 3));

            boolean[] free = new boolean[N];
            for (int i = 0; i < N; i++) free[i] = true;
            AdvancedGridCreator g = new AdvancedGridCreator(nRows, nCols);

            for (int op = 0; op < 12; op++) {
                int choice = rng.nextInt(5);
                switch (choice) {
                    case 0: { // reserveVertical single cell
                        try {
                            GridSegment seg = g.reserveRandomFittingVertical(1);
                            markReserved(free, seg.row, seg.col, nCols);
                        } catch (Exception ignored) {
                            // grid might be exhausted
                        }
                        break;
                    }
                    case 1: { // reserveHorizontal single cell
                        try {
                            GridSegment seg = g.reserveRandomFittingHorizontal(1);
                            markReserved(free, seg.row, seg.col, nCols);
                        } catch (Exception ignored) {
                            // grid might be exhausted
                        }
                        break;
                    }
                    case 2: { // reserveVertical multi-cell (length 1-3)
                        try {
                            int len = rng.nextInt(2) + 1;
                            GridSegment seg = g.reserveRandomFittingVertical(len);
                            for (int r = seg.row; r < seg.row + len; r++) {
                                markReserved(free, r, seg.col, nCols);
                            }
                        } catch (Exception ignored) {
                            // might fail if no space
                        }
                        break;
                    }
                    case 3: { // reserveHorizontal multi-cell (length 1-3)
                        try {
                            int len = rng.nextInt(2) + 1;
                            GridSegment seg = g.reserveRandomFittingHorizontal(len);
                            for (int c = seg.col; c < seg.col + len; c++) {
                                markReserved(free, seg.row, c, nCols);
                            }
                        } catch (Exception ignored) {
                            // might fail if no space
                        }
                        break;
                    }
                    case 4: { // reserveRandomPortalPair
                        long pairCount = g.countPortalPairs(d);
                        assertEquals("Trial " + trial + " op " + op,
                                bruteForcePortalPairCount(free, d), pairCount);
                        if (pairCount > 0) {
                            GridSegment[] pair = g.reserveRandomPortalPair(d);
                            markReserved(free, pair[0].row, pair[0].col, nCols);
                            markReserved(free, pair[1].row, pair[1].col, nCols);
                            // Verify ordering
                            assertTrue(code(pair[1].row, pair[1].col, nCols)
                                    > code(pair[0].row, pair[0].col, nCols));
                        }
                        break;
                    }
                }

                // After every operation, verify countPortalPairs is consistent
                assertEquals("Trial " + trial + " op " + op + " post-check",
                        bruteForcePortalPairCount(free, d), g.countPortalPairs(d));
            }

            g.destroy();
        }
    }

    @Test
    public void randomized_portal_pairs_then_exhaust_grid() {
        // Reserve some portal pairs, then fill remaining grid with single reserves.
        // No cell should ever be double-reserved.
        Random rng = new Random(271828);
        for (int trial = 0; trial < 20; trial++) {
            int nRows = rng.nextInt(4) + 3;
            int nCols = rng.nextInt(4) + 3;
            int N = nRows * nCols;
            int d = rng.nextInt(Math.max(1, N / 4));

            AdvancedGridCreator g = new AdvancedGridCreator(nRows, nCols);
            Set<String> reserved = new HashSet<>();

            // Reserve some portal pairs
            int portalPairs = 0;
            while (g.countPortalPairs(d) > 0 && portalPairs < 3) {
                GridSegment[] pair = g.reserveRandomPortalPair(d);
                for (GridSegment seg : pair) {
                    String key = seg.row + "," + seg.col;
                    assertFalse("Double reserve: " + key + " trial " + trial,
                            reserved.contains(key));
                    reserved.add(key);
                }
                portalPairs++;
            }

            // Fill the rest with single vertical reserves
            while (reserved.size() < N) {
                GridSegment seg = g.reserveRandomFittingVertical(1);
                String key = seg.row + "," + seg.col;
                assertFalse("Double reserve: " + key + " trial " + trial,
                        reserved.contains(key));
                reserved.add(key);
            }

            assertEquals(N, reserved.size());
            g.destroy();
        }
    }
}
