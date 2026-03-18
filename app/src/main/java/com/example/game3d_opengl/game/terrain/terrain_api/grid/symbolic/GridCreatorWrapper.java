package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.PartialSegmentHandlerResourcePack;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.AdvancedGridCreator;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos.EndPosTreeKind;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.basic.BasicGridCreator;
import com.example.game3d_opengl.game.terrain.terrain_api.main.GridResourcePack;

import java.util.Arrays;

public class GridCreatorWrapper {
    private static final int OP_VERTICAL = 0;
    private static final int OP_HORIZONTAL = 1;

    private BaseGridCreator content = null;
    private final PartialSegmentHandlerResourcePack partialSegmentHandlerResourcePack;
    private boolean isConfigured = false;
    private boolean isAdvanced = false;
    private int nRows = 0;
    private int nCols = 0;
    private GridCreatorWrapper parentWrapper = null;
    private int parentRowOffset = 0;
    private boolean propagateToParent = true;
    private EndPosTreeKind endPosTreeKind = EndPosTreeKind.POOLED_TREAP;
    private int[][] blockedRowRanges = new int[0][2];

    private int[] pendingOps = new int[32]; // [type,row,col,length] repeated
    private int pendingCount = 0;
    private int[] pendingBlockedRowRanges = new int[32]; // [rowStart,rowEnd] repeated
    private int pendingBlockedRangeCount = 0;
    private GridCreatorWrapper[] childWrappers = new GridCreatorWrapper[4];
    private int[] childRowOffsets = new int[4];
    private int childCount = 0;
    private AdvancedGridCreator retainedAdvancedCreator = null;

    public GridCreatorWrapper() {
        this(GridResourcePack.defaultInstance().partialSegmentHandlerResourcePack());
    }

    public GridCreatorWrapper(PartialSegmentHandlerResourcePack partialSegmentHandlerResourcePack) {
        this.partialSegmentHandlerResourcePack = partialSegmentHandlerResourcePack;
    }

    public BaseGridCreator getContent() {
        return content;
    }

    public void setContent(BaseGridCreator content) {
        this.content = content;
        flushPendingIfPossible();
    }

    public void configureStructure(
            boolean isAdvanced,
            int nRows,
            int nCols,
            GridCreatorWrapper parentWrapper,
            int parentRowOffset,
            EndPosTreeKind endPosTreeKind,
            boolean propagateToParent,
            int[][] blockedRowRanges
    ) {
        this.isConfigured = true;
        this.isAdvanced = isAdvanced;
        this.nRows = nRows;
        this.nCols = nCols;
        this.parentWrapper = parentWrapper;
        this.parentRowOffset = parentRowOffset;
        this.endPosTreeKind = endPosTreeKind != null ? endPosTreeKind : EndPosTreeKind.POOLED_TREAP;
        this.propagateToParent = propagateToParent;
        this.blockedRowRanges = blockedRowRanges != null ? blockedRowRanges : new int[0][2];
    }

    public void materializeIfNeeded() {
        if (content != null) {
            return;
        }
        if (!isConfigured) {
            throw new IllegalStateException("Grid creator wrapper has not been configured yet.");
        }
        if (isAdvanced) {
            content = AdvancedGridCreator.createFromChildren(
                    nRows,
                    nCols,
                    parentWrapper,
                    parentRowOffset,
                    endPosTreeKind,
                    propagateToParent,
                    blockedRowRanges,
                    partialSegmentHandlerResourcePack,
                    extractRetainedChildCreators(),
                    childRowOffsets,
                    childCount
            );
            releaseRetainedChildren();
        } else {
            content = new BasicGridCreator(nRows, nCols, parentWrapper, parentRowOffset, propagateToParent);
        }
        flushPendingIfPossible();
    }

    public void finishAddonPhase() {
        if (content instanceof AdvancedGridCreator) {
            AdvancedGridCreator creator = (AdvancedGridCreator) content;
            if (creator.shouldPropagateToParent() && creator.getParentWrapper() != null) {
                retainedAdvancedCreator = creator;
                content = null;
                return;
            }
        }
        if (content != null) {
            content.destroy();
            content = null;
        }
    }

    public void addChildWrapper(GridCreatorWrapper childWrapper, int rowOffset) {
        if (childWrapper == null) {
            return;
        }
        if (content != null) {
            throw new IllegalStateException(
                    "Child wrappers must be registered before grid creator materialization.");
        }
        if (isConfigured && childWrapper.nCols != nCols) {
            throw new IllegalArgumentException(
                    "Child column count " + childWrapper.nCols
                            + " does not match parent column count " + nCols + "."
            );
        }
        if (isConfigured && (rowOffset < 0 || rowOffset + childWrapper.nRows > nRows)) {
            throw new IllegalArgumentException(
                    "Child rows [" + rowOffset + ", " + (rowOffset + childWrapper.nRows)
                            + ") do not fit in parent row count " + nRows + "."
            );
        }
        ensureChildCapacity();
        childWrappers[childCount] = childWrapper;
        childRowOffsets[childCount] = rowOffset;
        childCount++;
    }

    public GridSegment reserveVertical(int row, int col, int length) {
        if (content != null) {
            return content.reserveVertical(row, col, length);
        }
        enqueuePending(OP_VERTICAL, row, col, length);
        return new GridSegment(row, col, length);
    }

