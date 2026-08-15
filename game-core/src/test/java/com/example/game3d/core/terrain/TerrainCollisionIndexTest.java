package com.example.game3d.core.terrain;

import com.example.game3d.core.math.Aabb;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.addon.Addon;
import com.example.game3d.core.terrain.addon.AddonFootprint;
import com.example.game3d.core.terrain.addon.DeathSpike;
import com.example.game3d.core.terrain.addon.Potion;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TerrainCollisionIndexTest {
    @Test
    public void commitCanUpsertTailAndRetirePrefix() {
        TerrainSegment first = segment(0L, 0.0, 4.0, SurfaceProperties.NORMAL);
        TerrainSegment tail = segment(1L, 4.0, 8.0, SurfaceProperties.NORMAL);
        TerrainCollisionIndex index = new TerrainCollisionIndex(
                new TerrainSnapshot(0L, 1L, 0L, Arrays.asList(first, tail)));
        long oldFingerprint = index.collisionFingerprint(2L);

        TerrainSegment movedTail = segment(
                1L, 4.0, 9.0, SurfaceProperties.BOOST_RAMP);
        index.apply(new TerrainCommit(
                0L, 1L, 1L, 1L, Collections.singletonList(movedTail)));

        assertFalse(index.containsTriangle(0L));
        assertFalse(index.containsTriangle(1L));
        assertTrue(index.containsTriangle(2L));
        assertNotEquals(oldFingerprint, index.collisionFingerprint(2L));
        assertEquals(1L, index.revision());
        assertEquals(1L, index.snapshot().retireBeforeSegmentId);
    }

    @Test
    public void adjoiningCommitCreatesWalkableTopologyAndRemovalRestoresBoundary() {
        TerrainSegment first = segment(0L, 0.0, 4.0, SurfaceProperties.NORMAL);
        TerrainCollisionIndex index = new TerrainCollisionIndex(
                new TerrainSnapshot(0L, 0L, 0L, Collections.singletonList(first)));
        int farEdgeBefore = index.collisionBoundaryMask(1L);

        TerrainSegment second = segment(1L, 4.0, 8.0, SurfaceProperties.NORMAL);
        index.apply(new TerrainCommit(
                0L, 1L, 1L, 0L, Collections.singletonList(second)));

        assertTrue(index.isWalkableTransition(
                1L, 2L, Math.cos(Math.toRadians(50.0))));
        assertNotEquals(farEdgeBefore, index.collisionBoundaryMask(1L));

        index.apply(new TerrainCommit(
                1L, 2L, 1L, 1L, Collections.<TerrainSegment>emptyList()));
        assertFalse(index.containsTriangle(0L));
        assertTrue(index.containsTriangle(2L));
    }

    @Test
    public void queryOrderAndDigestAreIndependentOfCommitMemberOrder() {
        TerrainSegment first = segment(0L, 0.0, 4.0, SurfaceProperties.NORMAL);
        TerrainSegment second = segment(1L, 4.0, 8.0, SurfaceProperties.BOOST_RAMP);
        TerrainCollisionIndex left = new TerrainCollisionIndex();
        TerrainCollisionIndex right = new TerrainCollisionIndex();

        left.apply(new TerrainCommit(
                0L, 1L, 1L, 0L, Arrays.asList(second, first)));
        right.apply(new TerrainCommit(
                0L, 1L, 1L, 0L, Arrays.asList(first, second)));

        ArrayList<TerrainTriangle> leftQuery = new ArrayList<TerrainTriangle>();
        ArrayList<TerrainTriangle> rightQuery = new ArrayList<TerrainTriangle>();
        Aabb all = new Aabb(
                new Vec3(-10.0, -1.0, -10.0),
                new Vec3(10.0, 1.0, 2.0));
        left.queryTriangles(all, leftQuery);
        right.queryTriangles(all, rightQuery);
        assertEquals(leftQuery.size(), rightQuery.size());
        for (int i = 0; i < leftQuery.size(); i++) {
            assertEquals(leftQuery.get(i).id, rightQuery.get(i).id);
        }
        assertEquals(left.deterministicDigest(), right.deterministicDigest());
    }

    @Test
    public void invalidRevisionLeavesStateUnchanged() {
        TerrainSegment segment = segment(0L, 0.0, 4.0, SurfaceProperties.NORMAL);
        TerrainCollisionIndex index = new TerrainCollisionIndex(
                new TerrainSnapshot(3L, 0L, 0L, Collections.singletonList(segment)));
        long digest = index.deterministicDigest();

        try {
            index.apply(new TerrainCommit(
                    0L, 1L, 0L, 0L, Collections.singletonList(segment)));
        } catch (IllegalArgumentException expected) {
            assertEquals(3L, index.revision());
            assertEquals(digest, index.deterministicDigest());
            return;
        }
        throw new AssertionError("Expected revision mismatch");
    }

    @Test
    public void canonicalTrackBuilderIncludesExplicitGap() {
        TerrainSnapshot snapshot = new TrackBuilder(4.0)
                .straight(3.0)
                .gap(2.0)
                .lift(1.0)
                .straight(3.0)
                .buildSnapshot();

        assertEquals(3, snapshot.segments.size());
        assertTrue(snapshot.segments.get(0).solid);
        assertFalse(snapshot.segments.get(1).solid);
        assertFalse(snapshot.segments.get(2).connectedToPrevious);
        assertFalse(snapshot.segments.get(1).farLeft.equals(
                snapshot.segments.get(2).nearLeft));
    }

    @Test
    public void canonicalTurnSeamsAreWalkableSharedEdges() {
        TerrainSnapshot snapshot = new TrackBuilder(4.0)
                .straight(3.0)
                .turnDegrees(75.0, 6.0, 10)
                .straight(3.0)
                .buildSnapshot();
        TerrainCollisionIndex index = new TerrainCollisionIndex(snapshot);

        int checkedSeams = 0;
        for (int i = 1; i < snapshot.segments.size(); i++) {
            TerrainSegment previous = snapshot.segments.get(i - 1);
            TerrainSegment current = snapshot.segments.get(i);
            if (!current.connectedToPrevious) {
                continue;
            }
            assertEquals(previous.farLeft, current.nearLeft);
            assertEquals(previous.farRight, current.nearRight);
            assertTrue(index.isWalkableTransition(
                    previous.id * 2L + 1L,
                    current.id * 2L,
                    0.0));
            checkedSeams++;
        }
        assertTrue(checkedSeams > 0);
    }

    @Test
    public void bootstrapPreservesRetiredAddonIdHighWatermark() {
        TerrainSegment original = withCollectible(
                segment(0L, 0.0, 4.0, SurfaceProperties.NORMAL), 10L);
        TerrainCollisionIndex first = new TerrainCollisionIndex(
                new TerrainSnapshot(
                        0L, 0L, 0L, Collections.singletonList(original)));
        first.apply(new TerrainCommit(
                0L, 1L, 0L, 1L,
                Collections.<TerrainSegment>emptyList()));
        TerrainSnapshot midRunBootstrap = first.snapshot();

        assertEquals(10L, midRunBootstrap.addonIdHighWatermark);
        TerrainCollisionIndex restored =
                new TerrainCollisionIndex(midRunBootstrap);
        TerrainSegment illegalReuse = withCollectible(
                segment(1L, 4.0, 8.0, SurfaceProperties.NORMAL), 10L);
        try {
            restored.apply(new TerrainCommit(
                    1L, 2L, 1L, 1L,
                    Collections.singletonList(illegalReuse)));
        } catch (IllegalArgumentException expected) {
            assertEquals(1L, restored.revision());
            return;
        }
        throw new AssertionError("Expected retired feature ID reuse rejection");
    }

    @Test
    public void existingAddonIdCannotMoveToAnotherOwner() {
        TerrainSegment original = withCollectible(
                segment(0L, 0.0, 4.0, SurfaceProperties.NORMAL), 1L);
        TerrainCollisionIndex index = new TerrainCollisionIndex(
                new TerrainSnapshot(0L, 0L, 0L, Collections.singletonList(original)));
        TerrainSegment emptied = segment(0L, 0.0, 4.0, SurfaceProperties.NORMAL);
        TerrainSegment relocated = withCollectible(
                segment(1L, 4.0, 8.0, SurfaceProperties.NORMAL), 1L);

        try {
            index.apply(new TerrainCommit(
                    0L, 1L, 1L, 0L, Arrays.asList(emptied, relocated)));
        } catch (IllegalArgumentException expected) {
            assertEquals(0L, index.revision());
            assertEquals(0L, index.snapshot().committedThroughSegmentId);
            return;
        }
        throw new AssertionError("Expected addon relocation rejection");
    }

    @Test
    public void existingAddonIdCannotChangeKind() {
        TerrainSegment original = withCollectible(
                segment(0L, 0.0, 4.0, SurfaceProperties.NORMAL), 1L);
        TerrainCollisionIndex index = new TerrainCollisionIndex(
                new TerrainSnapshot(0L, 0L, 0L, Collections.singletonList(original)));
        TerrainSegment replacement = withSpike(
                segment(0L, 0.0, 4.0, SurfaceProperties.NORMAL), 1L);

        try {
            index.apply(new TerrainCommit(
                    0L, 1L, 0L, 0L, Collections.singletonList(replacement)));
        } catch (IllegalArgumentException expected) {
            assertEquals(0L, index.revision());
            return;
        }
        throw new AssertionError("Expected addon kind replacement rejection");
    }

    private static TerrainSegment segment(
            long id, double nearZ, double farZ, SurfaceProperties surface) {
        // The track progresses toward negative Z. Parameters are positive distances.
        Vec3 nearLeft = new Vec3(-2.0, 0.0, -nearZ);
        Vec3 nearRight = new Vec3(2.0, 0.0, -nearZ);
        Vec3 farLeft = new Vec3(-2.0, 0.0, -farZ);
        Vec3 farRight = new Vec3(2.0, 0.0, -farZ);
        return new TerrainSegment(
                id,
                nearLeft,
                nearRight,
                farLeft,
                farRight,
                true,
                id > 0L,
                surface,
                TerrainVertexAppearance.DEFAULT,
                TerrainVertexAppearance.DEFAULT,
                TerrainVertexAppearance.DEFAULT,
                TerrainVertexAppearance.DEFAULT,
                Collections.<Addon>emptyList());
    }

    private static TerrainSegment withCollectible(
            TerrainSegment segment, long addonId) {
        Vec3 center = segment.nearLeft.add(new Vec3(1.0, 0.25, -1.0));
        Potion addon = new Potion(center, 0.25, "TEST");
        addon.place(addonId, segment.id,
                AddonFootprint.around(center, 0.25, 0.25, 0.25));
        return new TerrainSegment(
                segment.id,
                segment.nearLeft,
                segment.nearRight,
                segment.farLeft,
                segment.farRight,
                segment.solid,
                segment.connectedToPrevious,
                segment.surface,
                segment.nearLeftAppearance,
                segment.nearRightAppearance,
                segment.farLeftAppearance,
                segment.farRightAppearance,
                Collections.<Addon>singletonList(addon));
    }

    private static TerrainSegment withSpike(
            TerrainSegment segment, long addonId) {
        Vec3 center = segment.nearLeft.add(new Vec3(1.0, 0.0, -1.0));
        Vec3 nearLeft = center.add(new Vec3(-0.2, 0.0, 0.2));
        Vec3 nearRight = center.add(new Vec3(0.2, 0.0, 0.2));
        Vec3 farLeft = center.add(new Vec3(-0.2, 0.0, -0.2));
        Vec3 farRight = center.add(new Vec3(0.2, 0.0, -0.2));
        DeathSpike addon = new DeathSpike(
                nearLeft, nearRight, farLeft, farRight,
                center.add(new Vec3(0.0, 0.8, 0.0)), Vec3.UP, 0.0,
                center, 0.2, 0.8);
        addon.place(addonId, segment.id,
                AddonFootprint.quadrilateral(nearLeft, nearRight, farLeft, farRight));
        return new TerrainSegment(
                segment.id,
                segment.nearLeft,
                segment.nearRight,
                segment.farLeft,
                segment.farRight,
                segment.solid,
                segment.connectedToPrevious,
                segment.surface,
                segment.nearLeftAppearance,
                segment.nearRightAppearance,
                segment.farLeftAppearance,
                segment.farRightAppearance,
                Collections.<Addon>singletonList(addon));
    }
}
