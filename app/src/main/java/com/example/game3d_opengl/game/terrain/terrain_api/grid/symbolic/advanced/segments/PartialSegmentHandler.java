package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.AdvancedGridCreator;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos.EndPosTreeKind;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos.SegmentsByEndPosition;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_length.SegmentsByLength;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_length.segtree_implementation.PreallocatedHashedSegmentsByLengthNodes;
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
                ? PreallocatedHashedSegmentsByLengthNodes.fromFreeSegments(
                        nRows, nCols, vertical, resourcePack, initialFreeSegments
                )
                : new PreallocatedHashedSegmentsByLengthNodes(nRows, nCols, vertical, resourcePack);
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

    public static PartialSegmentHandler fromScratch(
            int nRows,
            int nCols,
            boolean vertical,
            EndPosTreeKind endPosTreeKind,
            PartialSegmentHandlerResourcePack resourcePack,
            GridBuildScratch scratch
    ) {
        SegmentsByEndPosition byEndPosition = SegmentsByEndPosition.fromScratch(
                nRows,
                nCols,
                vertical,
                endPosTreeKind,
                resourcePack,
                scratch
        );
        SegmentsByLength byLength = PreallocatedHashedSegmentsByLengthNodes.fromScratch(
                nRows,
                nCols,
                vertical,
                resourcePack,
                scratch
        );
        return new PartialSegmentHandler(nRows, nCols, byLength, byEndPosition);
    }

    public static PartialSegmentHandler fromChildren(
            int nRows,
            int nCols,
            boolean vertical,
            EndPosTreeKind endPosTreeKind,
            int[][] blockedRowsRanges,
            AdvancedGridCreator[] childCreators,
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
            AdvancedGridCreator[] childCreators,
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
            return fromScratch(nRows, nCols, vertical, endPosTreeKind, resourcePack, scratch);
        } finally {
            scratch.clear();
        }
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
            AdvancedGridCreator[] childCreators,
            int[] childRowOffsets,
            int childCount
    ) {
        boolean[] coveredRows = new boolean[nRows + 1];

        for (int i = 0; i < childCount; ++i) {
            AdvancedGridCreator child = getChild(childCreators, i);
            if (child == null) {
                continue;
            }
            int rowOffset = childRowOffsets[i];
            validateChildPlacement(nRows, nCols, child, rowOffset);
            for (int childRow = 1; childRow <= child.nRows; ++childRow) {
                int parentRow = rowOffset + childRow;
                if (coveredRows[parentRow]) {
                    throw new IllegalStateException("Overlapping propagated child rows in PartialSegmentHandler.");
                }
                coveredRows[parentRow] = true;
            }
            child.forEachHorizontalFreeSegment((row, col, length) -> {
                int parentRow = rowOffset + row;
                if (blockedRows[parentRow]) {
                    return;
                }
                scratch.add(parentRow, col, length);
            });
        }

        for (int row = 1; row <= nRows; ++row) {
            if (blockedRows[row] || coveredRows[row]) {
                continue;
            }
            scratch.add(row, 1, nCols);
        }
    }

    private static void fillVerticalFreeSegmentsFromChildren(
            GridBuildScratch scratch,
            int nRows,
            int nCols,
            boolean[] blockedRows,
            int[][] blockedRowsRanges,
            AdvancedGridCreator[] childCreators,
            int[] childRowOffsets,
            int childCount
    ) {
        boolean[] coveredRows = new boolean[nRows + 1];

        for (int i = 0; i < childCount; ++i) {
            AdvancedGridCreator child = getChild(childCreators, i);
            if (child == null) {
                continue;
            }
            int rowOffset = childRowOffsets[i];
            validateChildPlacement(nRows, nCols, child, rowOffset);
            for (int childRow = 1; childRow <= child.nRows; ++childRow) {
                int parentRow = rowOffset + childRow;
                if (coveredRows[parentRow]) {
                    throw new IllegalStateException("Overlapping propagated child rows in PartialSegmentHandler.");
                }
                coveredRows[parentRow] = true;
            }
            child.forEachVerticalFreeSegment((row, col, length) -> {
                int startRow = rowOffset + row;
                int endRow = startRow + length - 1;
                addClippedVerticalSegments(scratch, col, startRow, endRow, nRows, blockedRowsRanges);
            });
        }

        int runStart = -1;
        for (int row = 1; row <= nRows + 1; ++row) {
            boolean plainFree = row <= nRows && !blockedRows[row] && !coveredRows[row];
            if (plainFree && runStart == -1) {
                runStart = row;
                continue;
            }
            if (!plainFree && runStart != -1) {
                int len = row - runStart;
                for (int col = 1; col <= nCols; ++col) {
                    scratch.add(runStart, col, len);
                }
                runStart = -1;
            }
        }
        scratch.mergeVerticalIntervalsInPlace();
    }

    private static AdvancedGridCreator[] extractChildCreators(GridCreatorWrapper[] childWrappers, int childCount) {
        if (childCount <= 0) {
            return new AdvancedGridCreator[0];
        }
        AdvancedGridCreator[] childCreators = new AdvancedGridCreator[childCount];
        for (int i = 0; i < childCount; ++i) {
            childCreators[i] = childWrappers != null && i < childWrappers.length && childWrappers[i] != null
                    ? childWrappers[i].getRetainedAdvancedCreator()
                    : null;
        }
        return childCreators;
    }

    private static AdvancedGridCreator getChild(AdvancedGridCreator[] childCreators, int index) {
        if (childCreators == null || index < 0 || index >= childCreators.length) {
            return null;
        }
        return childCreators[index];
    }

    private static void validateChildPlacement(int nRows, int nCols, AdvancedGridCreator child, int rowOffset) {
        if (child.nCols != nCols) {
            throw new IllegalArgumentException("Child column count does not match parent column count.");
        }
        if (rowOffset < 0 || rowOffset + child.nRows > nRows) {
            throw new IllegalArgumentException("Child rows do not fit inside parent row range.");
        }
    }

    private static void addClippedVerticalSegments(
            GridBuildScratch out,
            int col,
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
                    out.add(curr, col, blockedStart - curr);
                }
                curr = Math.max(curr, blockedEnd + 1);
                if (curr > clippedEnd) {
                    return;
                }
            }
        }

        out.add(curr, col, clippedEnd - curr + 1);
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



}
