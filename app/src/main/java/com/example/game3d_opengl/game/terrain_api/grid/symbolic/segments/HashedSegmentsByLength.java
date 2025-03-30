package com.example.game3d_opengl.game.terrain_api.grid.symbolic.segments;


import com.example.game3d_opengl.game.terrain_api.grid.symbolic.GridSegment;

abstract class HashedSegmentsByLength implements SegmentsByLength {
    
    protected final int totalRows, nCols;
    protected final boolean areSegmentsVertical;

    public HashedSegmentsByLength(int totalRows, int nCols, boolean areSegmentsVertical) {
        this.totalRows = totalRows;
        this.nCols = nCols;
        this.areSegmentsVertical = areSegmentsVertical;
    }

    public static int nextPowerOfTwo(int n) {
        if (n <= 0) {
            return 1;
        }
        int highestOneBit = Integer.highestOneBit(n);
        return (n == highestOneBit) ? n : highestOneBit << 1;
    }

    protected int segHash(int row, int col, int length) {
        return length * totalRows * nCols + row * nCols + col;
    }

    protected GridSegment fromHash(int hash) {
        int col = hash % nCols;
        hash /= nCols;
        int row = hash % totalRows;
        hash /= totalRows;
        return GridSegment.GS(row + 1, col + 1, hash);
    }

    protected GridSegment kthSpaceInSegment(int hash, int spaceSize, int k) {
        int col = hash % nCols;
        hash /= nCols;
        int row = hash % totalRows;
        assert (k > 0);
        return areSegmentsVertical ? GridSegment.GS(row + k, col + 1, spaceSize)
                                   : GridSegment.GS(row + 1, col + k, spaceSize);
    }
}
