package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments;

@FunctionalInterface
public interface GridSegmentSink {
    void accept(int row, int col, int length);
}
