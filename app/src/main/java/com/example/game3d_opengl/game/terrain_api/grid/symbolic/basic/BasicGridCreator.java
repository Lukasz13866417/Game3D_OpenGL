package com.example.game3d_opengl.game.terrain_api.grid.symbolic.basic;

import com.example.game3d_opengl.game.terrain_api.grid.symbolic.BaseGridCreator;
import com.example.game3d_opengl.game.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain_api.grid.symbolic.GridSegment;

/**
 * Basic grid creator that mirrors the constructor and parent-propagation
 * behavior of AdvancedGridCreator but without validation or random helpers.
 */
public class BasicGridCreator implements BaseGridCreator {

    private final int nRows, nCols;
    private final GridCreatorWrapper parent;
    private final int parentRowOffset;

    public BasicGridCreator(int nRows, int nCols, GridCreatorWrapper parentGrid, int parentRowOffset) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.parent = parentGrid;
        this.parentRowOffset = parentRowOffset;
    }

    @Override
    public GridSegment reserveVertical(int row, int col, int length) {
        if (parent != null && parent.content != null) {
            parent.content.reserveVertical(row + parentRowOffset, col, length);
        }
        return new GridSegment(row, col, length);
    }

    @Override
    public GridSegment reserveHorizontal(int row, int col, int length) {
        if (parent != null && parent.content != null) {
            parent.content.reserveHorizontal(row + parentRowOffset, col, length);
        }
        return new GridSegment(row, col, length);
    }

    @Override
    public void destroy() {
        // no-op
    }

    @Override
    public void printGrid() {
        // no-op
    }

    @Override
    public void printMetaData() {
        System.out.println("GRID METADATA (Basic): ");
        System.out.println("rows: " + nRows + " cols: " + nCols);
        if (parent != null && parent.content != null) {
            System.out.println("Parent: " + parent.content.getClass().getSimpleName());
            parent.content.printMetaData();
        } else {
            System.out.println("Parent: null");
        }
    }
}
