package com.example.game3d.authoring;

import com.example.game3d.core.terrain.TerrainSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Frozen all-or-nothing result of interpreting one top-level structure. */
public final class MaterializedStructure {
    public final List<TerrainSegment> segments;
    public final Map<String, Long> sourceSegmentIds;
    public final Map<String, Long> sourceAddonIds;
    /** Completed distance-spaced GRID cells; an unfinished terminal cell is not counted. */
    public final int physicalGridRowCount;

    MaterializedStructure(
            List<TerrainSegment> segments,
            Map<String, Long> sourceSegmentIds,
            Map<String, Long> sourceAddonIds,
            int physicalGridRowCount) {
        this.segments = Collections.unmodifiableList(
                new ArrayList<TerrainSegment>(segments));
        this.sourceSegmentIds = Collections.unmodifiableMap(
                new LinkedHashMap<String, Long>(sourceSegmentIds));
        this.sourceAddonIds = Collections.unmodifiableMap(
                new LinkedHashMap<String, Long>(sourceAddonIds));
        this.physicalGridRowCount = physicalGridRowCount;
    }
}
