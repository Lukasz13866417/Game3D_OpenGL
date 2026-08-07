package com.example.game3d.core.terrain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Mutable canonical store. It is deliberately independent from collision and rendering caches. */
public final class TerrainState {
    private final TreeMap<Long, TerrainSegment> segments =
            new TreeMap<Long, TerrainSegment>();
    private final HashMap<Long, Long> featureOwners = new HashMap<Long, Long>();
    private final HashSet<Long> retiredFeatureIds = new HashSet<Long>();
    private long revision;
    private long committedThroughSegmentId = -1L;
    private long retireBeforeSegmentId;
    private long featureIdHighWatermark = -1L;

    public TerrainState() {
    }

    public TerrainState(TerrainSnapshot snapshot) {
        replace(snapshot);
    }

    public void replace(TerrainSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot == null");
        }
        segments.clear();
        featureOwners.clear();
        retiredFeatureIds.clear();
        revision = snapshot.revision;
        committedThroughSegmentId = snapshot.committedThroughSegmentId;
        retireBeforeSegmentId = snapshot.retireBeforeSegmentId;
        featureIdHighWatermark = snapshot.featureIdHighWatermark;
        for (TerrainSegment segment : snapshot.segments) {
            segments.put(segment.id, segment);
            addFeatureOwners(segment, featureOwners);
        }
    }

    public void validate(TerrainCommit commit) {
        if (commit == null) {
            throw new IllegalArgumentException("commit == null");
        }
        if (commit.baseRevision != revision
                || commit.revision != revision + 1L) {
            throw new IllegalArgumentException(
                    "Terrain revision mismatch: current=" + revision
                            + " base=" + commit.baseRevision);
        }
        if (commit.committedThroughSegmentId < committedThroughSegmentId) {
            throw new IllegalArgumentException("Committed frontier cannot regress");
        }
        if (commit.retireBeforeSegmentId < retireBeforeSegmentId) {
            throw new IllegalArgumentException("Retirement frontier cannot regress");
        }

        HashMap<Long, Long> prospectiveOwners =
                new HashMap<Long, Long>(featureOwners);
        Set<Long> newlyRetired = new HashSet<Long>();
        for (Map.Entry<Long, TerrainSegment> entry :
                segments.headMap(commit.retireBeforeSegmentId, false).entrySet()) {
            removeFeatureOwners(entry.getValue(), prospectiveOwners);
            for (TerrainFeatureSpec feature : entry.getValue().features) {
                newlyRetired.add(feature.id);
            }
        }
        for (TerrainSegment segment : commit.segmentUpserts) {
            TerrainSegment old = segments.get(segment.id);
            if (old != null) {
                removeFeatureOwners(old, prospectiveOwners);
                for (TerrainFeatureSpec oldFeature : old.features) {
                    if (!containsFeature(segment, oldFeature.id)) {
                        newlyRetired.add(oldFeature.id);
                    }
                }
            }
            for (TerrainFeatureSpec feature : segment.features) {
                Long originalOwner = featureOwners.get(feature.id);
                if (originalOwner == null
                        && feature.id <= featureIdHighWatermark) {
                    throw new IllegalArgumentException(
                            "Feature id is not newer than high-watermark "
                                    + feature.id);
                }
                if (retiredFeatureIds.contains(feature.id)
                        || newlyRetired.contains(feature.id)) {
                    throw new IllegalArgumentException(
                            "Retired feature id reused " + feature.id);
                }
                Long previousOwner = prospectiveOwners.put(
                        feature.id, feature.ownerSegmentId);
                if (previousOwner != null
                        && previousOwner.longValue() != feature.ownerSegmentId) {
                    throw new IllegalArgumentException(
                            "Feature id already owned by segment " + previousOwner);
                }
            }
        }
    }

    public void apply(TerrainCommit commit) {
        validate(commit);
        applyValidated(commit);
    }

    /**
     * Applies a commit that the caller has already passed to {@link #validate(TerrainCommit)}.
     *
     * <p>Package-private so projections such as {@link TerrainCollisionIndex} can validate once,
     * prepare their derived data, and then advance this canonical state without repeating the
     * allocation-heavy validation pass.</p>
     */
    void applyValidated(TerrainCommit commit) {
        Iterator<TerrainSegment> retiredSegments = segments
                .headMap(commit.retireBeforeSegmentId, false)
                .values()
                .iterator();
        while (retiredSegments.hasNext()) {
            TerrainSegment removed = retiredSegments.next();
            removeFeatureOwners(removed, featureOwners);
            for (TerrainFeatureSpec feature : removed.features) {
                retiredFeatureIds.add(feature.id);
            }
            retiredSegments.remove();
        }
        for (TerrainSegment segment : commit.segmentUpserts) {
            TerrainSegment old = segments.put(segment.id, segment);
            if (old != null) {
                removeFeatureOwners(old, featureOwners);
                for (TerrainFeatureSpec oldFeature : old.features) {
                    if (!containsFeature(segment, oldFeature.id)) {
                        retiredFeatureIds.add(oldFeature.id);
                    }
                }
            }
            addFeatureOwners(segment, featureOwners);
            for (TerrainFeatureSpec feature : segment.features) {
                featureIdHighWatermark = Math.max(
                        featureIdHighWatermark, feature.id);
            }
        }
        revision = commit.revision;
        committedThroughSegmentId = commit.committedThroughSegmentId;
        retireBeforeSegmentId = commit.retireBeforeSegmentId;
    }

    public TerrainSnapshot snapshot() {
        return new TerrainSnapshot(
                revision, committedThroughSegmentId, retireBeforeSegmentId,
                featureIdHighWatermark, segments.values());
    }

    public TerrainSegment segment(long id) {
        return segments.get(id);
    }

    public Iterable<TerrainSegment> segments() {
        return segments.values();
    }

    Iterable<TerrainSegment> segmentsBefore(long exclusiveSegmentId) {
        return segments.headMap(exclusiveSegmentId, false).values();
    }

    public long revision() {
        return revision;
    }

    public long committedThroughSegmentId() {
        return committedThroughSegmentId;
    }

    public long retireBeforeSegmentId() {
        return retireBeforeSegmentId;
    }

    public long featureIdHighWatermark() {
        return featureIdHighWatermark;
    }

    private static boolean containsFeature(TerrainSegment segment, long featureId) {
        for (TerrainFeatureSpec feature : segment.features) {
            if (feature.id == featureId) {
                return true;
            }
        }
        return false;
    }

    private static void addFeatureOwners(
            TerrainSegment segment, Map<Long, Long> owners) {
        for (TerrainFeatureSpec feature : segment.features) {
            Long previous = owners.put(feature.id, feature.ownerSegmentId);
            if (previous != null && previous.longValue() != feature.ownerSegmentId) {
                throw new IllegalArgumentException("Duplicate feature id " + feature.id);
            }
        }
    }

    private static void removeFeatureOwners(
            TerrainSegment segment, Map<Long, Long> owners) {
        for (TerrainFeatureSpec feature : segment.features) {
            Long owner = owners.get(feature.id);
            if (owner != null && owner.longValue() == segment.id) {
                owners.remove(feature.id);
            }
        }
    }
}
