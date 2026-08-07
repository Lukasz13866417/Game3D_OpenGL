package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_length;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;

public abstract class LengthOrderedSegments implements SegmentsByLength {
    protected final int totalRows;
    protected final int nCols;
    protected final boolean areSegmentsVertical;

    protected LengthOrderedSegments(int totalRows, int nCols, boolean areSegmentsVertical) {
        this.totalRows = totalRows;
        this.nCols = nCols;
        this.areSegmentsVertical = areSegmentsVertical;
    }

    protected static int compareKey(
            int lengthA, int rowA, int colA,
            int lengthB, int rowB, int colB
    ) {
        if (lengthA != lengthB) {
            return Integer.compare(lengthA, lengthB);
        }
        if (rowA != rowB) {
            return Integer.compare(rowA, rowB);
        }
        return Integer.compare(colA, colB);
    }

    protected GridSegment kthSpaceInSegment(int row, int col, int spaceSize, int k) {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be > 0");
        }
        return areSegmentsVertical
                ? GridSegment.GS(row + k - 1, col, spaceSize)
                : GridSegment.GS(row, col + k - 1, spaceSize);
    }
}
