package com.example.game3d.core.terrain;

import com.example.game3d.core.terrain.addon.Addon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** One atomic, revisioned update to canonical terrain state. */
public final class TerrainCommit {
    public final long baseRevision;
    public final long revision;
    public final long committedThroughSegmentId;
    public final long retireBeforeSegmentId;
    public final List<TerrainSegment> segmentUpserts;

    public TerrainCommit(
            long baseRevision,
            long revision,
            long committedThroughSegmentId,
            long retireBeforeSegmentId,
            List<TerrainSegment> segmentUpserts) {
        if (baseRevision < 0L || revision != baseRevision + 1L) {
            throw new IllegalArgumentException("Terrain revisions must be consecutive");
        }
        if (committedThroughSegmentId < -1L || retireBeforeSegmentId < 0L
                || retireBeforeSegmentId > committedThroughSegmentId + 1L) {
            throw new IllegalArgumentException("Invalid terrain frontiers");
        }
        ArrayList<TerrainSegment> sorted = new ArrayList<TerrainSegment>(
                segmentUpserts == null
                        ? Collections.<TerrainSegment>emptyList() : segmentUpserts);
        Collections.sort(sorted, new Comparator<TerrainSegment>() {
            @Override
            public int compare(TerrainSegment left, TerrainSegment right) {
                return Long.compare(left.id, right.id);
            }
        });
        Set<Long> segmentIds = new HashSet<Long>();
        Set<Long> addonIds = new HashSet<Long>();
        for (TerrainSegment segment : sorted) {
            if (segment == null
                    || segment.id < retireBeforeSegmentId
                    || segment.id > committedThroughSegmentId) {
                throw new IllegalArgumentException("Invalid terrain segment upsert");
            }
            if (!segmentIds.add(segment.id)) {
                throw new IllegalArgumentException("Duplicate segment id " + segment.id);
            }
            for (Addon addon : segment.addons) {
                if (!addonIds.add(addon.id())) {
                    throw new IllegalArgumentException(
                            "Duplicate addon id in commit " + addon.id());
                }
            }
        }
        this.baseRevision = baseRevision;
        this.revision = revision;
        this.committedThroughSegmentId = committedThroughSegmentId;
        this.retireBeforeSegmentId = retireBeforeSegmentId;
        this.segmentUpserts = Collections.unmodifiableList(sorted);
    }
}
