package com.example.game3d.core.terrain;

import com.example.game3d.core.terrain.addon.Addon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable complete canonical terrain state used for bootstrap and replay. */
public final class TerrainSnapshot {
    public final long revision;
    public final long committedThroughSegmentId;
    public final long retireBeforeSegmentId;
    public final long addonIdHighWatermark;
    public final List<TerrainSegment> segments;
    public final long deterministicDigest;

    public TerrainSnapshot(
            long revision,
            long committedThroughSegmentId,
            long retireBeforeSegmentId,
            Collection<TerrainSegment> segments) {
        this(revision, committedThroughSegmentId, retireBeforeSegmentId,
                deriveAddonIdHighWatermark(segments), segments);
    }

    public TerrainSnapshot(
            long revision,
            long committedThroughSegmentId,
            long retireBeforeSegmentId,
            long addonIdHighWatermark,
            Collection<TerrainSegment> segments) {
        if (revision < 0L || committedThroughSegmentId < -1L
                || retireBeforeSegmentId < 0L
                || retireBeforeSegmentId > committedThroughSegmentId + 1L
                || addonIdHighWatermark < -1L) {
            throw new IllegalArgumentException("Invalid terrain snapshot metadata");
        }
        ArrayList<TerrainSegment> sorted = new ArrayList<TerrainSegment>(
                segments == null ? Collections.<TerrainSegment>emptyList() : segments);
        Collections.sort(sorted, new Comparator<TerrainSegment>() {
            @Override
            public int compare(TerrainSegment left, TerrainSegment right) {
                return Long.compare(left.id, right.id);
            }
        });
        Set<Long> segmentIds = new HashSet<Long>();
        Set<Long> addonIds = new HashSet<Long>();
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, committedThroughSegmentId);
        hash = mix(hash, retireBeforeSegmentId);
        hash = mix(hash, addonIdHighWatermark);
        for (TerrainSegment segment : sorted) {
            if (segment == null || segment.id < retireBeforeSegmentId
                    || segment.id > committedThroughSegmentId
                    || !segmentIds.add(segment.id)) {
                throw new IllegalArgumentException("Invalid segment in terrain snapshot");
            }
            for (Addon addon : segment.addons) {
                if (addon.id() > addonIdHighWatermark) {
                    throw new IllegalArgumentException(
                            "Addon id exceeds snapshot high-watermark");
                }
                if (!addonIds.add(addon.id())) {
                    throw new IllegalArgumentException(
                            "Duplicate addon id " + addon.id());
                }
            }
            hash = mix(hash, segment.deterministicDigest());
        }
        this.revision = revision;
        this.committedThroughSegmentId = committedThroughSegmentId;
        this.retireBeforeSegmentId = retireBeforeSegmentId;
        this.addonIdHighWatermark = addonIdHighWatermark;
        this.segments = Collections.unmodifiableList(sorted);
        this.deterministicDigest = hash;
    }

    public static TerrainSnapshot empty() {
        return new TerrainSnapshot(0L, -1L, 0L, Collections.<TerrainSegment>emptyList());
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001b3L;
    }

    private static long deriveAddonIdHighWatermark(
            Collection<TerrainSegment> segments) {
        long highest = -1L;
        if (segments == null) {
            return highest;
        }
        for (TerrainSegment segment : segments) {
            if (segment == null) {
                continue;
            }
            for (Addon addon : segment.addons) {
                highest = Math.max(highest, addon.id());
            }
        }
        return highest;
    }
}
