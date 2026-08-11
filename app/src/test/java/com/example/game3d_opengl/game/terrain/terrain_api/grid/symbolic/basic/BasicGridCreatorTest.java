package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.basic;

import static org.junit.Assert.assertArrayEquals;

import java.util.ArrayList;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.RetainedGridSummary;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.AdvancedGridCreator;

import org.junit.Test;

public class BasicGridCreatorTest {
    @Test
    public void retained_summary_combines_child_summary_with_local_reservations() {
        AdvancedGridCreator child = new AdvancedGridCreator(2, 4);
        child.reserveHorizontal(2, 2, 2);

        BasicGridCreator creator = new BasicGridCreator(
                7,
                4,
                null,
                0,
                true,
                new int[0][2],
                new RetainedGridSummary[]{child},
                new int[]{0},
                1
        );
        try {
            creator.reserveVertical(4, 4, 2);

            assertArrayEquals(
                    new GridSegment[]{
                            new GridSegment(1, 1, 4),
                            new GridSegment(2, 1, 1),
                            new GridSegment(2, 4, 1),
                            new GridSegment(3, 1, 4),
                            new GridSegment(4, 1, 3),
                            new GridSegment(5, 1, 3),
                            new GridSegment(6, 1, 4),
                            new GridSegment(7, 1, 4)
                    },
                    collectHorizontalSegments(creator)
            );
        } finally {
            creator.destroy();
        }
    }

    @Test
    public void basic_child_summary_is_inlined_into_basic_parent() {
        BasicGridCreator child = new BasicGridCreator(3, 4, null, 0);
        child.reserveVertical(1, 2, 3);

        BasicGridCreator parent = new BasicGridCreator(
                5,
                4,
                null,
                0,
                true,
                new int[0][2],
                new RetainedGridSummary[]{child},
                new int[]{1},
                1
        );
        try {
            parent.reserveHorizontal(5, 1, 2);

            assertArrayEquals(
                    new GridSegment[]{
                            new GridSegment(1, 1, 4),
                            new GridSegment(2, 1, 1),
                            new GridSegment(2, 3, 2),
                            new GridSegment(3, 1, 1),
                            new GridSegment(3, 3, 2),
                            new GridSegment(4, 1, 1),
                            new GridSegment(4, 3, 2),
                            new GridSegment(5, 3, 2)
                    },
                    collectHorizontalSegments(parent)
            );
        } finally {
            parent.destroy();
        }
    }

    @Test
    public void inlined_basic_child_preserves_blocked_rows_in_vertical_segments() {
        BasicGridCreator child = new BasicGridCreator(
                3,
                4,
                null,
                0,
                true,
                new int[][]{{2, 2}},
                null,
                null,
                0
        );
        child.reserveVertical(1, 3, 2);

        BasicGridCreator parent = new BasicGridCreator(
                6,
                4,
                null,
                0,
                true,
                new int[0][2],
                new RetainedGridSummary[]{child},
                new int[]{1},
                1
        );
        try {
            assertArrayEquals(
                    new GridSegment[]{
                            new GridSegment(1, 1, 2),
                            new GridSegment(4, 1, 3),
                            new GridSegment(1, 2, 2),
                            new GridSegment(4, 2, 3),
                            new GridSegment(1, 3, 1),
                            new GridSegment(4, 3, 3),
                            new GridSegment(1, 4, 2),
                            new GridSegment(4, 4, 3)
                    },
                    collectVerticalSegments(parent)
            );
        } finally {
            parent.destroy();
        }
    }

    @Test
    public void nested_basic_inlining_keeps_advanced_leaf_summary_and_local_ops() {
        AdvancedGridCreator leaf = new AdvancedGridCreator(2, 4);
        leaf.reserveHorizontal(1, 1, 2);

        BasicGridCreator inner = new BasicGridCreator(
                4,
                4,
                null,
                0,
                true,
                new int[][]{{4, 4}},
                new RetainedGridSummary[]{leaf},
                new int[]{1},
                1
        );
        inner.reserveVertical(1, 4, 2);

        BasicGridCreator outer = new BasicGridCreator(
                6,
                4,
                null,
                0,
                true,
                new int[0][2],
                new RetainedGridSummary[]{inner},
                new int[]{1},
                1
        );
        try {
            outer.reserveHorizontal(6, 2, 3);

            assertArrayEquals(
                    new GridSegment[]{
                            new GridSegment(1, 1, 4),
                            new GridSegment(2, 1, 3),
                            new GridSegment(3, 3, 1),
                            new GridSegment(4, 1, 4),
                            new GridSegment(6, 1, 1)
                    },
                    collectHorizontalSegments(outer)
            );
        } finally {
            outer.destroy();
        }
    }

    private static GridSegment[] collectHorizontalSegments(RetainedGridSummary summary) {
        ArrayList<GridSegment> segments = new ArrayList<>();
        summary.forEachHorizontalFreeSegment((row, col, length) ->
                segments.add(new GridSegment(row, col, length))
        );
        return segments.toArray(new GridSegment[0]);
    }

    private static GridSegment[] collectVerticalSegments(RetainedGridSummary summary) {
        ArrayList<GridSegment> segments = new ArrayList<>();
        summary.forEachVerticalFreeSegment((row, col, length) ->
                segments.add(new GridSegment(row, col, length))
        );
        return segments.toArray(new GridSegment[0]);
    }
}
