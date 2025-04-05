package com.example.game3d_opengl.game.terrain_api.grid.symbolic;

import com.example.game3d_opengl.game.terrain_api.grid.symbolic.segments.PartialSegmentHandler;

public class GridCreator {

    private final int nRows, nCols;
    private final PartialSegmentHandler vertical, horizontal;
    private final GridCreatorWrapper parent;
    private final int parentRowOffset;

    public GridCreator(int nRows, int nCols, GridCreatorWrapper parentGrid, int parentRowOffset) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.horizontal = new PartialSegmentHandler(nRows, nCols, false);
        this.vertical = new PartialSegmentHandler(nRows, nCols, true);
        this.parentRowOffset = parentRowOffset;
        this.parent = parentGrid;
    }

    public GridCreator(int nRows, int nCols) {
        this(nRows, nCols, null, 0);
    }

    public void reserveVertical(int row, int col, int length) {
        if (parent != null && parent.content != null) {
            parent.content.reserveHorizontal(row + parentRowOffset, col, length);
        }
        vertical.reserve(row, col, length);
        for (int r = row; r < row + length; r++) {
            horizontal.reserve(r, col, 1);
        }
    }

    public void reserveHorizontal(int row, int col, int length) {
        if (parent != null && parent.content != null) {
            parent.content.reserveHorizontal(row + parentRowOffset, col, length);
        }
        horizontal.reserve(row, col, length);
        for (int c = col; c < col + length; c++) {
            vertical.reserve(row, c, 1);
        }
    }

    public GridSegment reserveRandomFittingVertical(int length) {
        GridSegment res = vertical.reserveRandomFitting(length);
        if (parent != null && parent.content != null) {
            parent.content.reserveHorizontal(res.row + parentRowOffset, res.col, res.length);
        }
        for (int r = res.row; r < res.row + length; r++) {
            horizontal.reserve(r, res.col, 1);
        }
        return res;
    }

    public GridSegment reserveRandomFittingHorizontal(int length) {
        GridSegment res = horizontal.reserveRandomFitting(length);
        if (parent != null && parent.content != null) {
            parent.content.reserveVertical(res.row + parentRowOffset, res.col, res.length);
        }
        for (int c = res.col; c < res.col + length; c++) {
            vertical.reserve(res.row, c, 1);
        }
        return res;
    }

    public void destroy(){
        vertical.flush();
        horizontal.flush();
    }

    public void printGrid(){
        vertical.printGrid();
    }

    public void printMetaData(){
        System.out.println("GRID METADATA: ");
        System.out.println("rows: "+nRows+" cols: "+nCols);
        GridCreator p = parent != null ? parent.content : null;
        System.out.println("Parent: "+p);
        if(p != null) {
            p.printMetaData();
        }
    }


}
