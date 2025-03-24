package com.example.game3d_opengl.game.terrain.grid.symbolic.segments;


import com.example.game3d_opengl.game.terrain.grid.symbolic.GridSegment;

public interface SegmentsByLength {

    void insert(int row, int col, int length);

    void delete(int row, int col, int length);

    int countFittingSpaces(int spaceSize);

    GridSegment getKthFittingSpace(int spaceSize, int k);

    void freeArraysIfCleanedUp();
}
