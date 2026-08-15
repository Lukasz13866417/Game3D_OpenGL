package com.example.game3d.authoring.grid.symbolic.advanced.segments;

import static org.junit.Assert.assertArrayEquals;

import com.example.game3d.authoring.grid.symbolic.GridCreatorWrapper;
import com.example.game3d.authoring.grid.symbolic.GridSegment;
import com.example.game3d.authoring.grid.symbolic.advanced.AdvancedGridCreator;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.GridBuildScratch;
import com.example.game3d.authoring.grid.symbolic.advanced.segments.by_end_pos.EndPosTreeKind;

import org.junit.Test;

public class PartialSegmentHandlerTest {
    private static void destroyIfMaterialized(GridCreatorWrapper wrapper) {
        if (wrapper == null) {
            return;
        }
        if (wrapper.getContent() != null) {
            wrapper.getContent().destroy();
        }
        wrapper.releaseRetainedAdvancedCreator();
    }

    @Test
    public void horizontal_handler_from_children_preserves_child_rows_and_plain_rows() {
        GridCreatorWrapper parentWrapper = new GridCreatorWrapper();
        GridCreatorWrapper childWrapper = new GridCreatorWrapper();
        PartialSegmentHandler handler = null;
        try {
            parentWrapper.configureStructure(
                    true, 4, 4, null, 0, EndPosTreeKind.POOLED_TREAP, true, new int[0][2]
            );
            childWrapper.configureStructure(
                    true, 2, 4, parentWrapper, 1, EndPosTreeKind.POOLED_TREAP, true, new int[0][2]
            );
            parentWrapper.addChildWrapper(childWrapper, 1);

            childWrapper.materializeIfNeeded();
            AdvancedGridCreator child = (AdvancedGridCreator) childWrapper.getContent();
            child.reserveHorizontal(1, 2, 2);
            childWrapper.finishAddonPhase();

            handler = PartialSegmentHandler.fromChildWrappers(
                    4,
                    4,
                    false,
                    EndPosTreeKind.POOLED_TREAP,
                    new int[0][2],
                    new GridCreatorWrapper[]{childWrapper},
                    new int[]{1},
                    1
            );

            assertArrayEquals(
                    new GridSegment[]{
                            GridSegment.GS(1, 1, 4),
                            GridSegment.GS(2, 1, 1),
                            GridSegment.GS(2, 4, 1),
                            GridSegment.GS(3, 1, 4),
                            GridSegment.GS(4, 1, 4)
                    },
                    handler.exportFreeSegments()
            );
        } finally {
            if (handler != null) {
                handler.destroy();
            }
            destroyIfMaterialized(parentWrapper);
            destroyIfMaterialized(childWrapper);
        }
    }

    @Test
    public void vertical_handler_from_children_clips_blocked_rows_and_merges_with_plain_rows() {
        GridCreatorWrapper parentWrapper = new GridCreatorWrapper();
        GridCreatorWrapper childWrapper = new GridCreatorWrapper();
        PartialSegmentHandler handler = null;
        try {
            parentWrapper.configureStructure(
                    true, 6, 1, null, 0, EndPosTreeKind.POOLED_TREAP, true, new int[0][2]
            );
            childWrapper.configureStructure(
                    true, 4, 1, parentWrapper, 1, EndPosTreeKind.POOLED_TREAP, true, new int[0][2]
            );
            parentWrapper.addChildWrapper(childWrapper, 1);

            childWrapper.materializeIfNeeded();
            childWrapper.finishAddonPhase();

            handler = PartialSegmentHandler.fromChildWrappers(
                    6,
                    1,
                    true,
                    EndPosTreeKind.POOLED_TREAP,
                    new int[][]{{3, 3}},
                    new GridCreatorWrapper[]{childWrapper},
                    new int[]{1},
                    1
            );

            assertArrayEquals(
                    new GridSegment[]{
                            GridSegment.GS(1, 1, 2),
                            GridSegment.GS(4, 1, 3)
                    },
                    handler.exportFreeSegments()
            );
        } finally {
            if (handler != null) {
                handler.destroy();
            }
            destroyIfMaterialized(parentWrapper);
            destroyIfMaterialized(childWrapper);
        }
    }

    @Test
    public void append_maximal_free_segments_exports_horizontal_segments_into_scratch() {
        PartialSegmentHandler handler = new PartialSegmentHandler(4, 4, false, EndPosTreeKind.POOLED_TREAP);
        GridBuildScratch scratch = new GridBuildScratch();
        try {
            handler.reserve(2, 2, 2);

            handler.appendMaximalFreeSegments(scratch);

            assertArrayEquals(
                    new GridSegment[]{
                            GridSegment.GS(1, 1, 4),
                            GridSegment.GS(2, 1, 1),
                            GridSegment.GS(2, 4, 1),
                            GridSegment.GS(3, 1, 4),
                            GridSegment.GS(4, 1, 4)
                    },
                    scratchToSegments(scratch)
            );
        } finally {
            handler.destroy();
        }
    }

    @Test
    public void append_maximal_free_segments_exports_vertical_segments_into_scratch() {
        PartialSegmentHandler handler = new PartialSegmentHandler(5, 2, true, EndPosTreeKind.POOLED_TREAP);
        GridBuildScratch scratch = new GridBuildScratch();
        try {
            handler.reserve(2, 1, 2);

            handler.appendMaximalFreeSegments(scratch);

            assertArrayEquals(
                    new GridSegment[]{
                            GridSegment.GS(1, 1, 1),
                            GridSegment.GS(4, 1, 2),
                            GridSegment.GS(1, 2, 5)
                    },
                    scratchToSegments(scratch)
            );
        } finally {
            handler.destroy();
        }
    }

    private static GridSegment[] scratchToSegments(GridBuildScratch scratch) {
        GridSegment[] out = new GridSegment[scratch.size()];
        for (int i = 0; i < scratch.size(); ++i) {
            out[i] = GridSegment.GS(scratch.rowAt(i), scratch.colAt(i), scratch.lengthAt(i));
        }
        return out;
    }
}
