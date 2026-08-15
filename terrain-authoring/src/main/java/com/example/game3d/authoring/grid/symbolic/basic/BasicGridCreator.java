package com.example.game3d.authoring.grid.symbolic.basic;

import java.util.Arrays;

import com.example.game3d.authoring.grid.symbolic.BaseGridCreator;
import com.example.game3d.authoring.grid.symbolic.GridCreatorWrapper;
import com.example.game3d.authoring.grid.symbolic.GridSegment;
import com.example.game3d.authoring.grid.symbolic.RetainedGridSummary;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.GridSegmentSink;

/**
 * Basic grid creator that mirrors the constructor and parent-propagation
 * behavior of AdvancedGridCreator but without random helpers.
 */
public class BasicGridCreator implements BaseGridCreator, RetainedGridSummary {
    private static final int OP_VERTICAL = 1;
    private static final int OP_HORIZONTAL = 2;

    private final int nRows, nCols;
    private final GridCreatorWrapper parent;
    private final int parentRowOffset;
    private final boolean propagateToParent;

    private int[] blockedRangeStarts = new int[4];
    private int[] blockedRangeEnds = new int[4];
    private int blockedRangeCount = 0;

    private RetainedGridSummary[] childSummaries = new RetainedGridSummary[4];
    private int[] childRowOffsets = new int[4];
    private int childCount = 0;

    private int[] opKinds = new int[8];
    private int[] opRows = new int[8];
    private int[] opCols = new int[8];
    private int[] opLengths = new int[8];
    private int opCount = 0;

    private int[] rowFreeMasks = new int[0];
    private boolean summaryDirty = true;

    public BasicGridCreator(int nRows, int nCols, GridCreatorWrapper parentGrid, int parentRowOffset) {
        this(nRows, nCols, parentGrid, parentRowOffset, true, new int[0][2], null, null, 0);
    }

    public BasicGridCreator(
            int nRows,
            int nCols,
            GridCreatorWrapper parentGrid,
            int parentRowOffset,
            boolean propagateToParent
    ) {
        this(nRows, nCols, parentGrid, parentRowOffset, propagateToParent, new int[0][2], null, null, 0);
    }

    public BasicGridCreator(
            int nRows,
            int nCols,
            GridCreatorWrapper parentGrid,
            int parentRowOffset,
            boolean propagateToParent,
            int[][] blockedRowsRanges,
            RetainedGridSummary[] childSummaries,
            int[] childRowOffsets,
            int childCount
    ) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.parent = parentGrid;
        this.parentRowOffset = parentRowOffset;
        this.propagateToParent = propagateToParent;

