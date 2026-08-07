package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class SegmentsByEndPositionTest {

    private static SegmentsByEndPosition initialized(
            int nRows, int nCols, boolean vertical, EndPosTreeKind kind) {
        SegmentsByEndPosition s = new SegmentsByEndPosition(nRows, nCols, vertical, kind);
        if (vertical) {
            for (int col = 1; col <= nCols; ++col) {
                s.insert(1, col, nRows);
            }
        } else {
            for (int row = 1; row <= nRows; ++row) {
                s.insert(row, 1, nCols);
            }
        }
        return s;
    }

    @Test
    public void reserve_horizontal_exact_at_start_removes_segment() {
        for (EndPosTreeKind kind : EndPosTreeKind.values()) {
            SegmentsByEndPosition s = new SegmentsByEndPosition(3, 10, false, kind);
            s.insert(2, 1, 5); // row=2, cols 1..5

            GridSegment[] res = s.reserve(2, 1, 5);
            assertNotNull(kind.name(), res[0]);
            assertEquals(kind.name(), new GridSegment(2, 1, 5), res[0]);
            assertNull(kind.name(), res[1]);
            assertNull(kind.name(), res[2]);
        }
    }

    @Test
    public void reserve_horizontal_from_start_leaves_right_remainder() {
        for (EndPosTreeKind kind : EndPosTreeKind.values()) {
            SegmentsByEndPosition s = new SegmentsByEndPosition(3, 10, false, kind);
            s.insert(2, 1, 7); // row=2, cols 1..7

            GridSegment[] res = s.reserve(2, 1, 4); // reserve cols 1..4
            assertEquals(kind.name(), new GridSegment(2, 1, 7), res[0]);
            assertEquals(kind.name(), new GridSegment(2, 5, 3), res[1]); // remaining cols 5..7
            assertNull(kind.name(), res[2]);
        }
    }

    @Test
    public void reserve_horizontal_in_middle_leaves_two_remainders() {
        for (EndPosTreeKind kind : EndPosTreeKind.values()) {
            SegmentsByEndPosition s = new SegmentsByEndPosition(3, 15, false, kind);
            s.insert(1, 3, 10); // row=1, cols 3..12

            GridSegment[] res = s.reserve(1, 6, 4); // reserve cols 6..9
            assertEquals(kind.name(), new GridSegment(1, 3, 10), res[0]);
            assertEquals(kind.name(), new GridSegment(1, 3, 3), res[1]); // 3..5
            assertEquals(kind.name(), new GridSegment(1, 10, 3), res[2]); // 10..12
        }
    }

    @Test
    public void reserve_vertical_exact_at_start_removes_segment() {
        for (EndPosTreeKind kind : EndPosTreeKind.values()) {
            SegmentsByEndPosition s = new SegmentsByEndPosition(10, 3, true, kind);
            s.insert(1, 2, 5); // col=2, rows 1..5

            GridSegment[] res = s.reserve(1, 2, 5);
            assertEquals(kind.name(), new GridSegment(1, 2, 5), res[0]);
            assertNull(kind.name(), res[1]);
            assertNull(kind.name(), res[2]);
        }
    }

    @Test
    public void reserve_vertical_from_middle_leaves_two_remainders() {
        for (EndPosTreeKind kind : EndPosTreeKind.values()) {
            SegmentsByEndPosition s = new SegmentsByEndPosition(20, 5, true, kind);
            s.insert(3, 4, 10); // col=4, rows 3..12

            GridSegment[] res = s.reserve(7, 4, 4); // rows 7..10
            assertEquals(kind.name(), new GridSegment(3, 4, 10), res[0]);
            assertEquals(kind.name(), new GridSegment(3, 4, 4), res[1]); // 3..6
            assertEquals(kind.name(), new GridSegment(11, 4, 2), res[2]); // 11..12
        }
    }

    @Test
    public void randomized_fixed_seed_backends_match_treeset() {
        Random rng = new Random(1234567L);
        for (int trial = 0; trial < 20; ++trial) {
            boolean vertical = (trial & 1) == 0;
            int nRows = 4 + rng.nextInt(8);
            int nCols = 4 + rng.nextInt(8);
            int total = nRows * nCols;
            int[] order = new int[total];
            for (int i = 0; i < total; ++i) order[i] = i;
            for (int i = total - 1; i > 0; --i) {
                int j = rng.nextInt(i + 1);
                int tmp = order[i];
                order[i] = order[j];
                order[j] = tmp;
            }

            SegmentsByEndPosition baseline = initialized(
                    nRows, nCols, vertical, EndPosTreeKind.TREE_SET);
            SegmentsByEndPosition rb = initialized(
                    nRows, nCols, vertical, EndPosTreeKind.POOLED_RB_TREE);
            SegmentsByEndPosition treap = initialized(
                    nRows, nCols, vertical, EndPosTreeKind.POOLED_TREAP);

            int ops = Math.min(total, 30);
            for (int k = 0; k < ops; ++k) {
                int code = order[k];
                int row = code / nCols + 1;
                int col = code % nCols + 1;

                GridSegment[] b = baseline.reserve(row, col, 1);
                GridSegment[] r = rb.reserve(row, col, 1);
                GridSegment[] t = treap.reserve(row, col, 1);

                assertEquals("reserved seg trial=" + trial + " op=" + k, b[0], r[0]);
                assertEquals("reserved seg trial=" + trial + " op=" + k, b[0], t[0]);
                assertEquals("left remainder trial=" + trial + " op=" + k, b[1], r[1]);
                assertEquals("left remainder trial=" + trial + " op=" + k, b[1], t[1]);
                assertEquals("right remainder trial=" + trial + " op=" + k, b[2], r[2]);
                assertEquals("right remainder trial=" + trial + " op=" + k, b[2], t[2]);
            }

            assertEquals(baseline.isEmpty(), rb.isEmpty());
            assertEquals(baseline.isEmpty(), treap.isEmpty());
        }
    }

    @Test
    public void bulk_built_treap_matches_inserted_treap_for_horizontal_segments() {
        GridSegment[] segments = new GridSegment[]{
                new GridSegment(4, 3, 4),
                new GridSegment(2, 1, 2),
                new GridSegment(2, 5, 3),
                new GridSegment(1, 2, 5)
        };
        GridSegment[] shuffled = segments.clone();
        for (int i = 0; i < shuffled.length / 2; ++i) {
            GridSegment tmp = shuffled[i];
            shuffled[i] = shuffled[shuffled.length - 1 - i];
            shuffled[shuffled.length - 1 - i] = tmp;
        }

        SegmentsByEndPosition inserted =
                new SegmentsByEndPosition(5, 8, false, EndPosTreeKind.POOLED_TREAP);
        for (GridSegment seg : segments) {
            inserted.insert(seg.row, seg.col, seg.length);
        }

        SegmentsByEndPosition bulkBuilt = SegmentsByEndPosition.fromFreeSegments(
                5, 8, false, EndPosTreeKind.POOLED_TREAP, shuffled
        );

        assertArrayEquals(inserted.toSortedArray(), bulkBuilt.toSortedArray());

        GridSegment[] insertedReserve = inserted.reserve(1, 4, 2);
        GridSegment[] bulkReserve = bulkBuilt.reserve(1, 4, 2);
        assertArrayEquals(insertedReserve, bulkReserve);
        assertArrayEquals(inserted.toSortedArray(), bulkBuilt.toSortedArray());
    }

    @Test
    public void bulk_built_treap_matches_inserted_treap_for_vertical_segments() {
        GridSegment[] segments = new GridSegment[]{
                new GridSegment(5, 1, 3),
                new GridSegment(1, 2, 4),
                new GridSegment(3, 4, 5),
                new GridSegment(2, 3, 2)
        };
        GridSegment[] shuffled = segments.clone();
        Arrays.sort(shuffled, (a, b) -> Integer.compare(b.length, a.length));

        SegmentsByEndPosition inserted =
                new SegmentsByEndPosition(8, 4, true, EndPosTreeKind.POOLED_TREAP);
        for (GridSegment seg : segments) {
            inserted.insert(seg.row, seg.col, seg.length);
        }

        SegmentsByEndPosition bulkBuilt = SegmentsByEndPosition.fromFreeSegments(
                8, 4, true, EndPosTreeKind.POOLED_TREAP, shuffled
        );

        assertArrayEquals(inserted.toSortedArray(), bulkBuilt.toSortedArray());

        GridSegment[] insertedReserve = inserted.reserve(4, 4, 2);
        GridSegment[] bulkReserve = bulkBuilt.reserve(4, 4, 2);
        assertArrayEquals(insertedReserve, bulkReserve);
        assertArrayEquals(inserted.toSortedArray(), bulkBuilt.toSortedArray());
    }

    @Test
    public void reserve_failures_leave_state_unchanged() {
        for (EndPosTreeKind kind : EndPosTreeKind.values()) {
            SegmentsByEndPosition horizontal = new SegmentsByEndPosition(4, 8, false, kind);
            horizontal.insert(2, 3, 4);
            GridSegment[] beforeHorizontal = horizontal.toSortedArray();
            assertReserveFails(horizontal, 2, 2, 1, kind.name() + " horizontal wrong start");
            assertArrayEquals(beforeHorizontal, horizontal.toSortedArray());
            assertReserveFails(horizontal, 1, 3, 2, kind.name() + " horizontal wrong row");
            assertArrayEquals(beforeHorizontal, horizontal.toSortedArray());
            assertReserveFails(horizontal, 2, 5, 3, kind.name() + " horizontal overrun");
            assertArrayEquals(beforeHorizontal, horizontal.toSortedArray());

            SegmentsByEndPosition vertical = new SegmentsByEndPosition(8, 4, true, kind);
            vertical.insert(2, 3, 4);
            GridSegment[] beforeVertical = vertical.toSortedArray();
            assertReserveFails(vertical, 1, 3, 1, kind.name() + " vertical wrong start");
            assertArrayEquals(beforeVertical, vertical.toSortedArray());
            assertReserveFails(vertical, 2, 2, 2, kind.name() + " vertical wrong col");
            assertArrayEquals(beforeVertical, vertical.toSortedArray());
            assertReserveFails(vertical, 4, 3, 4, kind.name() + " vertical overrun");
            assertArrayEquals(beforeVertical, vertical.toSortedArray());
        }
    }

    @Test
    public void randomized_reserve_matches_bruteforce_grid_for_all_backends() {
        Random rng = new Random(20260328L);
        for (int trial = 0; trial < 18; ++trial) {
            boolean vertical = (trial & 1) == 0;
            int nRows = 3 + rng.nextInt(6);
            int nCols = 3 + rng.nextInt(5);
            boolean[][] free = createFullyFreeGrid(nRows, nCols);
            SegmentsByEndPosition[] implementations = new SegmentsByEndPosition[EndPosTreeKind.values().length];
            for (int i = 0; i < EndPosTreeKind.values().length; ++i) {
                implementations[i] = initialized(nRows, nCols, vertical, EndPosTreeKind.values()[i]);
            }

            for (int op = 0; op < 45; ++op) {
                int row = 1 + rng.nextInt(nRows);
                int col = 1 + rng.nextInt(nCols);
                int maxLen = vertical ? (nRows - row + 1) : (nCols - col + 1);
                int length = 1 + rng.nextInt(Math.max(1, maxLen));

                boolean canReserve = canReserve(free, vertical, nRows, nCols, row, col, length);
                GridSegment containing = containingSegment(free, vertical, nRows, nCols, row, col);
                GridSegment[] expectedResult = canReserve
                        ? expectedReserveResult(containing, vertical, row, col, length)
                        : null;

                for (SegmentsByEndPosition implementation : implementations) {
                    if (canReserve) {
                        GridSegment[] actual = implementation.reserve(row, col, length);
                        assertArrayEquals(
                                "trial=" + trial + " op=" + op + " vertical=" + vertical,
                                expectedResult,
                                actual
                        );
                    } else {
                        assertReserveFails(
                                implementation,
                                row,
                                col,
                                length,
                                "trial=" + trial + " op=" + op + " vertical=" + vertical
                        );
                    }
                }

                if (canReserve) {
                    applyReserve(free, vertical, row, col, length);
                }
                GridSegment[] expectedSegments = extractSegments(free, vertical, nRows, nCols);
                for (SegmentsByEndPosition implementation : implementations) {
                    assertArrayEquals(
                            "state mismatch trial=" + trial + " op=" + op + " vertical=" + vertical,
                            expectedSegments,
                            implementation.toSortedArray()
                    );
                }
            }
        }
    }

    private static void assertReserveFails(
            SegmentsByEndPosition segments, int row, int col, int length, String message) {
        try {
            segments.reserve(row, col, length);
            fail(message);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static boolean[][] createFullyFreeGrid(int nRows, int nCols) {
        boolean[][] free = new boolean[nRows + 1][nCols + 1];
        for (int row = 1; row <= nRows; ++row) {
            for (int col = 1; col <= nCols; ++col) {
                free[row][col] = true;
            }
        }
        return free;
    }

    private static boolean canReserve(
            boolean[][] free,
            boolean vertical,
            int nRows,
            int nCols,
            int row,
            int col,
            int length
    ) {
        if (length <= 0 || row < 1 || col < 1 || row > nRows || col > nCols) {
            return false;
        }
        if (vertical) {
            if (row + length - 1 > nRows) {
                return false;
            }
            for (int r = row; r < row + length; ++r) {
                if (!free[r][col]) {
                    return false;
                }
            }
        } else {
            if (col + length - 1 > nCols) {
                return false;
            }
            for (int c = col; c < col + length; ++c) {
                if (!free[row][c]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static GridSegment containingSegment(
            boolean[][] free, boolean vertical, int nRows, int nCols, int row, int col) {
        if (!free[row][col]) {
            return null;
        }
        if (vertical) {
            int start = row;
            while (start > 1 && free[start - 1][col]) {
                start--;
            }
            int end = row;
            while (end < nRows && free[end + 1][col]) {
                end++;
            }
            return new GridSegment(start, col, end - start + 1);
        }
        int start = col;
        while (start > 1 && free[row][start - 1]) {
            start--;
        }
        int end = col;
        while (end < nCols && free[row][end + 1]) {
            end++;
        }
        return new GridSegment(row, start, end - start + 1);
    }

    private static GridSegment[] expectedReserveResult(
            GridSegment candidate, boolean vertical, int row, int col, int length) {
        int start = vertical ? row : col;
        int other = vertical ? col : row;
        int candidateStart = vertical ? candidate.row : candidate.col;
        int candidateEnd = candidateStart + candidate.length - 1;

        if (candidateStart == start) {
            int rightLength = candidate.length - length;
            if (rightLength <= 0) {
                return new GridSegment[]{candidate, null, null};
            }
            GridSegment right = vertical
                    ? GridSegment.GS(start + length, other, rightLength)
                    : GridSegment.GS(other, start + length, rightLength);
            return new GridSegment[]{candidate, right, null};
        }

        int leftLength = start - candidateStart;
        GridSegment left = vertical
                ? GridSegment.GS(candidateStart, other, leftLength)
                : GridSegment.GS(other, candidateStart, leftLength);
        int rightLength = candidateEnd - (start + length - 1);
        GridSegment right = rightLength > 0
                ? (vertical
                    ? GridSegment.GS(start + length, other, rightLength)
                    : GridSegment.GS(other, start + length, rightLength))
                : null;
        return new GridSegment[]{candidate, left, right};
    }

    private static void applyReserve(boolean[][] free, boolean vertical, int row, int col, int length) {
        if (vertical) {
            for (int r = row; r < row + length; ++r) {
                free[r][col] = false;
            }
        } else {
            for (int c = col; c < col + length; ++c) {
                free[row][c] = false;
            }
        }
    }

    private static GridSegment[] extractSegments(boolean[][] free, boolean vertical, int nRows, int nCols) {
        List<GridSegment> segments = new ArrayList<>();
        if (vertical) {
            for (int col = 1; col <= nCols; ++col) {
                int row = 1;
                while (row <= nRows) {
                    while (row <= nRows && !free[row][col]) {
                        row++;
                    }
                    if (row > nRows) {
                        break;
                    }
                    int start = row;
                    while (row <= nRows && free[row][col]) {
                        row++;
                    }
                    segments.add(new GridSegment(start, col, row - start));
                }
            }
        } else {
            for (int row = 1; row <= nRows; ++row) {
                int col = 1;
                while (col <= nCols) {
                    while (col <= nCols && !free[row][col]) {
                        col++;
                    }
                    if (col > nCols) {
                        break;
                    }
                    int start = col;
                    while (col <= nCols && free[row][col]) {
                        col++;
                    }
                    segments.add(new GridSegment(row, start, col - start));
                }
            }
        }
        return segments.toArray(new GridSegment[0]);
    }
}

