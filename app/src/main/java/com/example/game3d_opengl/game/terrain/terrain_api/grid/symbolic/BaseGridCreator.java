package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic;

/**
 * Minimal grid reservation API used by addon placement logic.
 * Randomized helpers are intentionally omitted.
 */
public interface BaseGridCreator {

	GridSegment reserveVertical(int row, int col, int length);

	GridSegment reserveHorizontal(int row, int col, int length);

	void destroy();

	void printGrid();

	void printMetaData();
}


