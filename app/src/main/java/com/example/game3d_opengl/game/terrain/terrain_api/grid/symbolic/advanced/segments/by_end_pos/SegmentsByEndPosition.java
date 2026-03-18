package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.GridBuildScratch;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.GridSegmentSink;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.PartialSegmentHandlerResourcePack;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos.red_black.PooledRedBlackOrderedSegmentSet;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos.treap.PooledTreapOrderedSegmentSet;
import com.example.game3d_opengl.game.terrain.terrain_api.main.GridResourcePack;

import java.util.Arrays;

public class SegmentsByEndPosition {

    private final boolean vertical;
    private final OrderedSegmentSet tree;
    private final int nRows;
    private final int nCols;

    public SegmentsByEndPosition(int nRows, int nCols, boolean vertical) {
        this(
                nRows,
                nCols,
                vertical,
                EndPosTreeKind.POOLED_TREAP,
                GridResourcePack.defaultInstance().partialSegmentHandlerResourcePack()
        );
    }

    public SegmentsByEndPosition(int nRows, int nCols, boolean vertical, EndPosTreeKind treeKind) {
        this(
                nRows,
                nCols,
                vertical,
                treeKind,
                GridResourcePack.defaultInstance().partialSegmentHandlerResourcePack()
        );
    }

    public SegmentsByEndPosition(
            int nRows,
            int nCols,
            boolean vertical,
            EndPosTreeKind treeKind,
            PartialSegmentHandlerResourcePack resourcePack
    ) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.vertical = vertical;
        this.tree = createBackend(
                treeKind,
                vertical,
                nRows,
                nCols,
                resourcePack,
                (GridSegment[]) null
        );
    }

    public static SegmentsByEndPosition fromFreeSegments(
            int nRows, int nCols, boolean vertical, EndPosTreeKind treeKind, GridSegment[] freeSegments
    ) {
        return fromFreeSegments(
                nRows,
                nCols,
                vertical,
                treeKind,
                GridResourcePack.defaultInstance().partialSegmentHandlerResourcePack(),
                freeSegments
        );
    }

    public static SegmentsByEndPosition fromFreeSegments(
            int nRows,
            int nCols,
            boolean vertical,
            EndPosTreeKind treeKind,
            PartialSegmentHandlerResourcePack resourcePack,
            GridSegment[] freeSegments
    ) {
        GridSegment[] sortedSegments = sortSegments(vertical, freeSegments);
        return new SegmentsByEndPosition(
                nRows,
                nCols,
                vertical,
                createBackend(treeKind, vertical, nRows, nCols, resourcePack, sortedSegments)
        );
    }

    public static SegmentsByEndPosition fromScratch(
            int nRows,
            int nCols,
            boolean vertical,
            EndPosTreeKind treeKind,
            PartialSegmentHandlerResourcePack resourcePack,
            GridBuildScratch scratch
    ) {
        if (scratch == null || scratch.size() == 0) {
            return new SegmentsByEndPosition(nRows, nCols, vertical, treeKind, resourcePack);
        }
        scratch.sortByEndPosition(vertical);
        return new SegmentsByEndPosition(
                nRows,
                nCols,
                vertical,
                createBackend(treeKind, vertical, nRows, nCols, resourcePack, scratch)
        );
    }

    private SegmentsByEndPosition(int nRows, int nCols, boolean vertical, OrderedSegmentSet tree) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.vertical = vertical;
        this.tree = tree;
    }

    private static OrderedSegmentSet createBackend(
            EndPosTreeKind treeKind, boolean vertical, int nRows, int nCols) {
        return createBackend(
                treeKind,
                vertical,
                nRows,
                nCols,
                GridResourcePack.defaultInstance().partialSegmentHandlerResourcePack(),
                (GridSegment[]) null
        );
    }

    private static OrderedSegmentSet createBackend(
            EndPosTreeKind treeKind,
            boolean vertical,
            int nRows,
            int nCols,
            PartialSegmentHandlerResourcePack resourcePack,
            GridSegment[] sortedSegments
    ) {
        if (treeKind == null || treeKind == EndPosTreeKind.TREE_SET) {
            OrderedSegmentSet tree = new TreeSetOrderedSegmentSet(vertical);
            populate(tree, sortedSegments);
            return tree;
        }
        if (treeKind == EndPosTreeKind.POOLED_RB_TREE) {
            OrderedSegmentSet tree =
                    new PooledRedBlackOrderedSegmentSet(vertical, nRows, nCols, resourcePack.rbNodePool());
            populate(tree, sortedSegments);
            return tree;
        }
        if (sortedSegments != null) {
            return PooledTreapOrderedSegmentSet.fromSortedSegments(
                    vertical,
                    nRows,
                    nCols,
                    resourcePack.treapNodePool(),
                    sortedSegments
            );
        }
        return new PooledTreapOrderedSegmentSet(vertical, nRows, nCols, resourcePack.treapNodePool());
    }

    private static OrderedSegmentSet createBackend(
            EndPosTreeKind treeKind,
            boolean vertical,
            int nRows,
            int nCols,
            PartialSegmentHandlerResourcePack resourcePack,
            GridBuildScratch scratch
    ) {
        if (treeKind == null || treeKind == EndPosTreeKind.TREE_SET) {
            OrderedSegmentSet tree = new TreeSetOrderedSegmentSet(vertical);
            populate(tree, scratch);
            return tree;
        }
        if (treeKind == EndPosTreeKind.POOLED_RB_TREE) {
            OrderedSegmentSet tree =
                    new PooledRedBlackOrderedSegmentSet(vertical, nRows, nCols, resourcePack.rbNodePool());
            populate(tree, scratch);
            return tree;
        }
        return PooledTreapOrderedSegmentSet.fromScratchSorted(
                vertical,
                nRows,
                nCols,
                resourcePack.treapNodePool(),
                scratch
        );
    }

    private static void populate(OrderedSegmentSet tree, GridSegment[] sortedSegments) {
        if (sortedSegments == null) {
            return;
        }
        for (GridSegment seg : sortedSegments) {
            if (seg == null || seg.length <= 0) {
                continue;
            }
            tree.add(seg);
        }
    }

    private static void populate(OrderedSegmentSet tree, GridBuildScratch scratch) {
        for (int i = 0; i < scratch.size(); ++i) {
            tree.add(scratch.rowAt(i), scratch.colAt(i), scratch.lengthAt(i));
        }
    }

    private static GridSegment[] sortSegments(boolean vertical, GridSegment[] freeSegments) {
        if (freeSegments == null || freeSegments.length == 0) {
            return new GridSegment[0];
        }
        GridSegment[] sorted = freeSegments.clone();
        Arrays.sort(sorted, (a, b) -> {
            int aPrimary = vertical ? a.col : a.row;
            int bPrimary = vertical ? b.col : b.row;
            if (aPrimary != bPrimary) {
                return Integer.compare(aPrimary, bPrimary);
            }
            int aEnd = vertical ? a.row + a.length : a.col + a.length;
            int bEnd = vertical ? b.row + b.length : b.col + b.length;
            return Integer.compare(aEnd, bEnd);
        });
        return sorted;
    }

    public GridSegment[] reserve(int row, int col, int length) {
        assert row <= nRows;
        assert row >= 1;
        assert col <= nCols;
        assert col >= 1;
        GridSegment candidate = bestFit(row, col);
        if (candidate == null) {
            throw new IllegalArgumentException("No space available for this segment");
        }
        int cStart = vertical ? candidate.row : candidate.col;
        int start = vertical ? row : col;
        int cLength = candidate.length;
        int cOther = vertical ? candidate.col : candidate.row;
        int other = vertical ? col : row;
        if (cStart > start || cOther != other || cStart + cLength - 1 < start + length - 1) {
            throw new IllegalArgumentException("No space available for this segment");
        }
        tree.remove(candidate);

        if (cStart == start) {
            int newLength = cLength - length;
            if (newLength != 0) {
                int newStart = cStart + length;
                GridSegment replacement = vertical
                        ? GridSegment.GS(newStart, other, newLength)
                        : GridSegment.GS(other, newStart, newLength);
                tree.add(replacement);
                return new GridSegment[]{candidate, replacement, null};
            }
            return new GridSegment[]{candidate, null, null};
        } else {
            int len1 = start - cStart;
            GridSegment replacement1 = null;
            GridSegment replacement2 = null;
            if (len1 > 0) {
                replacement1 = vertical
                        ? GridSegment.GS(cStart, cOther, len1)
                        : GridSegment.GS(cOther, cStart, len1);
                tree.add(replacement1);
            }
            int len2 = cStart + cLength - 1 - (start + length - 1);
            if (len2 > 0) {
                int newStart = start + length;
                replacement2 = vertical
                        ? GridSegment.GS(newStart, cOther, len2)
                        : GridSegment.GS(cOther, newStart, len2);
                tree.add(replacement2);
            }
            return new GridSegment[]{candidate, replacement1, replacement2};
        }
    }

    public void insert(int row, int col, int length) {
        tree.add(row, col, length);
    }

    public boolean isEmpty() {
        return tree.isEmpty();
    }

    public GridSegment pollFirst() {
        return tree.pollFirst();
    }

    public GridSegment[] toSortedArray() {
        return tree.toSortedArray();
    }

    public void forEachSorted(GridSegmentSink sink) {
        tree.forEachSorted(sink);
    }

    private GridSegment bestFit(int row, int col) {
        GridSegment dummy = new GridSegment(row, col, 1);
        GridSegment candidate = tree.ceiling(dummy);
        if (candidate == null) {
            return null;
        }
        if (vertical) {
            if (candidate.col == col
                    && candidate.row <= row
                    && row <= candidate.row + candidate.length - 1) {
                return candidate;
            }
        } else {
            if (candidate.row == row
                    && candidate.col <= col
                    && col <= candidate.col + candidate.length - 1) {
                return candidate;
            }
        }
        return null;
    }

    public void printGrid() {
        char[][] grid = new char[nRows][nCols];
        System.out.println("Rows: " + nRows + " | Cols: " + nCols + " " + hashCode() + "," + vertical);
        for (int r = 0; r < nRows; r++) {
            for (int c = 0; c < nCols; c++) {
                grid[r][c] = '#';
            }
        }
        GridSegment[] all = toSortedArray();
        for (GridSegment seg : all) {
            int row = seg.row - 1;
            int col = seg.col - 1;
            int len = seg.length;
            for (int i = 0; i < len; ++i) {
                if (vertical) {
                    grid[row + i][col] = '.';
                } else {
                    grid[row][col + i] = '.';
                }
            }
        }
        for (int r = 0; r < nRows; r++) {
            System.out.println(r + " " + Arrays.toString(grid[r]));
        }
    }
}

