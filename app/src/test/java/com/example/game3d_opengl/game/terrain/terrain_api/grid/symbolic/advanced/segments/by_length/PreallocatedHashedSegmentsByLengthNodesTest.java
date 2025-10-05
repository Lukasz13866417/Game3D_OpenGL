package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_length;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_length.segtree_implementation.PreallocatedHashedSegmentsByLengthNodes;
import org.junit.Test;

import static org.junit.Assert.*;

public class PreallocatedHashedSegmentsByLengthNodesTest {

    @Test
    public void count_and_kth_horizontal_segments() {
        PreallocatedHashedSegmentsByLengthNodes s = new PreallocatedHashedSegmentsByLengthNodes(5, 7, false);
        // row 2: [1..5] length 5
        s.insert(2, 1, 5);
        // row 2: [6..7] length 2
        s.insert(2, 6, 2);
        // row 4: [2..4] length 3
        s.insert(4, 2, 3);

        // For space size 3:
        // segments contribute: len - (3-1) each -> [5->3], [2->0], [3->1] => total 4 spaces
        assertEquals(4, s.countFittingSpaces(3));

        // Order is by hash = length*R*C + (row-1)*C + (col-1).
        // For len=3, candidates: (4,2,3) has smaller hash than (2,1,3) blocks.
        GridSegment k1 = s.getKthFittingSpace(3, 1);
        assertEquals(new GridSegment(4, 2, 3), k1);
        GridSegment k2 = s.getKthFittingSpace(3, 2);
        assertEquals(new GridSegment(2, 1, 3), k2);
        GridSegment k3 = s.getKthFittingSpace(3, 3);
        assertEquals(new GridSegment(2, 2, 3), k3);
        GridSegment k4 = s.getKthFittingSpace(3, 4);
        assertEquals(new GridSegment(2, 3, 3), k4);
    }

    @Test
    public void count_and_kth_vertical_segments() {
        PreallocatedHashedSegmentsByLengthNodes s = new PreallocatedHashedSegmentsByLengthNodes(8, 4, true);
        // col 1: rows [1..4] len 4
        s.insert(1, 1, 4);
        // col 3: rows [3..8] len 6
        s.insert(3, 3, 6);

        // For space size 5: contributions -> [4->0], [6->2] => total 2 spaces
        assertEquals(2, s.countFittingSpaces(5));

        GridSegment v1 = s.getKthFittingSpace(5, 1); // first fitting slot inside (3,3,len6)
        assertEquals(new GridSegment(3, 3, 5), v1);
        GridSegment v2 = s.getKthFittingSpace(5, 2); // next slot starts at row 4
        assertEquals(new GridSegment(4, 3, 5), v2);
    }

    @Test
    public void insert_delete_affects_counts() {
        PreallocatedHashedSegmentsByLengthNodes s = new PreallocatedHashedSegmentsByLengthNodes(6, 6, false);
        s.insert(1, 1, 3);
        s.insert(1, 5, 2);
        s.insert(2, 2, 4);
        assertEquals(3, s.countFittingSpaces(3));

        s.delete(1, 1, 3);
        assertEquals(2, s.countFittingSpaces(3));

        s.delete(2, 2, 4);
        assertEquals(0, s.countFittingSpaces(3));
    }

    @Test
    public void destroy_allows_reuse_without_crash() {
        PreallocatedHashedSegmentsByLengthNodes s = new PreallocatedHashedSegmentsByLengthNodes(4, 4, false);
        s.insert(1, 1, 4);
        s.insert(2, 1, 3);
        assertTrue(s.countFittingSpaces(2) > 0);
        s.destroy();
        // After destroy, a new instance should work fine (shared pool is reused)
        PreallocatedHashedSegmentsByLengthNodes t = new PreallocatedHashedSegmentsByLengthNodes(4, 4, true);
        t.insert(1, 1, 4);
        assertEquals(3, t.countFittingSpaces(2));
    }
}


