package com.example.game3d.authoring.grid.symbolic.advanced.segments;

@FunctionalInterface
public interface GridSegmentSink {
    void accept(int row, int col, int length);
}
