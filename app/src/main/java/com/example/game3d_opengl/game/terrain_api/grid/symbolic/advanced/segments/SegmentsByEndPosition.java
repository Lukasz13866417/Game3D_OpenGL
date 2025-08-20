package com.example.game3d_opengl.game.terrain_api.grid.symbolic.advanced.segments;

import com.example.game3d_opengl.game.terrain_api.grid.symbolic.GridSegment;

import java.util.Arrays;
import java.util.TreeSet;

public class SegmentsByEndPosition {

    private final boolean vertical;
    final TreeSet<GridSegment> tree;
    private final int nRows;
    private final int nCols;

    public SegmentsByEndPosition(int nRows, int nCols, boolean vertical) {
        this.nRows = nRows;
        this.nCols = nCols;
        this.vertical = vertical;
        if (vertical) {
            this.tree = new TreeSet<>((GridSegment a, GridSegment b) -> {
                if (a.col != b.col) {
                    return Integer.compare(a.col, b.col);
                }
                return Integer.compare(a.row + a.length, b.row + b.length); // End pos is actually row + len - 1
                // But I removed it from here for extra tiny optimization
            });
        } else {
            this.tree = new TreeSet<>((GridSegment a, GridSegment b) -> {
                if (a.row != b.row) {
                    return Integer.compare(a.row, b.row);
                }
                return Integer.compare(a.col + a.length, b.col + b.length);
            });
        }
    }

    public GridSegment[] reserve(int row, int col, int length) {

        GridSegment candidate = bestFit(row, col);
        assert candidate != null;
        int cStart = vertical ? candidate.row : candidate.col, start = vertical ? row : col;
        int cLength = candidate.length;
        int cOther = vertical ? candidate.col : candidate.row, other = vertical ? col : row;
        if (cStart > start || cOther != other || cStart + cLength - 1 < start + length - 1) {
            throw new IllegalArgumentException("No space available for this segment");
        }
        tree.remove(candidate);

        if (cStart == start) {
            int newLength = cLength - length;
            if (newLength != 0) {
                int newStart = cStart + length;
                GridSegment replacement = vertical ? GridSegment.GS(newStart, other, newLength) : GridSegment.GS(other, newStart, newLength);
                tree.add(replacement);
                return new GridSegment[]{candidate, replacement, null};
            }
            return new GridSegment[]{candidate, null, null};
        } else {
            int len1 = start - cStart;
            GridSegment replacement1 = null, replacement2 = null;
            if (len1 > 0) {
                replacement1 = vertical ? GridSegment.GS(cStart, cOther, len1) : GridSegment.GS(cOther, cStart, len1);
                tree.add(replacement1);
            }
            int len2 = cStart + cLength - 1 - (start + length - 1);
            if (len2 > 0) {
                int newStart = start + length;
                replacement2 = vertical ? GridSegment.GS(newStart, cOther, len2) : GridSegment.GS(cOther, newStart, len2);
                tree.add(replacement2);
            }
            return new GridSegment[]{candidate, replacement1, replacement2};
        }

    }

    public void insert(int row, int col, int length) {
        tree.add(new GridSegment(row, col, length));
    }

    private GridSegment bestFit(int row, int col) {
        GridSegment dummy = new GridSegment(row, col, 1);
        GridSegment candidate = tree.ceiling(dummy);
        if (candidate == null) {
            return null;
        }
        if (vertical) {
            if (candidate.col == col && candidate.row <= row && row <= candidate.row + candidate.length - 1) {
                return candidate;
            }
        } else {
            if (candidate.row == row && candidate.col <= col && col <= candidate.col + candidate.length - 1) {
                return candidate;
            }
        }
        return null;
    }

    void printGrid(){
        char[][] grid = new char[nRows][nCols];
        System.out.println("Rows: "+nRows+" | "+"Cols: "+nCols);
        for(int r=0;r<nRows;r++){
            for(int c=0;c<nCols;c++){
                grid[r][c] = '#';
            }
        }
        for(GridSegment seg : tree){
            int row = seg.row-1, col = seg.col-1, len = seg.length;
            for(int i=0;i<len;++i){
                if(vertical){
                    grid[row + i][col] = '.';
                }else{
                    grid[row][col+i] = '.';
                }
            }
        }
        for(int r=0;r<nRows;r++){
            System.out.println(r+" "+ Arrays.toString(grid[r]));
        }
    }

}