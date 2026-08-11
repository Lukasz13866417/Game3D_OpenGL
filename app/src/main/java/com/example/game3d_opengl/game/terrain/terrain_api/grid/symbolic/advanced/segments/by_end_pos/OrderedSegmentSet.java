package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.GridSegmentSink;

public interface OrderedSegmentSet {
    void add(GridSegment seg);

    default void add(int row, int col, int length) {
        add(new GridSegment(row, col, length));
    }

    boolean remove(GridSegment seg);

    GridSegment ceiling(GridSegment key);

    GridSegment pollFirst();

    boolean isEmpty();

    GridSegment[] toSortedArray();

    default void forEachSorted(GridSegmentSink sink) {
        GridSegment[] segments = toSortedArray();
        for (GridSegment seg : segments) {
            sink.accept(seg.row, seg.col, seg.length);
        }
    }
}

