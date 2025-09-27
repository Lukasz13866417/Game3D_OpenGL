package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.BaseGridCreator;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.PartialSegmentHandler;

public class AdvancedGridCreator implements BaseGridCreator {

    private final int nRows, nCols;
    private final PartialSegmentHandler vertical, horizontal;
    private final GridCreatorWrapper parent;
    private final int parentRowOffset;

    public AdvancedGridCreator(int nRows, int nCols, GridCreatorWrapper parentGrid, int parentRowOffset) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.horizontal = new PartialSegmentHandler(nRows, nCols, false);
        this.vertical = new PartialSegmentHandler(nRows, nCols, true);
        this.parentRowOffset = parentRowOffset;
        this.parent = parentGrid;
    }

    public AdvancedGridCreator(int nRows, int nCols) {
        this(nRows, nCols, null, 0);
    }

    @Override
    public GridSegment reserveVertical(int row, int col, int length) {
        if (parent != null && parent.content != null) {
            parent.content.reserveVertical(row + parentRowOffset, col, length);
        }
        vertical.reserve(row, col, length);
        for (int r = row; r < row + length; ++r) {
            horizontal.reserve(r, col, 1);
        }
        return new GridSegment(row, col, length);
    }

    @Override
    public GridSegment reserveHorizontal(int row, int col, int length) {
        if (parent != null && parent.content != null) {
            parent.content.reserveHorizontal(row + parentRowOffset, col, length);
        }
        horizontal.reserve(row, col, length);
        for (int c = col; c < col + length; ++c) {
            vertical.reserve(row, c, 1);
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
        }
        return res;
    }

    @Override
    public void destroy(){
        vertical.flush();
        horizontal.flush();
    }

    @Override
    public void printGrid(){
        vertical.printGrid();
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
