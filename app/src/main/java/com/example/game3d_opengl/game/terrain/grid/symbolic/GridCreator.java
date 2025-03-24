package com.example.game3d_opengl.game.terrain.grid.symbolic;

import com.example.game3d_opengl.game.terrain.grid.symbolic.segments.PartialSegmentHandler;

public class GridCreator {

    private final PartialSegmentHandler vertical, horizontal;
    private final GridCreator parent;
    private final int parentRowOffset;

    public GridCreator(int nRows, int nCols, GridCreator parentGrid, int parentRowOffset) {
        this.horizontal = new PartialSegmentHandler(nRows, nCols, false);
        this.vertical = new PartialSegmentHandler(nRows, nCols, true);
        this.parentRowOffset = parentRowOffset;
        this.parent = parentGrid;
    }

    public GridCreator(int nRows, int nCols) {
        this(nRows, nCols, null, 0);
    }

    public void reserveVertical(int row, int col, int length) {
        if (parent != null) {
            parent.reserveHorizontal(row + parentRowOffset, col, length);
        }
        vertical.reserve(row, col, length);
        for (int r = row; r < row + length; r++) {
            horizontal.reserve(r, col, 1);
        }
    }

    public void reserveHorizontal(int row, int col, int length) {
        if (parent != null) {
            parent.reserveHorizontal(row + parentRowOffset, col, length);
        }
        horizontal.reserve(row, col, length);
        for (int c = col; c < col + length; c++) {
            vertical.reserve(row, c, 1);
        }
    }

    public GridSegment reserveRandomFittingVertical(int length) {
        GridSegment res = vertical.reserveRandomFitting(length);
        if (parent != null) {
            parent.reserveHorizontal(res.row + parentRowOffset, res.col, res.length);
        }
        for (int r = res.row; r < res.row + length; r++) {
            horizontal.reserve(r, res.col, 1);
        }
        return res;
    }

    public GridSegment reserveRandomFittingHorizontal(int length) {
        GridSegment res = horizontal.reserveRandomFitting(length);
        if (parent != null) {
            parent.reserveVertical(res.row + parentRowOffset, res.col, res.length);
        }
        for (int c = res.col; c < res.col + length; c++) {
            vertical.reserve(res.row, c, 1);
        }
        return res;
    }

    public void printGrid() {
        horizontal.printGrid(); // can also be vertical.printGrid(), since they represent the same grid
    }

    public void destroy(){
        vertical.flush();
        horizontal.flush();
    }


    public static void main(String[] args) {
        GridCreator creator = new GridCreator(9,9);
        long t0 = System.nanoTime();

        creator.printGrid();
        System.out.println((System.nanoTime() - t0)/1000000);
    }

}