        absorbBlockedRanges(blockedRowsRanges, 0);
        absorbChildren(childSummaries, childRowOffsets, childCount);
    }

    @Override
    public GridSegment reserveVertical(int row, int col, int length) {
        validateVerticalReservation(row, col, length);
        recordReservation(OP_VERTICAL, row, col, length);
        return new GridSegment(row, col, length);
    }

    @Override
    public GridSegment reserveHorizontal(int row, int col, int length) {
        validateHorizontalReservation(row, col, length);
        recordReservation(OP_HORIZONTAL, row, col, length);
        return new GridSegment(row, col, length);
    }

    @Override
    public void destroy() {
        for (int i = 0; i < childCount; ++i) {
            if (childSummaries[i] != null) {
                childSummaries[i].destroy();
                childSummaries[i] = null;
            }
        }
    }

    @Override
    public void printGrid() {
        ensureSummaryBuilt();
        for (int row = 1; row <= nRows; ++row) {
            StringBuilder sb = new StringBuilder(nCols);
            int mask = rowFreeMasks[row];
            for (int col = 1; col <= nCols; ++col) {
                sb.append(isColFree(mask, col) ? '.' : '#');
            }
            System.out.println(sb);
        }
    }

    @Override
    public void printMetaData() {
        System.out.println("GRID METADATA (Basic): ");
        System.out.println("rows: " + nRows + " cols: " + nCols);
        BaseGridCreator parentContent = parent == null ? null : parent.getContent();
        if (parentContent != null) {
            System.out.println("Parent: " + parentContent.getClass().getSimpleName());
            parentContent.printMetaData();
        } else {
            System.out.println("Parent: null");
        }
    }

    @Override
    public int getRowCount() {
        return nRows;
    }

    @Override
    public int getColCount() {
        return nCols;
    }

    @Override
    public void forEachHorizontalFreeSegment(GridSegmentSink sink) {
        ensureSummaryBuilt();
        if (sink == null) {
            return;
        }
        for (int row = 1; row <= nRows; ++row) {
            emitHorizontalRuns(sink, row, rowFreeMasks[row]);
        }
    }

    @Override
    public void forEachVerticalFreeSegment(GridSegmentSink sink) {
        ensureSummaryBuilt();
        if (sink == null) {
            return;
        }
        for (int col = 1; col <= nCols; ++col) {
            int runStart = -1;
            for (int row = 1; row <= nRows + 1; ++row) {
                boolean free = row <= nRows && isColFree(rowFreeMasks[row], col);
                if (free && runStart == -1) {
                    runStart = row;
                    continue;
                }
                if (!free && runStart != -1) {
                    sink.accept(runStart, col, row - runStart);
                    runStart = -1;
                }
            }
        }
    }

    private void validateVerticalReservation(int row, int col, int length) {
        if (row < 1 || row > nRows || col < 1 || col > nCols || length < 1 || row + length - 1 > nRows) {
            throw new IllegalArgumentException("Basic vertical reservation does not fit inside the grid.");
        }
    }

    private void validateHorizontalReservation(int row, int col, int length) {
        if (row < 1 || row > nRows || col < 1 || col > nCols || length < 1 || col + length - 1 > nCols) {
            throw new IllegalArgumentException("Basic horizontal reservation does not fit inside the grid.");
        }
    }

    private void recordReservation(int kind, int row, int col, int length) {
        appendReservation(kind, row, col, length);
        summaryDirty = true;
    }

    private void appendReservation(int kind, int row, int col, int length) {
        ensureOpCapacity(opCount + 1);
        opKinds[opCount] = kind;
        opRows[opCount] = row;
        opCols[opCount] = col;
        opLengths[opCount] = length;
        opCount++;
    }

    private void ensureOpCapacity(int desiredSize) {
        if (opKinds.length >= desiredSize) {
            return;
        }
        int newSize = Math.max(desiredSize, opKinds.length * 2);
        opKinds = Arrays.copyOf(opKinds, newSize);
        opRows = Arrays.copyOf(opRows, newSize);
        opCols = Arrays.copyOf(opCols, newSize);
        opLengths = Arrays.copyOf(opLengths, newSize);
    }

    private void ensureBlockedRangeCapacity(int desiredSize) {
        if (blockedRangeStarts.length >= desiredSize) {
            return;
        }
        int newSize = Math.max(desiredSize, blockedRangeStarts.length * 2);
        blockedRangeStarts = Arrays.copyOf(blockedRangeStarts, newSize);
        blockedRangeEnds = Arrays.copyOf(blockedRangeEnds, newSize);
    }

    private void ensureChildCapacity(int desiredSize) {
        if (childSummaries.length >= desiredSize) {
            return;
        }
        int newSize = Math.max(desiredSize, childSummaries.length * 2);
        childSummaries = Arrays.copyOf(childSummaries, newSize);
        childRowOffsets = Arrays.copyOf(childRowOffsets, newSize);
    }

    private void ensureSummaryBuilt() {
        if (!summaryDirty) {
            return;
        }
        buildRowMasks();
        summaryDirty = false;
    }

    private void buildRowMasks() {
        ensureRowMaskCapacity();
        Arrays.fill(rowFreeMasks, 0, nRows + 1, fullRowMask());
        rowFreeMasks[0] = 0;

        for (int i = 0; i < blockedRangeCount; ++i) {
            int start = Math.max(1, blockedRangeStarts[i]);
            int end = Math.min(nRows, blockedRangeEnds[i]);
            for (int row = start; row <= end; ++row) {
                rowFreeMasks[row] = 0;
            }
        }

        for (int i = 0; i < childCount; ++i) {
            RetainedGridSummary child = childSummaries[i];
            if (child == null) {
                continue;
            }
            int rowOffset = childRowOffsets[i];
            int childStartRow = rowOffset + 1;
            int childEndRow = rowOffset + child.getRowCount();
            for (int row = childStartRow; row <= childEndRow; ++row) {
                if (!isBlockedRow(row)) {
                    rowFreeMasks[row] = 0;
                }
            }
            child.forEachHorizontalFreeSegment((row, col, length) -> {
                int parentRow = rowOffset + row;
                if (parentRow < 1 || parentRow > nRows || isBlockedRow(parentRow)) {
                    return;
                }
                rowFreeMasks[parentRow] |= buildMask(col, length);
            });
        }

        for (int i = 0; i < opCount; ++i) {
            int row = opRows[i];
            int col = opCols[i];
            int length = opLengths[i];
            if (opKinds[i] == OP_VERTICAL) {
                int clearMask = ~(1 << (col - 1));
                for (int delta = 0; delta < length; ++delta) {
                    rowFreeMasks[row + delta] &= clearMask;
                }
            } else {
                rowFreeMasks[row] &= ~buildMask(col, length);
            }
        }
    }

    private void ensureRowMaskCapacity() {
        if (rowFreeMasks.length >= nRows + 1) {
            return;
        }
        rowFreeMasks = new int[nRows + 1];
    }

    private void absorbChildren(
            RetainedGridSummary[] incomingChildSummaries,
            int[] incomingChildRowOffsets,
            int incomingChildCount
    ) {
        if (incomingChildSummaries == null || incomingChildCount <= 0) {
            return;
        }
        int limit = Math.min(incomingChildCount, incomingChildSummaries.length);
        for (int i = 0; i < limit; ++i) {
            RetainedGridSummary child = incomingChildSummaries[i];
            if (child == null) {
                continue;
            }
            int rowOffset = incomingChildRowOffsets != null && i < incomingChildRowOffsets.length
                    ? incomingChildRowOffsets[i]
                    : 0;
            absorbChild(child, rowOffset);
        }
    }

    private void absorbChild(RetainedGridSummary child, int rowOffset) {
        if (child instanceof BasicGridCreator) {
            absorbBasicChild((BasicGridCreator) child, rowOffset);
            return;
        }
        validateChildPlacement(child, rowOffset);
        ensureNoChildOverlap(child.getRowCount(), rowOffset);
        ensureChildCapacity(childCount + 1);
        childSummaries[childCount] = child;
        childRowOffsets[childCount] = rowOffset;
        childCount++;
    }

    private void absorbBasicChild(BasicGridCreator child, int rowOffset) {
        if (child == null) {
            return;
        }
        validateChildPlacement(child, rowOffset);
        absorbBlockedRanges(child.blockedRangeStarts, child.blockedRangeEnds, child.blockedRangeCount, rowOffset);
        for (int i = 0; i < child.childCount; ++i) {
            RetainedGridSummary grandChild = child.childSummaries[i];
            if (grandChild == null) {
                continue;
            }
            absorbChild(grandChild, rowOffset + child.childRowOffsets[i]);
            child.childSummaries[i] = null;
        }
        for (int i = 0; i < child.opCount; ++i) {
            appendReservation(
                    child.opKinds[i],
                    rowOffset + child.opRows[i],
                    child.opCols[i],
                    child.opLengths[i]
            );
        }
    }

    private void absorbBlockedRanges(int[][] ranges, int baseOffset) {
        if (ranges == null) {
            return;
        }
        for (int[] range : ranges) {
            if (range == null || range.length < 2) {
                continue;
            }
            addBlockedRange(baseOffset + range[0], baseOffset + range[1]);
        }
    }

    private void absorbBlockedRanges(int[] starts, int[] ends, int count, int baseOffset) {
        for (int i = 0; i < count; ++i) {
            addBlockedRange(baseOffset + starts[i], baseOffset + ends[i]);
        }
    }

    private void addBlockedRange(int startRow, int endRow) {
        int start = Math.max(1, Math.min(startRow, endRow));
        int end = Math.min(nRows, Math.max(startRow, endRow));
        if (start > end) {
            return;
        }
        ensureBlockedRangeCapacity(blockedRangeCount + 1);
        blockedRangeStarts[blockedRangeCount] = start;
        blockedRangeEnds[blockedRangeCount] = end;
        blockedRangeCount++;
        summaryDirty = true;
    }

    private void ensureNoChildOverlap(int childRowCount, int rowOffset) {
        int start = rowOffset + 1;
        int end = rowOffset + childRowCount;
        for (int i = 0; i < childCount; ++i) {
            int otherStart = childRowOffsets[i] + 1;
            int otherEnd = childRowOffsets[i] + childSummaries[i].getRowCount();
            if (start <= otherEnd && otherStart <= end) {
                throw new IllegalStateException("Overlapping propagated child summaries are not supported.");
            }
        }
    }

    private void validateChildPlacement(RetainedGridSummary child, int rowOffset) {
        if (child.getColCount() != nCols) {
            throw new IllegalArgumentException("Child column count does not match parent column count.");
        }
        if (rowOffset < 0 || rowOffset + child.getRowCount() > nRows) {
            throw new IllegalArgumentException("Child rows do not fit inside parent row range.");
        }
    }

    private boolean isBlockedRow(int row) {
        for (int i = 0; i < blockedRangeCount; ++i) {
            if (row >= blockedRangeStarts[i] && row <= blockedRangeEnds[i]) {
                return true;
            }
        }
        return false;
    }

    private int fullRowMask() {
        if (nCols >= 31) {
            return -1;
        }
        return (1 << nCols) - 1;
    }

    private int buildMask(int startCol, int length) {
        int safeStart = Math.max(1, startCol);
        int safeEnd = Math.min(nCols, startCol + length - 1);
        if (safeStart > safeEnd) {
            return 0;
        }
        int mask = 0;
        for (int col = safeStart; col <= safeEnd; ++col) {
            mask |= (1 << (col - 1));
        }
        return mask;
    }

    private boolean isColFree(int rowMask, int col) {
        return (rowMask & (1 << (col - 1))) != 0;
    }

    private void emitHorizontalRuns(GridSegmentSink sink, int row, int rowMask) {
        int runStart = -1;
        for (int col = 1; col <= nCols + 1; ++col) {
            boolean free = col <= nCols && isColFree(rowMask, col);
            if (free && runStart == -1) {
                runStart = col;
                continue;
            }
            if (!free && runStart != -1) {
                sink.accept(row, runStart, col - runStart);
                runStart = -1;
            }
        }
    }
}
