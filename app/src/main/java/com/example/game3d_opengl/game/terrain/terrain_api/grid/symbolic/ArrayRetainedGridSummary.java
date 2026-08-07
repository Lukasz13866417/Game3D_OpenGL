package com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic;

import com.example.game3d_opengl.game.terrain.terrain_api.grid.symbolic.advanced.segments.GridSegmentSink;

public final class ArrayRetainedGridSummary implements RetainedGridSummary {
    private final int rowCount;
    private final int colCount;
    private final GridSegment[] horizontalFreeSegments;
    private final GridSegment[] verticalFreeSegments;

    public ArrayRetainedGridSummary(
            int rowCount,
            int colCount,
            GridSegment[] horizontalFreeSegments,
            GridSegment[] verticalFreeSegments
    ) {
        this.rowCount = rowCount;
        this.colCount = colCount;
        this.horizontalFreeSegments = horizontalFreeSegments != null
                ? horizontalFreeSegments
                : new GridSegment[0];
        this.verticalFreeSegments = verticalFreeSegments != null
                ? verticalFreeSegments
                : new GridSegment[0];
    }

    @Override
    public int getRowCount() {
        return rowCount;
    }

    @Override
    public int getColCount() {
        return colCount;
    }

    @Override
    public void forEachHorizontalFreeSegment(GridSegmentSink sink) {
        if (sink == null) {
            return;
        }
        for (GridSegment segment : horizontalFreeSegments) {
            if (segment != null) {
                sink.accept(segment.row, segment.col, segment.length);
            }
        }
    }

    @Override
    public void forEachVerticalFreeSegment(GridSegmentSink sink) {
        if (sink == null) {
            return;
        }
        for (GridSegment segment : verticalFreeSegments) {
            if (segment != null) {
                sink.accept(segment.row, segment.col, segment.length);
            }
        }
    }

    @Override
    public void destroy() {
        // No pooled resources are owned here.
    }
}
