package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.basic;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.BaseGridCreator;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;

/**
 * Basic grid creator that mirrors the constructor and parent-propagation
 * behavior of AdvancedGridCreator but without validation or random helpers.
 */
public class BasicGridCreator implements BaseGridCreator {

    private final int nRows, nCols;
    private final GridCreatorWrapper parent;
    private final int parentRowOffset;
    private final boolean propagateToParent;

    public BasicGridCreator(int nRows, int nCols, GridCreatorWrapper parentGrid, int parentRowOffset) {
        this(nRows, nCols, parentGrid, parentRowOffset, true);
    }

    public BasicGridCreator(int nRows, int nCols, GridCreatorWrapper parentGrid,
                            int parentRowOffset, boolean propagateToParent) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.parent = parentGrid;
        this.parentRowOffset = parentRowOffset;
        this.propagateToParent = propagateToParent;
    }

    @Override
    public GridSegment reserveVertical(int row, int col, int length) {
        assert row <= nRows;
        assert row >= 1;
        assert col <= nCols;
        assert col >= 1;
        if (propagateToParent && parent != null) {
            parent.reserveVertical(row + parentRowOffset, col, length);
        }
        return new GridSegment(row, col, length);
    }

    @Override
    public GridSegment reserveHorizontal(int row, int col, int length) {
        assert row <= nRows;
        assert row >= 1;
        assert col <= nCols;
        assert col >= 1;
        if (propagateToParent && parent != null) {
            parent.reserveHorizontal(row + parentRowOffset, col, length);
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
        BaseGridCreator parentContent = parent == null ? null : parent.getContent();
        if (parentContent != null) {
            System.out.println("Parent: " + parentContent.getClass().getSimpleName());
            parentContent.printMetaData();
        } else {
            System.out.println("Parent: null");
        }
    }

}
