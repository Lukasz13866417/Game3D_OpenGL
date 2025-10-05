package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import org.junit.Test;

import static org.junit.Assert.*;

public class SegmentsByEndPositionTest {

    @Test
    public void reserve_horizontal_exact_at_start_removes_segment() {
        SegmentsByEndPosition s = new SegmentsByEndPosition(3, 10, false);
        s.insert(2, 1, 5); // row=2, cols 1..5

        GridSegment[] res = s.reserve(2, 1, 5);
        assertNotNull(res[0]);
        assertEquals(new GridSegment(2, 1, 5), res[0]);
        assertNull(res[1]);
        assertNull(res[2]);
    }

    @Test
    public void reserve_horizontal_from_start_leaves_right_remainder() {
        SegmentsByEndPosition s = new SegmentsByEndPosition(3, 10, false);
        s.insert(2, 1, 7); // row=2, cols 1..7

        GridSegment[] res = s.reserve(2, 1, 4); // reserve cols 1..4
        assertEquals(new GridSegment(2, 1, 7), res[0]);
        assertEquals(new GridSegment(2, 5, 3), res[1]); // remaining cols 5..7
        assertNull(res[2]);
    }

    @Test
    public void reserve_horizontal_in_middle_leaves_two_remainders() {
        SegmentsByEndPosition s = new SegmentsByEndPosition(3, 15, false);
        s.insert(1, 3, 10); // row=1, cols 3..12

        GridSegment[] res = s.reserve(1, 6, 4); // reserve cols 6..9
        assertEquals(new GridSegment(1, 3, 10), res[0]);
        assertEquals(new GridSegment(1, 3, 3), res[1]); // 3..5
        assertEquals(new GridSegment(1, 10, 3), res[2]); // 10..12
    }

    @Test
    public void reserve_vertical_exact_at_start_removes_segment() {
        SegmentsByEndPosition s = new SegmentsByEndPosition(10, 3, true);
        s.insert(1, 2, 5); // col=2, rows 1..5

        GridSegment[] res = s.reserve(1, 2, 5);
        assertEquals(new GridSegment(1, 2, 5), res[0]);
        assertNull(res[1]);
        assertNull(res[2]);
    }

    @Test
    public void reserve_vertical_from_middle_leaves_two_remainders() {
        SegmentsByEndPosition s = new SegmentsByEndPosition(20, 5, true);
        s.insert(3, 4, 10); // col=4, rows 3..12

        GridSegment[] res = s.reserve(7, 4, 4); // rows 7..10
        assertEquals(new GridSegment(3, 4, 10), res[0]);
        assertEquals(new GridSegment(3, 4, 4), res[1]); // 3..6
        assertEquals(new GridSegment(11, 4, 2), res[2]); // 11..12
    }
}