    public GridSegment reserveHorizontal(int row, int col, int length) {
        if (content != null) {
            return content.reserveHorizontal(row, col, length);
        }
        enqueuePending(OP_HORIZONTAL, row, col, length);
        return new GridSegment(row, col, length);
    }

    public void addPendingBlockedRowsRange(int rowStartInclusive, int rowEndInclusive) {
        if (content != null) {
            throw new IllegalStateException(
                    "Blocked rows must be queued before grid creator construction");
        }
        int start = Math.min(rowStartInclusive, rowEndInclusive);
        int end = Math.max(rowStartInclusive, rowEndInclusive);
        if (end < 1) {
            return;
        }
        start = Math.max(1, start);
        ensureBlockedRangesCapacity();
        int base = pendingBlockedRangeCount * 2;
        pendingBlockedRowRanges[base] = start;
        pendingBlockedRowRanges[base + 1] = end;
        pendingBlockedRangeCount++;
    }

    public int[][] consumePendingBlockedRowsRanges() {
        if (pendingBlockedRangeCount == 0) {
            return new int[0][2];
        }
        int[][] ranges = new int[pendingBlockedRangeCount][2];
        for (int i = 0; i < pendingBlockedRangeCount; ++i) {
            int base = i * 2;
            ranges[i][0] = pendingBlockedRowRanges[base];
            ranges[i][1] = pendingBlockedRowRanges[base + 1];
        }
        pendingBlockedRangeCount = 0;

        Arrays.sort(ranges, (a, b) -> Integer.compare(a[0], b[0]));
        int[][] merged = new int[ranges.length][2];
        int mergedCount = 0;
        for (int[] range : ranges) {
            if (mergedCount == 0 || range[0] > merged[mergedCount - 1][1] + 1) {
                merged[mergedCount][0] = range[0];
                merged[mergedCount][1] = range[1];
                mergedCount++;
                continue;
            }
            merged[mergedCount - 1][1] = Math.max(merged[mergedCount - 1][1], range[1]);
        }
        return Arrays.copyOf(merged, mergedCount);
    }

    private void enqueuePending(int type, int row, int col, int length) {
        ensurePendingCapacity();
        int base = pendingCount * 4;
        pendingOps[base] = type;
        pendingOps[base + 1] = row;
        pendingOps[base + 2] = col;
        pendingOps[base + 3] = length;
        pendingCount++;
    }

    private void ensurePendingCapacity() {
        int required = (pendingCount + 1) * 4;
        if (required <= pendingOps.length) {
            return;
        }
        int[] newOps = new int[pendingOps.length * 2];
        System.arraycopy(pendingOps, 0, newOps, 0, pendingOps.length);
        pendingOps = newOps;
    }

    private void ensureBlockedRangesCapacity() {
        int required = (pendingBlockedRangeCount + 1) * 2;
        if (required <= pendingBlockedRowRanges.length) {
            return;
        }
        int[] newRanges = new int[pendingBlockedRowRanges.length * 2];
        System.arraycopy(pendingBlockedRowRanges, 0, newRanges, 0, pendingBlockedRowRanges.length);
        pendingBlockedRowRanges = newRanges;
    }

    private void ensureChildCapacity() {
        int required = childCount + 1;
        if (required <= childWrappers.length) {
            return;
        }
        GridCreatorWrapper[] newChildWrappers = new GridCreatorWrapper[childWrappers.length * 2];
        int[] newRowOffsets = new int[childRowOffsets.length * 2];
        System.arraycopy(childWrappers, 0, newChildWrappers, 0, childWrappers.length);
        System.arraycopy(childRowOffsets, 0, newRowOffsets, 0, childRowOffsets.length);
        childWrappers = newChildWrappers;
        childRowOffsets = newRowOffsets;
    }

    private void flushPendingIfPossible() {
        if (content == null || pendingCount == 0) {
            return;
        }
        for (int i = 0; i < pendingCount; ++i) {
            int base = i * 4;
            int type = pendingOps[base];
            int row = pendingOps[base + 1];
            int col = pendingOps[base + 2];
            int length = pendingOps[base + 3];
            if (type == OP_VERTICAL) {
                content.reserveVertical(row, col, length);
            } else {
                content.reserveHorizontal(row, col, length);
            }
        }
        pendingCount = 0;
    }

    public AdvancedGridCreator getRetainedAdvancedCreator() {
        return retainedAdvancedCreator;
    }

    public void releaseRetainedAdvancedCreator() {
        if (retainedAdvancedCreator == null) {
            return;
        }
        retainedAdvancedCreator.destroy();
        retainedAdvancedCreator = null;
    }

    private void releaseRetainedChildren() {
        for (int i = 0; i < childCount; ++i) {
            if (childWrappers[i] != null) {
                childWrappers[i].releaseRetainedAdvancedCreator();
            }
        }
    }

    private AdvancedGridCreator[] extractRetainedChildCreators() {
        AdvancedGridCreator[] childCreators = new AdvancedGridCreator[childCount];
        for (int i = 0; i < childCount; ++i) {
            childCreators[i] = childWrappers[i] == null ? null : childWrappers[i].getRetainedAdvancedCreator();
        }
        return childCreators;
    }
}
