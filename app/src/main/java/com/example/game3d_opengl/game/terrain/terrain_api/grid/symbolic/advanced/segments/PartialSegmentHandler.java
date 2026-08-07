package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.RetainedGridSummary;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.AdvancedGridCreator;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos.EndPosTreeKind;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos.SegmentsByEndPosition;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_length.SegmentsByLength;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_length.treap.PooledTreapSegmentsByLength;
import com.example.game3d_opengl.game.terrain.terrain_api.main.GridResourcePack;
import com.example.game3d_opengl.game.util.GameRandom;

public class PartialSegmentHandler {


    private final int nRows, nCols;
    private final SegmentsByLength segmentsByLength;
    private final SegmentsByEndPosition segmentsByEndPosition;

    private PartialSegmentHandler(
            int nRows,
            int nCols,
            SegmentsByLength segmentsByLength,
            SegmentsByEndPosition segmentsByEndPosition
    ) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.segmentsByLength = segmentsByLength;
        this.segmentsByEndPosition = segmentsByEndPosition;
    }

    public PartialSegmentHandler(int nRows, int nCols, boolean vertical) {
        this(
                nRows,
                nCols,
                vertical,
                EndPosTreeKind.POOLED_TREAP,
                GridResourcePack.defaultInstance().partialSegmentHandlerResourcePack(),
                null,
                null
        );
    }

    public PartialSegmentHandler(int nRows, int nCols, boolean vertical, EndPosTreeKind endPosTreeKind) {
        this(
                nRows,
                nCols,
                vertical,
                endPosTreeKind,
                GridResourcePack.defaultInstance().partialSegmentHandlerResourcePack(),
                null,
                null
        );
    }

    public PartialSegmentHandler(
            int nRows, int nCols, boolean vertical,
            EndPosTreeKind endPosTreeKind, boolean[] blockedRows
    ) {
        this(
                nRows,
                nCols,
                vertical,
                endPosTreeKind,
                GridResourcePack.defaultInstance().partialSegmentHandlerResourcePack(),
                blockedRows,
                null
        );
    }

    public PartialSegmentHandler(
            int nRows, int nCols, boolean vertical,
            EndPosTreeKind endPosTreeKind,
            PartialSegmentHandlerResourcePack resourcePack,
            boolean[] blockedRows,
            GridSegment[] initialFreeSegments
    ) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.segmentsByLength = initialFreeSegments != null
                ? PooledTreapSegmentsByLength.fromFreeSegments(
                        nRows, nCols, vertical, resourcePack, initialFreeSegments
                )
                : new PooledTreapSegmentsByLength(nRows, nCols, vertical, resourcePack);
        this.segmentsByEndPosition = initialFreeSegments != null
                ? SegmentsByEndPosition.fromFreeSegments(
                        nRows, nCols, vertical, endPosTreeKind, resourcePack, initialFreeSegments
                )
                : new SegmentsByEndPosition(nRows, nCols, vertical, endPosTreeKind, resourcePack);

        if (initialFreeSegments != null) {
            return;
        } else if(vertical) {
            initializeVerticalSegments(blockedRows);
        }else{
            initializeHorizontalSegments(blockedRows);
        }
    }

    public static PartialSegmentHandler fromFreeSegments(
            int nRows, int nCols, boolean vertical,
            EndPosTreeKind endPosTreeKind, GridSegment[] freeSegments
    ) {
        return fromFreeSegments(
                nRows,
                nCols,
                vertical,
                endPosTreeKind,
                GridResourcePack.defaultInstance().partialSegmentHandlerResourcePack(),
                freeSegments
        );
    }

    public static PartialSegmentHandler fromFreeSegments(
            int nRows,
            int nCols,
            boolean vertical,
            EndPosTreeKind endPosTreeKind,
            PartialSegmentHandlerResourcePack resourcePack,
            GridSegment[] freeSegments
    ) {
        return new PartialSegmentHandler(nRows, nCols, vertical, endPosTreeKind, resourcePack, null, freeSegments);
    }

    public static PartialSegmentHandler fromChildren(
            int nRows,
            int nCols,
            boolean vertical,
            EndPosTreeKind endPosTreeKind,
            int[][] blockedRowsRanges,
            RetainedGridSummary[] childCreators,
            int[] childRowOffsets,
            int childCount
    ) {
        return fromChildren(
                nRows,
                nCols,
                vertical,
                endPosTreeKind,
                GridResourcePack.defaultInstance().partialSegmentHandlerResourcePack(),
                blockedRowsRanges,
                childCreators,
                childRowOffsets,
                childCount
        );
    }

    public static PartialSegmentHandler fromChildren(
            int nRows,
            int nCols,
            boolean vertical,
            EndPosTreeKind endPosTreeKind,
            PartialSegmentHandlerResourcePack resourcePack,
            int[][] blockedRowsRanges,
            RetainedGridSummary[] childCreators,
            int[] childRowOffsets,
            int childCount
    ) {
        boolean[] blockedRows = buildBlockedRowsMap(nRows, blockedRowsRanges);
        GridBuildScratch scratch = resourcePack.gridBuildScratch();
        scratch.clear();
        try {
            if (vertical) {
                fillVerticalFreeSegmentsFromChildren(
                        scratch,
                        nRows,
                        nCols,
                        blockedRows,
                        blockedRowsRanges,
                        childCreators,
                        childRowOffsets,
                        childCount
                );
            } else {
                fillHorizontalFreeSegmentsFromChildren(
                        scratch,
                        nRows,
                        nCols,
                        blockedRows,
                        childCreators,
                        childRowOffsets,
                        childCount
                );
            }
            SegmentsByEndPosition byEndPosition = SegmentsByEndPosition.fromEndSortedScratch(
                    nRows,
                    nCols,
                    vertical,
                    endPosTreeKind,
                    resourcePack,
                    scratch
            );
            scratch.sortByLengthThenPosition();
            SegmentsByLength byLength = PooledTreapSegmentsByLength.fromLengthSortedScratch(
                    nRows,
                    nCols,
                    vertical,
                    resourcePack,
                    scratch
            );
            return new PartialSegmentHandler(nRows, nCols, byLength, byEndPosition);
        } finally {
            scratch.clear();
        }
    }

    public static PartialSegmentHandler buildHorizontalFromChildren(
            int nRows,
            int nCols,
            EndPosTreeKind endPosTreeKind,
            PartialSegmentHandlerResourcePack resourcePack,
            int[][] blockedRowsRanges,
            RetainedGridSummary[] childCreators,
            int[] childRowOffsets,
            int childCount
    ) {
        return fromChildren(
                nRows,
                nCols,
                false,
                endPosTreeKind,
                resourcePack,
                blockedRowsRanges,
                childCreators,
                childRowOffsets,
                childCount
        );
    }

    public static PartialSegmentHandler buildVerticalFromChildren(
            int nRows,
            int nCols,
            EndPosTreeKind endPosTreeKind,
            PartialSegmentHandlerResourcePack resourcePack,
            int[][] blockedRowsRanges,
            RetainedGridSummary[] childCreators,
            int[] childRowOffsets,
            int childCount
    ) {
        return fromChildren(
                nRows,
                nCols,
                true,
                endPosTreeKind,
                resourcePack,
                blockedRowsRanges,
                childCreators,
                childRowOffsets,
                childCount
        );
    }

    public static PartialSegmentHandler fromChildWrappers(
            int nRows,
            int nCols,
            boolean vertical,
            EndPosTreeKind endPosTreeKind,
            int[][] blockedRowsRanges,
            GridCreatorWrapper[] childWrappers,
            int[] childRowOffsets,
            int childCount
    ) {
        return fromChildren(
                nRows,
                nCols,
                vertical,
                endPosTreeKind,
                blockedRowsRanges,
                extractChildCreators(childWrappers, childCount),
                childRowOffsets,
                childCount
        );
    }

    public static PartialSegmentHandler fromChildWrappers(
            int nRows,
            int nCols,
            boolean vertical,
            EndPosTreeKind endPosTreeKind,
            PartialSegmentHandlerResourcePack resourcePack,
            int[][] blockedRowsRanges,
            GridCreatorWrapper[] childWrappers,
            int[] childRowOffsets,
            int childCount
    ) {
        return fromChildren(
                nRows,
                nCols,
                vertical,
                endPosTreeKind,
                resourcePack,
                blockedRowsRanges,
                extractChildCreators(childWrappers, childCount),
                childRowOffsets,
                childCount
        );
    }

    private void initializeHorizontalSegments(boolean[] blockedRows) {
        for (int row = 1; row <= nRows; ++row) {
            if (isRowBlocked(blockedRows, row)) {
                continue;
            }
            this.segmentsByLength.insert(row, 1, nCols);
            this.segmentsByEndPosition.insert(row, 1, nCols);
        }
    }

    private void initializeVerticalSegments(boolean[] blockedRows) {
        for (int col = 1; col <= nCols; ++col) {
            int runStart = -1;
            for (int row = 1; row <= nRows + 1; ++row) {
                boolean blocked = row == nRows + 1 || isRowBlocked(blockedRows, row);
                if (!blocked && runStart == -1) {
                    runStart = row;
                    continue;
                }
                if (blocked && runStart != -1) {
                    int len = row - runStart;
                    this.segmentsByLength.insert(runStart, col, len);
                    this.segmentsByEndPosition.insert(runStart, col, len);
                    runStart = -1;
                }
            }
        }
    }

    private static boolean isRowBlocked(boolean[] blockedRows, int row) {
        return blockedRows != null && row >= 1 && row < blockedRows.length && blockedRows[row];
    }

    private static boolean[] buildBlockedRowsMap(int nRows, int[][] blockedRowsRanges) {
        boolean[] blockedRows = new boolean[nRows + 1];
        if (blockedRowsRanges == null) {
            return blockedRows;
        }
        for (int[] range : blockedRowsRanges) {
            if (range == null || range.length < 2) {
                continue;
            }
            int start = Math.max(1, Math.min(range[0], range[1]));
            int end = Math.min(nRows, Math.max(range[0], range[1]));
            if (start > end) {
                continue;
            }
            for (int row = start; row <= end; ++row) {
                blockedRows[row] = true;
            }
        }
        return blockedRows;
    }

    private static void fillHorizontalFreeSegmentsFromChildren(
            GridBuildScratch scratch,
            int nRows,
            int nCols,
            boolean[] blockedRows,
            RetainedGridSummary[] childCreators,
            int[] childRowOffsets,
            int childCount
    ) {
        int nextParentRow = 1;
        for (int i = 0; i < childCount; ++i) {
            RetainedGridSummary child = getChild(childCreators, i);
            if (child == null) {
                continue;
            }
            int rowOffset = childRowOffsets[i];
            validateChildPlacement(nRows, nCols, child, rowOffset);
            int childStartRow = rowOffset + 1;
            int childEndRow = rowOffset + child.getRowCount();
            if (childStartRow < nextParentRow) {
                throw new IllegalStateException("Overlapping or out-of-order propagated child rows.");
            }
            appendPlainHorizontalRows(scratch, nextParentRow, childStartRow - 1, nCols, blockedRows);
            child.forEachHorizontalFreeSegment((row, col, length) -> {
                int parentRow = rowOffset + row;
                if (blockedRows[parentRow]) {
                    return;
                }
                scratch.add(parentRow, col, length);
            });
            nextParentRow = childEndRow + 1;
        }
        appendPlainHorizontalRows(scratch, nextParentRow, nRows, nCols, blockedRows);
    }

    private static void fillVerticalFreeSegmentsFromChildren(
            GridBuildScratch scratch,
            int nRows,
            int nCols,
            boolean[] blockedRows,
            int[][] blockedRowsRanges,
            RetainedGridSummary[] childCreators,
            int[] childRowOffsets,
            int childCount
    ) {
        for (int col = 1; col <= nCols; ++col) {
            VerticalRunWriter writer = new VerticalRunWriter(scratch, col);
            int nextParentRow = 1;
            for (int i = 0; i < childCount; ++i) {
                RetainedGridSummary child = getChild(childCreators, i);
                if (child == null) {
                    continue;
                }
                int rowOffset = childRowOffsets[i];
                validateChildPlacement(nRows, nCols, child, rowOffset);
                int childStartRow = rowOffset + 1;
                int childEndRow = rowOffset + child.getRowCount();
                if (childStartRow < nextParentRow) {
                    throw new IllegalStateException("Overlapping or out-of-order propagated child rows.");
                }

                appendClippedVerticalRange(
                        writer,
                        nextParentRow,
                        childStartRow - 1,
                        nRows,
                        blockedRowsRanges
                );

                final int expectedCol = col;
                child.forEachVerticalFreeSegment((row, childCol, length) -> {
                    if (childCol != expectedCol) {
                        return;
                    }
                    int startRow = rowOffset + row;
                    int endRow = startRow + length - 1;
                    appendClippedVerticalRange(writer, startRow, endRow, nRows, blockedRowsRanges);
                });

                nextParentRow = childEndRow + 1;
            }
            appendClippedVerticalRange(writer, nextParentRow, nRows, nRows, blockedRowsRanges);
            writer.flush();
        }
    }

    private static void appendPlainHorizontalRows(
            GridBuildScratch scratch,
            int fromRow,
            int toRow,
            int nCols,
            boolean[] blockedRows
    ) {
        if (fromRow > toRow) {
            return;
        }
        for (int row = fromRow; row <= toRow; ++row) {
            if (!isRowBlocked(blockedRows, row)) {
                scratch.add(row, 1, nCols);
            }
        }
    }

    private static RetainedGridSummary[] extractChildCreators(GridCreatorWrapper[] childWrappers, int childCount) {
        if (childCount <= 0) {
            return new RetainedGridSummary[0];
        }
        RetainedGridSummary[] childCreators = new RetainedGridSummary[childCount];
        for (int i = 0; i < childCount; ++i) {
            childCreators[i] = childWrappers != null && i < childWrappers.length && childWrappers[i] != null
                    ? childWrappers[i].getRetainedSummary()
                    : null;
        }
        return childCreators;
    }

    private static RetainedGridSummary getChild(RetainedGridSummary[] childCreators, int index) {
        if (childCreators == null || index < 0 || index >= childCreators.length) {
            return null;
        }
        return childCreators[index];
    }

    private static void validateChildPlacement(int nRows, int nCols, RetainedGridSummary child, int rowOffset) {
        if (child.getColCount() != nCols) {
            throw new IllegalArgumentException("Child column count does not match parent column count.");
        }
        if (rowOffset < 0 || rowOffset + child.getRowCount() > nRows) {
            throw new IllegalArgumentException("Child rows do not fit inside parent row range.");
        }
    }

    private static void appendClippedVerticalRange(
            VerticalRunWriter out,
            int startRow,
            int endRow,
            int nRows,
            int[][] blockedRowsRanges
    ) {
        int clippedStart = Math.max(1, startRow);
        int clippedEnd = Math.min(nRows, endRow);
        if (clippedStart > clippedEnd) {
            return;
        }

        int curr = clippedStart;
        if (blockedRowsRanges != null) {
            for (int[] range : blockedRowsRanges) {
                if (range == null || range.length < 2) {
                    continue;
                }
                int blockedStart = Math.max(1, Math.min(range[0], range[1]));
                int blockedEnd = Math.min(nRows, Math.max(range[0], range[1]));
                if (blockedEnd < curr) {
                    continue;
                }
                if (blockedStart > clippedEnd) {
                    break;
                }
                if (curr < blockedStart) {
                    out.append(curr, blockedStart - 1);
                }
                curr = Math.max(curr, blockedEnd + 1);
                if (curr > clippedEnd) {
                    return;
                }
            }
        }

        out.append(curr, clippedEnd);
    }

    public void reserve(int row, int col, int length) {
        assert row <= nRows;
        assert row >= 1;
        assert col <= nCols;
        assert col >= 1;
        GridSegment[] reserve = segmentsByEndPosition.reserve(row, col, length);
        segmentsByLength.delete(reserve[0].row, reserve[0].col, reserve[0].length);
        if (reserve[1] != null) {
            segmentsByLength.insert(reserve[1].row, reserve[1].col, reserve[1].length);
        }
        if (reserve[2] != null) {
            segmentsByLength.insert(reserve[2].row, reserve[2].col, reserve[2].length);
        }
    }

    public GridSegment reserveRandomFitting(int length) {
        int total = segmentsByLength.countFittingSpaces(length);
        int k = GameRandom.randInt(1,total);
        GridSegment found = segmentsByLength.getKthFittingSpace(length,k);
        reserve(found.row, found.col, length);
        return found;
    }

    public void printGrid(){
        segmentsByEndPosition.printGrid();
    }

    public GridSegment[] exportFreeSegments() {
        return segmentsByEndPosition.toSortedArray();
    }

    public void forEachFreeSegment(GridSegmentSink sink) {
        segmentsByEndPosition.forEachSorted(sink);
    }

    public void appendMaximalFreeSegments(GridBuildScratch out) {
        if (out == null) {
            return;
        }
        segmentsByEndPosition.forEachSorted(out::add);
    }

    public void destroy(){
        while(!segmentsByEndPosition.isEmpty()){
            GridSegment curr = segmentsByEndPosition.pollFirst();
            if (curr == null) {
                break;
            }
            segmentsByLength.delete(curr.row,curr.col,curr.length);
        }
        segmentsByLength.destroy();
    }

    private static final class VerticalRunWriter {
        private final GridBuildScratch out;
        private final int col;
        private int runStart = -1;
        private int runEnd = -1;

        private VerticalRunWriter(GridBuildScratch out, int col) {
            this.out = out;
            this.col = col;
        }

        private void append(int startRow, int endRow) {
            if (startRow > endRow) {
                return;
            }
            if (runStart == -1) {
                runStart = startRow;
                runEnd = endRow;
                return;
            }
            if (startRow <= runEnd + 1) {
                runEnd = Math.max(runEnd, endRow);
                return;
            }
            flush();
            runStart = startRow;
            runEnd = endRow;
        }

        private void flush() {
            if (runStart == -1) {
                return;
            }
            out.add(runStart, col, runEnd - runStart + 1);
            runStart = -1;
            runEnd = -1;
        }
    }
}
