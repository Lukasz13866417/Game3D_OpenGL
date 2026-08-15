package com.example.game3d.authoring.grid.symbolic;

import com.example.game3d.authoring.grid.symbolic.advanced.segments.GridSegmentSink;

public interface RetainedGridSummary {
    int getRowCount();

    int getColCount();

    void forEachHorizontalFreeSegment(GridSegmentSink sink);

    void forEachVerticalFreeSegment(GridSegmentSink sink);

    void destroy();
}
