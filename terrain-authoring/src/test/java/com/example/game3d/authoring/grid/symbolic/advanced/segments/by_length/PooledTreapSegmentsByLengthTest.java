package com.example.game3d.authoring.grid.symbolic.advanced.segments.by_length;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.game3d.authoring.grid.symbolic.GridSegment;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.by_length.treap.PooledTreapSegmentsByLength;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class PooledTreapSegmentsByLengthTest {

    @Test
    public void count_and_kth_horizontal_segments() {
        PooledTreapSegmentsByLength segments = new PooledTreapSegmentsByLength(5, 7, false);
        segments.insert(2, 1, 5);
        segments.insert(2, 6, 2);
        segments.insert(4, 2, 3);

        assertEquals(4, segments.countFittingSpaces(3));

        GridSegment k1 = segments.getKthFittingSpace(3, 1);
        assertEquals(new GridSegment(4, 2, 3), k1);
        GridSegment k2 = segments.getKthFittingSpace(3, 2);
        assertEquals(new GridSegment(2, 1, 3), k2);
        GridSegment k3 = segments.getKthFittingSpace(3, 3);
        assertEquals(new GridSegment(2, 2, 3), k3);
        GridSegment k4 = segments.getKthFittingSpace(3, 4);
        assertEquals(new GridSegment(2, 3, 3), k4);
    }

    @Test
    public void count_and_kth_vertical_segments() {
        PooledTreapSegmentsByLength segments = new PooledTreapSegmentsByLength(8, 4, true);
        segments.insert(1, 1, 4);
        segments.insert(3, 3, 6);

        assertEquals(2, segments.countFittingSpaces(5));

        GridSegment v1 = segments.getKthFittingSpace(5, 1);
        assertEquals(new GridSegment(3, 3, 5), v1);
        GridSegment v2 = segments.getKthFittingSpace(5, 2);
        assertEquals(new GridSegment(4, 3, 5), v2);
    }

    @Test
    public void insert_delete_affects_counts() {
        PooledTreapSegmentsByLength segments = new PooledTreapSegmentsByLength(6, 6, false);
        segments.insert(1, 1, 3);
        segments.insert(1, 5, 2);
        segments.insert(2, 2, 4);
        assertEquals(3, segments.countFittingSpaces(3));

        segments.delete(1, 1, 3);
        assertEquals(2, segments.countFittingSpaces(3));

        segments.delete(2, 2, 4);
        assertEquals(0, segments.countFittingSpaces(3));
    }

    @Test
    public void destroy_allows_reuse_without_crash() {
        PooledTreapSegmentsByLength first = new PooledTreapSegmentsByLength(4, 4, false);
        first.insert(1, 1, 4);
        first.insert(2, 1, 3);
        assertTrue(first.countFittingSpaces(2) > 0);
        first.destroy();

        PooledTreapSegmentsByLength second = new PooledTreapSegmentsByLength(4, 4, true);
        second.insert(1, 1, 4);
        assertEquals(3, second.countFittingSpaces(2));
    }

    @Test
    public void bulk_build_matches_insert_build_for_horizontal_segments() {
        GridSegment[] segments = new GridSegment[]{
                new GridSegment(4, 2, 3),
                new GridSegment(2, 6, 2),
                new GridSegment(2, 1, 5),
                new GridSegment(5, 1, 7)
        };

        PooledTreapSegmentsByLength inserted = new PooledTreapSegmentsByLength(5, 7, false);
        for (GridSegment seg : segments) {
            inserted.insert(seg.row, seg.col, seg.length);
        }

        PooledTreapSegmentsByLength bulkBuilt =
                PooledTreapSegmentsByLength.fromFreeSegments(5, 7, false, segments);

        for (int size = 1; size <= 7; ++size) {
            assertEquals(inserted.countFittingSpaces(size), bulkBuilt.countFittingSpaces(size));
            int total = inserted.countFittingSpaces(size);
            for (int k = 1; k <= total; ++k) {
                assertEquals(inserted.getKthFittingSpace(size, k), bulkBuilt.getKthFittingSpace(size, k));
            }
        }

        inserted.destroy();
        bulkBuilt.destroy();
    }

    @Test
    public void bulk_build_matches_insert_build_for_vertical_segments() {
        GridSegment[] segments = new GridSegment[]{
                new GridSegment(6, 2, 3),
                new GridSegment(1, 1, 4),
                new GridSegment(3, 3, 6),
                new GridSegment(2, 4, 2)
        };

        PooledTreapSegmentsByLength inserted = new PooledTreapSegmentsByLength(8, 4, true);
        for (GridSegment seg : segments) {
            inserted.insert(seg.row, seg.col, seg.length);
        }

        PooledTreapSegmentsByLength bulkBuilt =
                PooledTreapSegmentsByLength.fromFreeSegments(8, 4, true, segments);

        for (int size = 1; size <= 8; ++size) {
            assertEquals(inserted.countFittingSpaces(size), bulkBuilt.countFittingSpaces(size));
            int total = inserted.countFittingSpaces(size);
            for (int k = 1; k <= total; ++k) {
                assertEquals(inserted.getKthFittingSpace(size, k), bulkBuilt.getKthFittingSpace(size, k));
            }
        }

        inserted.destroy();
        bulkBuilt.destroy();
    }

    @Test
    public void bulk_build_handles_parent_style_rebased_segments() {
        GridSegment[] childAInParent = new GridSegment[]{
                new GridSegment(2, 1, 4),
                new GridSegment(3, 5, 2)
        };
        GridSegment[] childBInParent = new GridSegment[]{
                new GridSegment(7, 2, 3),
                new GridSegment(8, 1, 5)
        };
        GridSegment[] parentOnly = new GridSegment[]{
                new GridSegment(1, 1, 5),
                new GridSegment(6, 1, 5)
        };

        GridSegment[] all = new GridSegment[childAInParent.length + childBInParent.length + parentOnly.length];
        System.arraycopy(childAInParent, 0, all, 0, childAInParent.length);
        System.arraycopy(childBInParent, 0, all, childAInParent.length, childBInParent.length);
        System.arraycopy(parentOnly, 0, all, childAInParent.length + childBInParent.length, parentOnly.length);

        GridSegment[] shuffled = all.clone();
        Arrays.sort(shuffled, (a, b) -> Integer.compare(b.length, a.length));

        PooledTreapSegmentsByLength inserted = new PooledTreapSegmentsByLength(8, 5, false);
        for (GridSegment seg : all) {
            inserted.insert(seg.row, seg.col, seg.length);
        }

        PooledTreapSegmentsByLength bulkBuilt =
                PooledTreapSegmentsByLength.fromFreeSegments(8, 5, false, shuffled);

        for (int size = 1; size <= 5; ++size) {
            assertEquals(inserted.countFittingSpaces(size), bulkBuilt.countFittingSpaces(size));
            int total = inserted.countFittingSpaces(size);
            for (int k = 1; k <= total; ++k) {
                assertEquals(inserted.getKthFittingSpace(size, k), bulkBuilt.getKthFittingSpace(size, k));
            }
        }

        inserted.destroy();
        bulkBuilt.destroy();
    }

    @Test
    public void invalid_arguments_and_duplicate_operations_behave_consistently() {
        PooledTreapSegmentsByLength segments = new PooledTreapSegmentsByLength(6, 6, false);
        segments.insert(2, 2, 4);
        segments.insert(2, 2, 4); // duplicate should be ignored

        assertEquals(2, segments.countFittingSpaces(3));

        assertIllegalArgumentCount(segments, 0);
        assertIllegalArgumentKth(segments, 0, 1);
        assertIllegalArgumentKth(segments, 3, 0);
        assertIllegalArgumentKth(segments, 5, 1);

        segments.delete(5, 5, 2); // deleting missing entry should be a no-op
        assertEquals(2, segments.countFittingSpaces(3));
    }

    @Test
    public void randomized_operations_match_bruteforce_model() {
        Random rng = new Random(20260328L);
        for (int trial = 0; trial < 16; ++trial) {
            boolean vertical = (trial & 1) == 0;
            int totalRows = 4 + rng.nextInt(4);
            int nCols = 4 + rng.nextInt(4);
            PooledTreapSegmentsByLength actual =
                    new PooledTreapSegmentsByLength(totalRows, nCols, vertical);
            BruteForceLengthModel baseline = new BruteForceLengthModel(vertical);

            for (int op = 0; op < 50; ++op) {
                GridSegment segment = randomValidSegment(rng, totalRows, nCols, vertical);
                if (rng.nextBoolean()) {
                    actual.insert(segment.row, segment.col, segment.length);
                    baseline.insert(segment);
                } else {
                    actual.delete(segment.row, segment.col, segment.length);
                    baseline.delete(segment);
                }

                int maxSpaceSize = vertical ? totalRows : nCols;
                for (int spaceSize = 1; spaceSize <= maxSpaceSize; ++spaceSize) {
                    int expectedCount = baseline.countFittingSpaces(spaceSize);
                    assertEquals(
                            "count mismatch trial=" + trial + " op=" + op + " size=" + spaceSize,
                            expectedCount,
                            actual.countFittingSpaces(spaceSize)
                    );
                    for (int k = 1; k <= expectedCount; ++k) {
                        assertEquals(
                                "kth mismatch trial=" + trial + " op=" + op + " size=" + spaceSize + " k=" + k,
                                baseline.getKthFittingSpace(spaceSize, k),
                                actual.getKthFittingSpace(spaceSize, k)
                        );
                    }
                }
            }

            actual.destroy();
        }
    }

    private static void assertIllegalArgumentCount(PooledTreapSegmentsByLength segments, int spaceSize) {
        try {
            segments.countFittingSpaces(spaceSize);
            fail("Expected IllegalArgumentException for spaceSize=" + spaceSize);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void assertIllegalArgumentKth(
            PooledTreapSegmentsByLength segments, int spaceSize, int k) {
        try {
            segments.getKthFittingSpace(spaceSize, k);
            fail("Expected IllegalArgumentException for spaceSize=" + spaceSize + " k=" + k);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static GridSegment randomValidSegment(Random rng, int totalRows, int nCols, boolean vertical) {
        if (vertical) {
            int col = 1 + rng.nextInt(nCols);
            int row = 1 + rng.nextInt(totalRows);
            int length = 1 + rng.nextInt(totalRows - row + 1);
            return new GridSegment(row, col, length);
        }
        int row = 1 + rng.nextInt(totalRows);
        int col = 1 + rng.nextInt(nCols);
        int length = 1 + rng.nextInt(nCols - col + 1);
        return new GridSegment(row, col, length);
    }

    private static int compareSegments(GridSegment a, GridSegment b) {
        if (a.length != b.length) {
            return Integer.compare(a.length, b.length);
        }
        if (a.row != b.row) {
            return Integer.compare(a.row, b.row);
        }
        return Integer.compare(a.col, b.col);
    }

    private static final class BruteForceLengthModel {
        private final boolean vertical;
        private final List<GridSegment> segments = new ArrayList<>();

        private BruteForceLengthModel(boolean vertical) {
            this.vertical = vertical;
        }

        private void insert(GridSegment segment) {
            if (segment.length <= 0 || segments.contains(segment)) {
                return;
            }
            segments.add(segment);
        }

        private void delete(GridSegment segment) {
            segments.remove(segment);
        }

        private int countFittingSpaces(int spaceSize) {
            if (spaceSize <= 0) {
                throw new IllegalArgumentException("spaceSize must be > 0");
            }
            int total = 0;
            for (GridSegment segment : segments) {
                if (segment.length >= spaceSize) {
                    total += segment.length - spaceSize + 1;
                }
            }
            return total;
        }

        private GridSegment getKthFittingSpace(int spaceSize, int k) {
            if (spaceSize <= 0) {
                throw new IllegalArgumentException("spaceSize must be > 0");
            }
            if (k <= 0) {
                throw new IllegalArgumentException("k must be > 0");
            }
            List<GridSegment> orderedSegments = new ArrayList<>(segments);
            orderedSegments.sort(PooledTreapSegmentsByLengthTest::compareSegments);

            List<GridSegment> spaces = new ArrayList<>();
            for (GridSegment segment : orderedSegments) {
                if (segment.length < spaceSize) {
                    continue;
                }
                int count = segment.length - spaceSize + 1;
                for (int offset = 0; offset < count; ++offset) {
                    spaces.add(vertical
                            ? GridSegment.GS(segment.row + offset, segment.col, spaceSize)
                            : GridSegment.GS(segment.row, segment.col + offset, spaceSize));
                }
            }
            if (k > spaces.size()) {
                throw new IllegalArgumentException("k exceeds number of fitting spaces");
            }
            return spaces.get(k - 1);
        }
    }
}
