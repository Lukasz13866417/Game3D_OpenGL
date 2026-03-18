package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.BaseGridCreator;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridCreatorWrapper;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.GridSegment;
import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.by_end_pos.EndPosTreeKind;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class AdvancedGridCreatorTest {
    private static final Object PRINT_CAPTURE_LOCK = new Object();

    private static String[] capturePrintedGridLines(BaseGridCreator creator) {
        synchronized (PRINT_CAPTURE_LOCK) {
            PrintStream originalOut = System.out;
            ByteArrayOutputStream capturedBytes = new ByteArrayOutputStream();
            PrintStream capture = new PrintStream(capturedBytes);
            try {
                System.setOut(capture);
                creator.printGrid();
            } finally {
                System.setOut(originalOut);
                capture.close();
            }

            String text = new String(capturedBytes.toByteArray(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            while (text.endsWith("\n")) {
                text = text.substring(0, text.length() - 1);
            }
            return text.split("\n");
        }
    }

    private static void assertPrintedGrid(
            BaseGridCreator creator, int expectedRows, int expectedCols, String... expectedGridRows
    ) {
        String[] lines = capturePrintedGridLines(creator);
        assertEquals(expectedGridRows.length + 2, lines.length);
        assertTrue(lines[0].startsWith("Horizontal hashCode(): "));
        assertTrue(lines[1].startsWith("Rows: " + expectedRows + " | Cols: " + expectedCols + " "));
        assertTrue(lines[1].endsWith(",false"));
        for (int i = 0; i < expectedGridRows.length; ++i) {
            assertEquals(expectedGridRows[i], lines[i + 2]);
        }
    }

    private static void destroyIfMaterialized(GridCreatorWrapper wrapper) {
        BaseGridCreator content = wrapper.getContent();
        if (content != null) {
            content.destroy();
        }
        wrapper.releaseRetainedAdvancedCreator();
    }


    @Test
    public void reserveVertical_blocks_same_cells_for_horizontal_reservations() {
        AdvancedGridCreator creator = new AdvancedGridCreator(4, 4);
        try {
            creator.reserveVertical(1, 2, 3);
            GridSegment onlyFit = creator.reserveRandomFittingHorizontal(4);
            assertEquals(new GridSegment(4, 1, 4), onlyFit);
        } finally {
            creator.destroy();
        }
    }

    @Test
    public void reserveHorizontal_on_taken_cells_throws() {
        AdvancedGridCreator creator = new AdvancedGridCreator(4, 4);
        try {
            creator.reserveVertical(1, 2, 4);
            try {
                creator.reserveHorizontal(2, 1, 3);
                fail("Expected reserveHorizontal to reject overlapping reserved cells.");
            } catch (IllegalArgumentException expected) {
                // Expected.
            }
        } finally {
            creator.destroy();
        }
    }

    @Test
    public void printGrid_outputs_expected_rows_after_non_random_reservations() {
        AdvancedGridCreator creator = new AdvancedGridCreator(4, 5);
        try {
            creator.reserveVertical(1, 2, 3);
            creator.reserveHorizontal(4, 3, 2);

            assertPrintedGrid(
                    creator,
                    4,
                    5,
                    "0 [., #, ., ., .]",
                    "1 [., #, ., ., .]",
                    "2 [., #, ., ., .]",
                    "3 [., ., #, #, .]"
            );
        } finally {
            creator.destroy();
        }
    }

    @Test
    public void reserveKRandomFields_returns_sorted_unique_single_cells() {
        AdvancedGridCreator creator = new AdvancedGridCreator(5, 5);
        try {
            GridSegment[] reserved = creator.reserveKRandomFields(6);
            assertEquals(6, reserved.length);

            Set<String> seen = new HashSet<>();
            GridSegment prev = null;
            for (GridSegment seg : reserved) {
                assertEquals(1, seg.length);
                assertTrue(seg.row >= 1 && seg.row <= 5);
                assertTrue(seg.col >= 1 && seg.col <= 5);
                assertTrue(seen.add(seg.row + ":" + seg.col));
                if (prev != null) {
                    assertTrue(
                            prev.row < seg.row || (prev.row == seg.row && prev.col <= seg.col)
                    );
                }
                prev = seg;
            }
        } finally {
            creator.destroy();
        }
    }

    @Test
    public void exportHorizontalFreeSegments_roundTrip_through_rebuild() {
        AdvancedGridCreator original = new AdvancedGridCreator(6, 5);
        AdvancedGridCreator rebuilt = null;
        try {
            original.reserveVertical(2, 2, 3);
            original.reserveHorizontal(6, 1, 2);
            original.reserveHorizontal(1, 4, 2);

            rebuilt = AdvancedGridCreator.createFromHorizontalFreeSegments(
                    original.nRows,
                    original.nCols,
                    null,
                    0,
                    EndPosTreeKind.POOLED_TREAP,
                    true,
                    original.exportHorizontalFreeSegments()
            );

            assertArrayEquals(
                    original.exportHorizontalFreeSegments(),
                    rebuilt.exportHorizontalFreeSegments()
            );
        } finally {
            original.destroy();
            if (rebuilt != null) {
                rebuilt.destroy();
            }
        }
    }

    @Test
    public void wrapper_materializes_parent_from_child_creator() {
        GridCreatorWrapper parentWrapper = new GridCreatorWrapper();
        GridCreatorWrapper childWrapper = new GridCreatorWrapper();
        try {
            parentWrapper.configureStructure(
                    true,
                    5,
                    4,
                    null,
                    0,
                    EndPosTreeKind.POOLED_TREAP,
                    true,
                    new int[0][2]
            );
            childWrapper.configureStructure(
                    true,
                    3,
                    4,
                    parentWrapper,
                    1,
                    EndPosTreeKind.POOLED_TREAP,
                    true,
                    new int[0][2]
            );
            parentWrapper.addChildWrapper(childWrapper, 1);
            childWrapper.materializeIfNeeded();
            ((AdvancedGridCreator) childWrapper.getContent()).reserveHorizontal(2, 2, 2);
            childWrapper.finishAddonPhase();
            parentWrapper.materializeIfNeeded();

            AdvancedGridCreator parent = (AdvancedGridCreator) parentWrapper.getContent();
            assertNotNull(parent);

            try {
                parent.reserveHorizontal(3, 2, 2);
                fail("Expected parent summary build to preserve child reservations.");
            } catch (IllegalArgumentException expected) {
                // Expected.
            }

            GridSegment freeTopRow = parent.reserveRandomFittingHorizontal(4);
            assertEquals(1, freeTopRow.col);
            assertEquals(4, freeTopRow.length);
            assertTrue(
                    freeTopRow.row == 1
                            || freeTopRow.row == 2
                            || freeTopRow.row == 4
                            || freeTopRow.row == 5
            );

            parentWrapper.finishAddonPhase();
        } finally {
            destroyIfMaterialized(parentWrapper);
            destroyIfMaterialized(childWrapper);
        }
    }

    @Test
    public void wrapper_build_merges_vertical_runs_across_adjacent_children() {
        GridCreatorWrapper parentWrapper = new GridCreatorWrapper();
        GridCreatorWrapper firstChild = new GridCreatorWrapper();
        GridCreatorWrapper secondChild = new GridCreatorWrapper();
        parentWrapper.configureStructure(
                true,
                4,
                1,
                null,
                0,
                EndPosTreeKind.POOLED_TREAP,
                true,
                new int[0][2]
        );
        firstChild.configureStructure(
                true,
                2,
                1,
                parentWrapper,
                0,
                EndPosTreeKind.POOLED_TREAP,
                true,
                new int[0][2]
        );
        secondChild.configureStructure(
                true,
                2,
                1,
                parentWrapper,
                2,
                EndPosTreeKind.POOLED_TREAP,
                true,
                new int[0][2]
        );
        parentWrapper.addChildWrapper(firstChild, 0);
        parentWrapper.addChildWrapper(secondChild, 2);

        try {
            firstChild.materializeIfNeeded();
            firstChild.finishAddonPhase();
            secondChild.materializeIfNeeded();
            secondChild.finishAddonPhase();

            parentWrapper.materializeIfNeeded();

            AdvancedGridCreator parent = (AdvancedGridCreator) parentWrapper.getContent();
            assertNotNull(parent);
            assertEquals(new GridSegment(1, 1, 4), parent.reserveRandomFittingVertical(4));

            parentWrapper.finishAddonPhase();
        } finally {
            destroyIfMaterialized(parentWrapper);
            destroyIfMaterialized(firstChild);
            destroyIfMaterialized(secondChild);
        }
    }

    @Test
    public void createFromChildren_builds_parent_directly_from_child_creators() {
        AdvancedGridCreator firstChild = new AdvancedGridCreator(2, 4);
        AdvancedGridCreator secondChild = new AdvancedGridCreator(2, 4);
        AdvancedGridCreator parent = null;
        try {
            firstChild.reserveHorizontal(1, 2, 2);
            secondChild.reserveVertical(1, 4, 2);

            parent = AdvancedGridCreator.createFromChildren(
                    5,
                    4,
                    null,
                    0,
                    EndPosTreeKind.POOLED_TREAP,
                    true,
                    new int[][]{{3, 3}},
                    new AdvancedGridCreator[]{firstChild, secondChild},
                    new int[]{0, 2},
                    2
            );

            assertArrayEquals(
                    new GridSegment[]{
                            GridSegment.GS(1, 1, 1),
                            GridSegment.GS(1, 4, 1),
                            GridSegment.GS(2, 1, 4),
                            GridSegment.GS(4, 1, 3),
                            GridSegment.GS(5, 1, 4)
                    },
                    parent.exportHorizontalFreeSegments()
            );

            assertArrayEquals(
                    new GridSegment[]{
                            GridSegment.GS(1, 1, 2),
                            GridSegment.GS(4, 1, 2),
                            GridSegment.GS(2, 2, 1),
                            GridSegment.GS(4, 2, 2),
                            GridSegment.GS(2, 3, 1),
                            GridSegment.GS(4, 3, 2),
                            GridSegment.GS(1, 4, 2),
                            GridSegment.GS(5, 4, 1)
                    },
                    parent.exportVerticalFreeSegments()
            );
        } finally {
            firstChild.destroy();
            secondChild.destroy();
            if (parent != null) {
                parent.destroy();
            }
        }
    }

    @Test
    public void createFromChildren_builds_parent_directly_from_child_creators_for_all_backends() {
        for (EndPosTreeKind kind : EndPosTreeKind.values()) {
            AdvancedGridCreator firstChild = new AdvancedGridCreator(2, 4, kind);
            AdvancedGridCreator secondChild = new AdvancedGridCreator(2, 4, kind);
            AdvancedGridCreator parent = null;
            try {
                firstChild.reserveHorizontal(1, 2, 2);
                secondChild.reserveVertical(1, 4, 2);

                parent = AdvancedGridCreator.createFromChildren(
                        5,
                        4,
                        null,
                        0,
                        kind,
                        true,
                        new int[][]{{3, 3}},
                        new AdvancedGridCreator[]{firstChild, secondChild},
                        new int[]{0, 2},
                        2
                );

                assertArrayEquals(
                        kind.name(),
                        new GridSegment[]{
                                GridSegment.GS(1, 1, 1),
                                GridSegment.GS(1, 4, 1),
                                GridSegment.GS(2, 1, 4),
                                GridSegment.GS(4, 1, 3),
                                GridSegment.GS(5, 1, 4)
                        },
                        parent.exportHorizontalFreeSegments()
                );

                assertArrayEquals(
                        kind.name(),
                        new GridSegment[]{
                                GridSegment.GS(1, 1, 2),
                                GridSegment.GS(4, 1, 2),
                                GridSegment.GS(2, 2, 1),
                                GridSegment.GS(4, 2, 2),
                                GridSegment.GS(2, 3, 1),
                                GridSegment.GS(4, 3, 2),
                                GridSegment.GS(1, 4, 2),
                                GridSegment.GS(5, 4, 1)
                        },
                        parent.exportVerticalFreeSegments()
                );
            } finally {
                firstChild.destroy();
                secondChild.destroy();
                if (parent != null) {
                    parent.destroy();
                }
            }
        }
    }

    @Test
    public void wrapper_tree_prints_expected_grids_across_multiple_levels() {
        GridCreatorWrapper root = new GridCreatorWrapper();
        GridCreatorWrapper childA = new GridCreatorWrapper();
        GridCreatorWrapper childB = new GridCreatorWrapper();
        GridCreatorWrapper grandchildA1 = new GridCreatorWrapper();
        GridCreatorWrapper grandchildA2 = new GridCreatorWrapper();
        GridCreatorWrapper grandchildB1 = new GridCreatorWrapper();

        root.configureStructure(
                true,
                9,
                5,
                null,
                0,
                EndPosTreeKind.POOLED_TREAP,
                true,
                new int[0][2]
        );
        childA.configureStructure(
                true,
                4,
                5,
                root,
                1,
                EndPosTreeKind.POOLED_TREAP,
                true,
                new int[0][2]
        );
        childB.configureStructure(
                true,
                3,
                5,
                root,
                6,
                EndPosTreeKind.POOLED_TREAP,
                true,
                new int[0][2]
        );
        grandchildA1.configureStructure(
                true,
                2,
                5,
                childA,
                1,
                EndPosTreeKind.POOLED_TREAP,
                true,
                new int[0][2]
        );
        grandchildA2.configureStructure(
                true,
                1,
                5,
                childA,
                3,
                EndPosTreeKind.POOLED_TREAP,
                true,
                new int[0][2]
        );
        grandchildB1.configureStructure(
                true,
                2,
                5,
                childB,
                0,
                EndPosTreeKind.POOLED_TREAP,
                true,
                new int[0][2]
        );
        childA.addChildWrapper(grandchildA1, 1);
        childA.addChildWrapper(grandchildA2, 3);
        childB.addChildWrapper(grandchildB1, 0);
        root.addChildWrapper(childA, 1);
        root.addChildWrapper(childB, 6);

        try {
            grandchildA1.materializeIfNeeded();
            grandchildA1.reserveHorizontal(1, 2, 3);
            grandchildA1.reserveVertical(1, 5, 2);
            grandchildA1.finishAddonPhase();

            grandchildA2.materializeIfNeeded();
            grandchildA2.reserveVertical(1, 1, 1);
            grandchildA2.finishAddonPhase();

            grandchildB1.materializeIfNeeded();
            grandchildB1.reserveVertical(1, 3, 2);
            grandchildB1.finishAddonPhase();

            childA.reserveVertical(2, 1, 2);
            childA.reserveHorizontal(4, 2, 2);
            childA.materializeIfNeeded();
            assertPrintedGrid(
                    childA.getContent(),
                    4,
                    5,
                    "0 [., ., ., ., .]",
                    "1 [#, #, #, #, #]",
                    "2 [#, ., ., ., #]",
                    "3 [#, #, #, ., .]"
            );
            childA.finishAddonPhase();

            childB.reserveHorizontal(3, 1, 4);
            childB.reserveVertical(1, 5, 3);
            childB.materializeIfNeeded();
            assertPrintedGrid(
                    childB.getContent(),
                    3,
                    5,
                    "0 [., ., #, ., #]",
                    "1 [., ., #, ., #]",
                    "2 [#, #, #, #, #]"
            );
            childB.finishAddonPhase();

            root.reserveVertical(1, 5, 2);
            root.reserveHorizontal(6, 2, 3);
            root.materializeIfNeeded();
            assertPrintedGrid(
                    root.getContent(),
                    9,
                    5,
                    "0 [., ., ., ., #]",
                    "1 [., ., ., ., #]",
                    "2 [#, #, #, #, #]",
                    "3 [#, ., ., ., #]",
                    "4 [#, #, #, ., .]",
                    "5 [., #, #, #, .]",
                    "6 [., ., #, ., #]",
                    "7 [., ., #, ., #]",
                    "8 [#, #, #, #, #]"
            );
            root.finishAddonPhase();
        } finally {
            destroyIfMaterialized(root);
            destroyIfMaterialized(childA);
            destroyIfMaterialized(childB);
            destroyIfMaterialized(grandchildA1);
            destroyIfMaterialized(grandchildA2);
            destroyIfMaterialized(grandchildB1);
        }
    }
}
