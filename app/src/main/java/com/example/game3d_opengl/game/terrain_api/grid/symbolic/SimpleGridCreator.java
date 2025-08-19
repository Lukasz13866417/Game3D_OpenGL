package com.example.game3d_opengl.game.terrain_api.grid.symbolic;

/**
 * Trivial non-validating grid creator: simply echoes requested segments without checks.
 */
public class SimpleGridCreator implements BaseGridCreator {

	@Override
	public GridSegment reserveVertical(int row, int col, int length) {
		return new GridSegment(row, col, length);
	}

	@Override
	public GridSegment reserveHorizontal(int row, int col, int length) {
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
		// no-op
	}
}


