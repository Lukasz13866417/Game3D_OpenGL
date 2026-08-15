package com.example.game3d.authoring.grid.symbolic.advanced.segments.by_end_pos;

import com.example.game3d.authoring.grid.symbolic.GridSegment;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.GridSegmentSink;

import java.util.TreeSet;

final class TreeSetOrderedSegmentSet implements OrderedSegmentSet {
    private final TreeSet<GridSegment> tree;

    TreeSetOrderedSegmentSet(boolean vertical) {
        if (vertical) {
            tree = new TreeSet<>((a, b) -> {
                if (a.col != b.col) {
                    return Integer.compare(a.col, b.col);
                }
                return Integer.compare(a.row + a.length, b.row + b.length);
            });
        } else {
            tree = new TreeSet<>((a, b) -> {
                if (a.row != b.row) {
                    return Integer.compare(a.row, b.row);
                }
                return Integer.compare(a.col + a.length, b.col + b.length);
            });
        }
    }

    @Override
    public void add(GridSegment seg) {
        tree.add(seg);
    }

    @Override
    public void add(int row, int col, int length) {
        tree.add(new GridSegment(row, col, length));
    }

    @Override
    public boolean remove(GridSegment seg) {
        return tree.remove(seg);
    }

    @Override
    public GridSegment ceiling(GridSegment key) {
        return tree.ceiling(key);
    }

    @Override
    public GridSegment pollFirst() {
        return tree.pollFirst();
    }

    @Override
    public boolean isEmpty() {
        return tree.isEmpty();
    }

    @Override
    public GridSegment[] toSortedArray() {
        return tree.toArray(new GridSegment[0]);
    }

    @Override
    public void forEachSorted(GridSegmentSink sink) {
        for (GridSegment seg : tree) {
            sink.accept(seg.row, seg.col, seg.length);
        }
    }
}
