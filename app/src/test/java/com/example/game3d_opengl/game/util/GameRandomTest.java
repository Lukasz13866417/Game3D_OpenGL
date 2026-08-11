package com.example.game3d_opengl.game.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.util.Random;

import org.junit.Test;

public class GameRandomTest {
    @Test
    public void sampleDistinctGridPoints_returns_requested_unique_points_within_bounds()
            throws Exception {
        setRandomSeed(1234L);

        int[][] points = GameRandom.sampleDistinctGridPoints(4, 5, 7);

        assertEquals(7, points.length);
        boolean[][] seen = new boolean[5][6];
        int uniqueCount = 0;
        for (int[] point : points) {
            assertEquals(2, point.length);
            assertTrue(point[0] >= 1 && point[0] <= 4);
            assertTrue(point[1] >= 1 && point[1] <= 5);
            if (seen[point[0]][point[1]]) {
                fail("Duplicate point returned");
            }
            seen[point[0]][point[1]] = true;
            uniqueCount++;
        }
        assertEquals(7, uniqueCount);
    }

    @Test
    public void sampleDistinctGridPoints_returns_every_cell_when_count_matches_grid_size()
            throws Exception {
        setRandomSeed(9876L);

        int nRows = 3;
        int nCols = 4;
        int[][] points = GameRandom.sampleDistinctGridPoints(nRows, nCols, nRows * nCols);

        assertEquals(nRows * nCols, points.length);
        boolean[][] seen = new boolean[nRows + 1][nCols + 1];
        int uniqueCount = 0;
        for (int[] point : points) {
            if (seen[point[0]][point[1]]) {
                fail("Duplicate point returned");
            }
            seen[point[0]][point[1]] = true;
            uniqueCount++;
        }
        assertEquals(nRows * nCols, uniqueCount);
        for (int row = 1; row <= nRows; ++row) {
            for (int col = 1; col <= nCols; ++col) {
                assertTrue(seen[row][col]);
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void sampleDistinctGridPoints_rejects_requests_larger_than_grid() {
        GameRandom.sampleDistinctGridPoints(2, 2, 5);
    }

    private static void setRandomSeed(long seed) throws Exception {
        Field randomField = GameRandom.class.getDeclaredField("RANDOM");
        randomField.setAccessible(true);
        randomField.set(null, new Random(seed));
    }
}
