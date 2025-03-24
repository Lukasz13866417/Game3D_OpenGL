package com.example.game3d_opengl.game.terrain.grid.symbolic;

public class GridSegment {
    public final int row, col, length;
    public GridSegment(int row, int col, int length) {
        this.row = row;
        this.col = col;
        this.length = length;
    }

    @Override
    public String toString() {
        return "(r" + row + ", c" + col + ", l" + length + ")";
    }

    public static GridSegment GS(int row, int col, int length) {
        return new GridSegment(row, col, length);
    }

    @Override
    public boolean equals(Object other) {
        if(!(other instanceof GridSegment)){
            return false;
        }
        return row == ((GridSegment) other).row && col == ((GridSegment) other).col && length == ((GridSegment) other).length;
    }
}
