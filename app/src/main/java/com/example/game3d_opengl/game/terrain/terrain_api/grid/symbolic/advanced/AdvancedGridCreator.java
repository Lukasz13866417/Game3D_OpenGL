package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.BaseGridCreator;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.cell_pair.CellPairQuerySegtree;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.PartialSegmentHandler;
import com.example.game3d_opengl.game.util.GameRandom;

import java.util.Arrays;

public class AdvancedGridCreator implements BaseGridCreator {

    public final int nRows, nCols;
    private final PartialSegmentHandler vertical, horizontal;
    private final CellPairQuerySegtree cellPairTree;
    private final GridCreatorWrapper parent;
    private final int parentRowOffset;

    public AdvancedGridCreator(int nRows, int nCols, GridCreatorWrapper parentGrid, int parentRowOffset) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.horizontal = new PartialSegmentHandler(nRows, nCols, false);
        this.vertical = new PartialSegmentHandler(nRows, nCols, true);
        this.cellPairTree = new CellPairQuerySegtree(nRows, nCols);
        this.parentRowOffset = parentRowOffset;
        this.parent = parentGrid;
    }

    public AdvancedGridCreator(int nRows, int nCols) {
        this(nRows, nCols, null, 0);
    }

    @Override
    public GridSegment reserveVertical(int row, int col, int length) {
        assert row <= nRows;
        assert row >= 1;
        assert col <= nCols;
        assert col >= 1;
        if (parent != null && parent.content != null) {
            parent.content.reserveVertical(row + parentRowOffset, col, length);
        }
        vertical.reserve(row, col, length);
        for (int r = row; r < row + length; ++r) {
            horizontal.reserve(r, col, 1);
            cellPairTree.reserve(r, col);
        }
        return new GridSegment(row, col, length);
    }

    @Override
    public GridSegment reserveHorizontal(int row, int col, int length) {
        assert row <= nRows;
        assert row >= 1;
        assert col <= nCols;
        assert col >= 1;
        if (parent != null && parent.content != null) {
            parent.content.reserveHorizontal(row + parentRowOffset, col, length);
        }
        horizontal.reserve(row, col, length);
        for (int c = col; c < col + length; ++c) {
            vertical.reserve(row, c, 1);
            cellPairTree.reserve(row, c);
        }
        return new GridSegment(row, col, length);
    }

    public GridSegment reserveRandomFittingVertical(int length) {
        GridSegment res = vertical.reserveRandomFitting(length);
        if (parent != null && parent.content != null) {
            parent.content.reserveVertical(res.row + parentRowOffset, res.col, res.length);
        }
        for (int r = res.row; r < res.row + length; ++r) {
            horizontal.reserve(r, res.col, 1);
            cellPairTree.reserve(r, res.col);
        }
        return res;
    }

    public GridSegment reserveRandomFittingHorizontal(int length) {
        GridSegment res = horizontal.reserveRandomFitting(length);
        if (parent != null && parent.content != null) {
            parent.content.reserveHorizontal(res.row + parentRowOffset, res.col, res.length);
        }
        for (int c = res.col; c < res.col + length; ++c) {
            vertical.reserve(res.row, c, 1);
            cellPairTree.reserve(res.row, c);
        }
        return res;
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

    /**
     * Returns the number of valid portal pairs with at least {@code d} free cells
     * between them (measured in the linearized row-major code space).
     */
    public long countPortalPairs(int d) {
        return cellPairTree.countGoodPairs(d);
    }

    /**
     * Reserves a random pair of free cells (entrance, exit) that have at least {@code d}
     * free cells between them. The exit cell always has a larger row-major code than the
     * entrance cell. Both cells are also reserved in the vertical/horizontal handlers.
     *
     * @return {@code GridSegment[]{entrance, exit}}, both with length 1.
     * @throws IllegalArgumentException if no valid pair exists.
     */
    public GridSegment[] reserveRandomPortalPair(int d) {
        long count = cellPairTree.countGoodPairs(d);
        if (count <= 0) {
            throw new IllegalArgumentException(
                    "No valid portal pair with distance >= " + d
                    + " (free cells: " + (nRows * nCols) + ")");
        }
        int k = GameRandom.randInt(1, (int) Math.min(count, Integer.MAX_VALUE));
        GridSegment[] pair = cellPairTree.findKthPairAndReserve(k, d);

        // Sync the two reserved cells into vertical/horizontal handlers + parent
        GridSegment entrance = pair[0];
        GridSegment exit = pair[1];
        vertical.reserve(entrance.row, entrance.col, 1);
        horizontal.reserve(entrance.row, entrance.col, 1);
        vertical.reserve(exit.row, exit.col, 1);
        horizontal.reserve(exit.row, exit.col, 1);
        if (parent != null && parent.content != null) {
            parent.content.reserveVertical(entrance.row + parentRowOffset, entrance.col, 1);
            parent.content.reserveVertical(exit.row + parentRowOffset, exit.col, 1);
        }

        return pair;
    }

    @Override
    public void destroy(){
        vertical.destroy();
        horizontal.destroy();
        cellPairTree.destroy();
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
        if (parent != null && parent.content != null) {
            System.out.println("Parent: "+ parent.content.getClass().getSimpleName());
            parent.content.printMetaData();
        } else {
            System.out.println("Parent: null");
        }
    }


}
