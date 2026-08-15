package com.example.game3d.core.terrain;

import com.example.game3d.core.terrain.addon.Addon;

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
    private final HashMap<Long, Long> addonOwners = new HashMap<Long, Long>();
    private final HashSet<Long> retiredAddonIds = new HashSet<Long>();
    private long revision;
    private long committedThroughSegmentId = -1L;
    private long retireBeforeSegmentId;
    private long addonIdHighWatermark = -1L;

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
        addonOwners.clear();
        retiredAddonIds.clear();
        revision = snapshot.revision;
        committedThroughSegmentId = snapshot.committedThroughSegmentId;
        retireBeforeSegmentId = snapshot.retireBeforeSegmentId;
        addonIdHighWatermark = snapshot.addonIdHighWatermark;
        for (TerrainSegment segment : snapshot.segments) {
            segments.put(segment.id, segment);
            addAddonOwners(segment, addonOwners);
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
                new HashMap<Long, Long>(addonOwners);
        Set<Long> newlyRetired = new HashSet<Long>();
        for (Map.Entry<Long, TerrainSegment> entry :
                segments.headMap(commit.retireBeforeSegmentId, false).entrySet()) {
            removeAddonOwners(entry.getValue(), prospectiveOwners);
            for (Addon addon : entry.getValue().addons) {
                newlyRetired.add(addon.id());
            }
        }
        for (TerrainSegment segment : commit.segmentUpserts) {
            TerrainSegment old = segments.get(segment.id);
            if (old != null) {
                removeAddonOwners(old, prospectiveOwners);
                for (Addon oldAddon : old.addons) {
                    if (!containsAddon(segment, oldAddon.id())) {
                        newlyRetired.add(oldAddon.id());
                    }
                }
            }
            for (Addon addon : segment.addons) {
                Long originalOwner = addonOwners.get(addon.id());
                if (originalOwner != null
                        && originalOwner.longValue() != addon.ownerSegmentId()) {
                    throw new IllegalArgumentException(
                            "Addon id cannot move from segment " + originalOwner
                                    + " to " + addon.ownerSegmentId());
                }
                Addon originalAddon = originalAddon(addon.id(), originalOwner);
                if (originalAddon != null && originalAddon.kind != addon.kind) {
                    throw new IllegalArgumentException(
                            "Addon id cannot change kind from " + originalAddon.kind
                                    + " to " + addon.kind);
                }
                if (originalOwner == null
                        && addon.id() <= addonIdHighWatermark) {
                    throw new IllegalArgumentException(
                            "Addon id is not newer than high-watermark " + addon.id());
                }
                if (retiredAddonIds.contains(addon.id())
                        || newlyRetired.contains(addon.id())) {
                    throw new IllegalArgumentException(
                            "Retired addon id reused " + addon.id());
                }
                Long previousOwner = prospectiveOwners.put(
                        addon.id(), addon.ownerSegmentId());
                if (previousOwner != null
                        && previousOwner.longValue() != addon.ownerSegmentId()) {
                    throw new IllegalArgumentException(
                            "Addon id already owned by segment " + previousOwner);
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
            removeAddonOwners(removed, addonOwners);
            for (Addon addon : removed.addons) {
                retiredAddonIds.add(addon.id());
            }
            retiredSegments.remove();
        }
        for (TerrainSegment segment : commit.segmentUpserts) {
            TerrainSegment old = segments.put(segment.id, segment);
            if (old != null) {
                removeAddonOwners(old, addonOwners);
                for (Addon oldAddon : old.addons) {
                    if (!containsAddon(segment, oldAddon.id())) {
                        retiredAddonIds.add(oldAddon.id());
                    }
                }
            }
            addAddonOwners(segment, addonOwners);
            for (Addon addon : segment.addons) {
                addonIdHighWatermark = Math.max(addonIdHighWatermark, addon.id());
            }
        }
        revision = commit.revision;
        committedThroughSegmentId = commit.committedThroughSegmentId;
        retireBeforeSegmentId = commit.retireBeforeSegmentId;
    }

    public TerrainSnapshot snapshot() {
        return new TerrainSnapshot(
                revision, committedThroughSegmentId, retireBeforeSegmentId,
                addonIdHighWatermark, segments.values());
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

    public long addonIdHighWatermark() {
        return addonIdHighWatermark;
    }

    private static boolean containsAddon(TerrainSegment segment, long addonId) {
        for (Addon addon : segment.addons) {
            if (addon.id() == addonId) {
                return true;
            }
        }
        return false;
    }

    private Addon originalAddon(long addonId, Long ownerSegmentId) {
        if (ownerSegmentId == null) {
            return null;
        }
        TerrainSegment owner = segments.get(ownerSegmentId);
        if (owner == null) {
            throw new IllegalStateException(
                    "Addon owner index refers to missing segment " + ownerSegmentId);
        }
        for (Addon addon : owner.addons) {
            if (addon.id() == addonId) {
                return addon;
            }
        }
        throw new IllegalStateException(
                "Addon owner index refers to missing addon " + addonId);
    }

    private static void addAddonOwners(
            TerrainSegment segment, Map<Long, Long> owners) {
        for (Addon addon : segment.addons) {
            Long previous = owners.put(addon.id(), addon.ownerSegmentId());
            if (previous != null && previous.longValue() != addon.ownerSegmentId()) {
                throw new IllegalArgumentException("Duplicate addon id " + addon.id());
            }
        }
    }

    private static void removeAddonOwners(
            TerrainSegment segment, Map<Long, Long> owners) {
        for (Addon addon : segment.addons) {
            Long owner = owners.get(addon.id());
            if (owner != null && owner.longValue() == segment.id) {
                owners.remove(addon.id());
            }
        }
    }
}
