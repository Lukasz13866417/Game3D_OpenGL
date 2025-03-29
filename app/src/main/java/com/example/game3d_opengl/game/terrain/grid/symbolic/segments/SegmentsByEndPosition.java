package com.example.game3d_opengl.game.terrain.grid.symbolic.segments;


import static com.example.game3d_opengl.game.terrain.grid.symbolic.GridSegment.GS;

import com.example.game3d_opengl.game.terrain.grid.symbolic.GridSegment;

import java.util.TreeSet;

class SegmentsByEndPosition {

    private final boolean vertical;
    final TreeSet<GridSegment> tree;

    public SegmentsByEndPosition(int nRows, int nCols, boolean vertical) {
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
                GridSegment replacement = vertical ? GS(newStart, other, newLength) : GS(other, newStart, newLength);
                tree.add(replacement);
                return new GridSegment[]{candidate, replacement, null};
            }
            return new GridSegment[]{candidate, null, null};
        } else {
            int len1 = start - cStart;
            GridSegment replacement1 = null, replacement2 = null;
            if (len1 > 0) {
                replacement1 = vertical ? GS(cStart, cOther, len1) : GS(cOther, cStart, len1);
                tree.add(replacement1);
            }
            int len2 = cStart + cLength - 1 - (start + length - 1);
            if (len2 > 0) {
                int newStart = start + length;
                replacement2 = vertical ? GS(newStart, cOther, len2) : GS(cOther, newStart, len2);
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

}