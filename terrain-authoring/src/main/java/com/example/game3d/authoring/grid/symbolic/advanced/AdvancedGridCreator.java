package com.example.game3d.authoring.grid.symbolic.advanced;

import com.example.game3d.authoring.grid.symbolic.BaseGridCreator;
import com.example.game3d.authoring.grid.symbolic.GridCreatorWrapper;
import com.example.game3d.authoring.grid.symbolic.GridSegment;
import com.example.game3d.authoring.grid.symbolic.RetainedGridSummary;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.GridBuildScratch;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.GridSegmentSink;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.PartialSegmentHandlerResourcePack;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.by_end_pos.EndPosTreeKind;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.PartialSegmentHandler;

import java.util.Arrays;

public class AdvancedGridCreator implements BaseGridCreator, RetainedGridSummary {

    public final int nRows, nCols;
    private final PartialSegmentHandler vertical, horizontal;
    private final GridCreatorWrapper parent;
    private final int parentRowOffset;
    private final boolean propagateToParent;

    private AdvancedGridCreator(
            int nRows, int nCols,
            GridCreatorWrapper parentGrid, int parentRowOffset, boolean propagateToParent,
            PartialSegmentHandler horizontalHandler, PartialSegmentHandler verticalHandler
    ) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.horizontal = horizontalHandler;
        this.vertical = verticalHandler;
        this.parentRowOffset = parentRowOffset;
        this.parent = parentGrid;
        this.propagateToParent = propagateToParent;
    }

    public AdvancedGridCreator(int nRows, int nCols, GridCreatorWrapper parentGrid, int parentRowOffset) {
        this(
                nRows, nCols, parentGrid, parentRowOffset,
                EndPosTreeKind.POOLED_TREAP, true, null
        );
    }

    public AdvancedGridCreator(
            int nRows, int nCols, GridCreatorWrapper parentGrid, int parentRowOffset,
            boolean propagateToParent
    ) {
        this(
                nRows, nCols, parentGrid, parentRowOffset,
                EndPosTreeKind.POOLED_TREAP, propagateToParent, null
        );
    }

    public AdvancedGridCreator(
            int nRows, int nCols, GridCreatorWrapper parentGrid, int parentRowOffset,
            boolean propagateToParent, int[][] blockedRowsRanges
    ) {
        this(
                nRows, nCols, parentGrid, parentRowOffset,
                EndPosTreeKind.POOLED_TREAP, propagateToParent, blockedRowsRanges
        );
    }

    public AdvancedGridCreator(
            int nRows, int nCols, GridCreatorWrapper parentGrid, int parentRowOffset,
            EndPosTreeKind endPosTreeKind) {
        this(nRows, nCols, parentGrid, parentRowOffset, endPosTreeKind, true, null);
    }

    public AdvancedGridCreator(
            int nRows, int nCols, GridCreatorWrapper parentGrid, int parentRowOffset,
            EndPosTreeKind endPosTreeKind, boolean propagateToParent, int[][] blockedRowsRanges
    ) {
        this(
                nRows,
                nCols,
                parentGrid,
                parentRowOffset,
                endPosTreeKind,
                propagateToParent,
                blockedRowsRanges,
                PartialSegmentHandlerResourcePack.createDefault(),
                null,
                null,
                0
        );
    }

    public AdvancedGridCreator(
            int nRows, int nCols, GridCreatorWrapper parentGrid, int parentRowOffset,
            EndPosTreeKind endPosTreeKind, boolean propagateToParent, int[][] blockedRowsRanges,
            PartialSegmentHandlerResourcePack partialSegmentHandlerResourcePack,
            GridCreatorWrapper[] childWrappers, int[] childRowOffsets, int childCount
    ) {
        this(
                createFromChildren(
                        nRows,
                        nCols,
                        parentGrid,
                        parentRowOffset,
                        endPosTreeKind,
                        propagateToParent,
                        blockedRowsRanges,
                        partialSegmentHandlerResourcePack,
                        extractChildCreators(childWrappers, childCount),
                        childRowOffsets,
                        childCount
                )
        );
    }

    private AdvancedGridCreator(AdvancedGridCreator other) {
        this(
                other.nRows,
                other.nCols,
                other.parent,
                other.parentRowOffset,
                other.propagateToParent,
                other.horizontal,
                other.vertical
        );
    }

    public AdvancedGridCreator(int nRows, int nCols) {
        this(nRows, nCols, null, 0, EndPosTreeKind.POOLED_TREAP, true, null);
    }

    public AdvancedGridCreator(int nRows, int nCols, EndPosTreeKind endPosTreeKind) {
        this(nRows, nCols, null, 0, endPosTreeKind, true, null);
    }

    /** Creates an independent optimized grid using caller-owned pools and random state. */
    public AdvancedGridCreator(
            int nRows,
            int nCols,
            PartialSegmentHandlerResourcePack resources
    ) {
        this(nRows, nCols, EndPosTreeKind.POOLED_TREAP, resources);
    }

    /** Creates an independent optimized grid using caller-owned pools and random state. */
    public AdvancedGridCreator(
            int nRows,
            int nCols,
            EndPosTreeKind endPosTreeKind,
            PartialSegmentHandlerResourcePack resources
    ) {
        this(
                nRows,
                nCols,
                null,
                0,
                endPosTreeKind,
                true,
                null,
                resources,
                null,
                null,
                0
        );
    }

    public static AdvancedGridCreator createFromChildren(
            int nRows,
            int nCols,
            GridCreatorWrapper parentGrid,
            int parentRowOffset,
            EndPosTreeKind endPosTreeKind,
            boolean propagateToParent,
            int[][] blockedRowsRanges,
            RetainedGridSummary[] childCreators,
            int[] childRowOffsets,
            int childCount
    ) {
        return createFromChildren(
                nRows,
                nCols,
                parentGrid,
                parentRowOffset,
                endPosTreeKind,
                propagateToParent,
                blockedRowsRanges,
                PartialSegmentHandlerResourcePack.createDefault(),
                childCreators,
                childRowOffsets,
                childCount
        );
    }

    public static AdvancedGridCreator createFromChildren(
            int nRows,
            int nCols,
            GridCreatorWrapper parentGrid,
            int parentRowOffset,
            EndPosTreeKind endPosTreeKind,
            boolean propagateToParent,
            int[][] blockedRowsRanges,
            PartialSegmentHandlerResourcePack partialSegmentHandlerResourcePack,
            RetainedGridSummary[] childCreators,
            int[] childRowOffsets,
            int childCount
    ) {
        return new AdvancedGridCreator(
                nRows,
                nCols,
                parentGrid,
                parentRowOffset,
                propagateToParent,
                PartialSegmentHandler.fromChildren(
                        nRows,
                        nCols,
                        false,
                        endPosTreeKind,
                        partialSegmentHandlerResourcePack,
                        blockedRowsRanges,
                        childCreators,
                        childRowOffsets,
                        childCount
                ),
                PartialSegmentHandler.fromChildren(
                        nRows,
                        nCols,
                        true,
                        endPosTreeKind,
                        partialSegmentHandlerResourcePack,
                        blockedRowsRanges,
                        childCreators,
                        childRowOffsets,
                        childCount
                )
        );
    }

    public static AdvancedGridCreator createFromPreparedHandlers(
            int nRows,
            int nCols,
            GridCreatorWrapper parentGrid,
            int parentRowOffset,
            boolean propagateToParent,
            PartialSegmentHandler horizontalHandler,
            PartialSegmentHandler verticalHandler
    ) {
        if (horizontalHandler == null || verticalHandler == null) {
            throw new IllegalArgumentException("Prepared handlers must both be non-null.");
        }
        return new AdvancedGridCreator(
                nRows,
                nCols,
                parentGrid,
                parentRowOffset,
                propagateToParent,
                horizontalHandler,
                verticalHandler
        );
    }

    public static AdvancedGridCreator createFromHorizontalFreeSegments(
            int nRows, int nCols,
            GridCreatorWrapper parentGrid, int parentRowOffset,
            EndPosTreeKind endPosTreeKind, boolean propagateToParent,
            GridSegment[] horizontalFreeSegments
    ) {
        return createFromHorizontalFreeSegments(
                nRows,
                nCols,
                parentGrid,
                parentRowOffset,
                endPosTreeKind,
                propagateToParent,
                PartialSegmentHandlerResourcePack.createDefault(),
                horizontalFreeSegments
        );
    }

    public static AdvancedGridCreator createFromHorizontalFreeSegments(
            int nRows, int nCols,
            GridCreatorWrapper parentGrid, int parentRowOffset,
            EndPosTreeKind endPosTreeKind, boolean propagateToParent,
            PartialSegmentHandlerResourcePack resources,
            GridSegment[] horizontalFreeSegments
    ) {
        if (resources == null) {
            throw new IllegalArgumentException("resources == null");
        }
        GridSegment[] horizontalSegments = horizontalFreeSegments != null
                ? horizontalFreeSegments.clone()
                : new GridSegment[0];
        GridSegment[] verticalSegments = buildVerticalFreeSegments(nRows, nCols, horizontalSegments);
        return new AdvancedGridCreator(
                nRows,
                nCols,
                parentGrid,
                parentRowOffset,
                propagateToParent,
                PartialSegmentHandler.fromFreeSegments(
                        nRows,
                        nCols,
                        false,
                        endPosTreeKind,
                        resources,
                        horizontalSegments
                ),
                PartialSegmentHandler.fromFreeSegments(
                        nRows,
                        nCols,
                        true,
                        endPosTreeKind,
                        resources,
                        verticalSegments
                )
        );
    }

    @Override
    public GridSegment reserveVertical(int row, int col, int length) {
        assert row <= nRows;
        assert row >= 1;
        assert col <= nCols;
        assert col >= 1;
        vertical.reserve(row, col, length);
        for (int r = row; r < row + length; ++r) {
            horizontal.reserve(r, col, 1);
        }
        return new GridSegment(row, col, length);
    }

    @Override
    public GridSegment reserveHorizontal(int row, int col, int length) {
        assert row <= nRows;
        assert row >= 1;
        assert col <= nCols;
        assert col >= 1;
        horizontal.reserve(row, col, length);
        for (int c = col; c < col + length; ++c) {
            vertical.reserve(row, c, 1);
        }
        return new GridSegment(row, col, length);
    }

    public GridSegment reserveRandomFittingVertical(int length) {
        GridSegment res = vertical.reserveRandomFitting(length);
        for (int r = res.row; r < res.row + length; ++r) {
            horizontal.reserve(r, res.col, 1);
        }
        return res;
    }

    public GridSegment reserveRandomFittingHorizontal(int length) {
        GridSegment res = horizontal.reserveRandomFitting(length);
        for (int c = res.col; c < res.col + length; ++c) {
            vertical.reserve(res.row, c, 1);
        }
        return res;
    }

    public GridCreatorWrapper getParentWrapper() {
        return parent;
    }

    public int getParentRowOffset() {
        return parentRowOffset;
    }

    public boolean shouldPropagateToParent() {
        return propagateToParent;
    }

    @Override
    public int getRowCount() {
        return nRows;
    }

    @Override
    public int getColCount() {
        return nCols;
    }

    public GridSegment[] exportHorizontalFreeSegments() {
        return horizontal.exportFreeSegments();
    }

    public GridSegment[] exportVerticalFreeSegments() {
        return vertical.exportFreeSegments();
    }

    public void forEachHorizontalFreeSegment(GridSegmentSink sink) {
        horizontal.forEachFreeSegment(sink);
    }

    public void forEachVerticalFreeSegment(GridSegmentSink sink) {
        vertical.forEachFreeSegment(sink);
    }

    public void appendMaximalFreeSegments(boolean verticalSegments, GridBuildScratch out) {
        if (verticalSegments) {
            vertical.appendMaximalFreeSegments(out);
        } else {
            horizontal.appendMaximalFreeSegments(out);
        }
    }

    /**
     * Reserves {@code k} random unoccupied single-cell fields.
     * Returned segments are sorted by (row, col).
     */
    public GridSegment[] reserveKRandomFields(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be > 0");
        }
        GridSegment[] reserved = new GridSegment[k];
        for (int i = 0; i < k; ++i) {
            reserved[i] = reserveRandomFittingVertical(1);
        }
        Arrays.sort(reserved, (a, b) -> {
            if (a.row != b.row) {
                return Integer.compare(a.row, b.row);
            }
            return Integer.compare(a.col, b.col);
        });
        return reserved;
    }

    private static GridSegment[] buildVerticalFreeSegments(
            int nRows, int nCols, GridSegment[] horizontalFreeSegments
    ) {
        boolean[][] freeCells = new boolean[nRows][nCols];
        for (GridSegment seg : horizontalFreeSegments) {
            if (seg == null || seg.length <= 0) {
                continue;
            }
            int rowIdx = seg.row - 1;
            int startCol = seg.col - 1;
            int endColExclusive = Math.min(nCols, startCol + seg.length);
            if (rowIdx < 0 || rowIdx >= nRows) {
                continue;
            }
            for (int colIdx = Math.max(0, startCol); colIdx < endColExclusive; ++colIdx) {
                freeCells[rowIdx][colIdx] = true;
            }
        }

        GridSegment[] verticalSegments = new GridSegment[nRows * nCols];
        int count = 0;
        for (int col = 0; col < nCols; ++col) {
            int runStart = -1;
            for (int row = 0; row <= nRows; ++row) {
                boolean free = row < nRows && freeCells[row][col];
                if (free && runStart == -1) {
                    runStart = row;
                    continue;
                }
                if (!free && runStart != -1) {
                    verticalSegments[count++] =
                            GridSegment.GS(runStart + 1, col + 1, row - runStart);
                    runStart = -1;
                }
            }
        }
        return Arrays.copyOf(verticalSegments, count);
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

    @Override
    public void destroy(){
        vertical.destroy();
        horizontal.destroy();
    }

    @Override
    public void printGrid(){
        System.out.println("Horizontal hashCode(): "+horizontal.hashCode());
        horizontal.printGrid();
    }

    @Override
    public void printMetaData(){
        System.out.println("GRID METADATA: ");
        System.out.println("rows: "+nRows+" cols: "+nCols);
        BaseGridCreator parentContent = parent == null ? null : parent.getContent();
        if (parentContent != null) {
            System.out.println("Parent: "+ parentContent.getClass().getSimpleName());
            parentContent.printMetaData();
        } else {
            System.out.println("Parent: null");
        }
    }


}
