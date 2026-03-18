package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
}

