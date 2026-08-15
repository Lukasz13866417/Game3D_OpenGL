package com.example.game3d.core.terrain;

import com.example.game3d.core.math.Vec3;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StreamingTerrainGeneratorTest {
    @Test
    public void finalCanonicalStateDoesNotDependOnGenerationChunkSize() {
        StreamingTerrainGenerator whole = generator();
        StreamingTerrainGenerator chunked = generator();
        whole.enqueueIntroSegments();
        whole.enqueueGameplayLevel(7);
        chunked.enqueueIntroSegments();
        chunked.enqueueGameplayLevel(7);

        whole.generateChunks(-1);
        while (chunked.hasPendingGenerationWork()) {
            chunked.generateChunks(7);
        }

        TerrainSnapshot expected = whole.snapshot();
        TerrainSnapshot actual = chunked.snapshot();
        assertEquals(expected.committedThroughSegmentId,
                actual.committedThroughSegmentId);
        assertEquals(expected.addonIdHighWatermark,
                actual.addonIdHighWatermark);
        assertEquals(expected.deterministicDigest,
                actual.deterministicDigest);
        assertEquals(expected.segments.size(), actual.segments.size());
        assertTrue("chunking intentionally changes only commit revision count",
                actual.revision > expected.revision);
    }

    @Test
    public void retirementAndLaterGenerationFormOneOrderedOutputStream() {
        StreamingTerrainGenerator generator = generator();
        generator.enqueueIntroSegments();
        generator.generateChunks(100);
        generator.drainPendingCommits();

        generator.removeOldTerrainElements(80L);
        generator.generateChunks(10);
        List<TerrainCommit> commits = generator.drainPendingCommits();

        assertEquals(2, commits.size());
        assertEquals(30L, commits.get(0).retireBeforeSegmentId);
        assertTrue(commits.get(0).segmentUpserts.isEmpty());
        assertEquals(commits.get(0).revision, commits.get(1).baseRevision);
        assertFalse(commits.get(1).segmentUpserts.isEmpty());
        assertEquals(commits.get(1).revision, generator.revision());
    }

    @Test
    public void recipeEnqueueingDoesNotMaterializeBeyondTheChunkBudget() {
        StreamingTerrainGenerator generator = generator();
        generator.enqueueIntroSegments();
        for (int level = 0; level < 8; level++) {
            generator.enqueueGameplayLevel(level);
        }

        assertEquals(0, generator.snapshot().segments.size());
        assertEquals(0L, generator.revision());

        assertEquals(3, generator.generateChunks(3));
        List<TerrainCommit> commits = generator.drainPendingCommits();

        assertEquals(1, commits.size());
        assertEquals(3, commits.get(0).segmentUpserts.size());
        assertEquals(3, generator.snapshot().segments.size());
        assertTrue(generator.hasPendingGenerationWork());
    }

    @Test
    public void recipesContainRampsFeaturesAndDisconnectedSegments() {
        StreamingTerrainGenerator generator = generator();
        for (int level = 0; level < 12; level++) {
            generator.enqueueGameplayLevel(level);
        }
        generator.generateChunks(-1);

        boolean boost = false;
        boolean feature = false;
        boolean disconnected = false;
        for (TerrainSegment segment : generator.snapshot().segments) {
            boost |= segment.surface.kind
                    != SurfaceProperties.Kind.NORMAL;
            feature |= !segment.addons.isEmpty();
            disconnected |= !segment.connectedToPrevious;
        }

        assertTrue(boost);
        assertTrue(feature);
        assertTrue(disconnected);
    }

    @Test
    public void connectedSeamsStayExactAcrossSingleSegmentCommits() {
        StreamingTerrainGenerator generator = generator();
        for (int level = 0; level < 12; level++) {
            generator.enqueueGameplayLevel(level);
        }

        TerrainSegment previous = null;
        int connectedSeams = 0;
        int turningSegments = 0;
        while (generator.hasPendingGenerationWork()) {
            assertEquals(1, generator.generateChunks(1));
            List<TerrainCommit> commits = generator.drainPendingCommits();
            assertEquals(1, commits.size());
            assertEquals(1, commits.get(0).segmentUpserts.size());
            TerrainSegment current = commits.get(0).segmentUpserts.get(0);

            if (current.connectedToPrevious) {
                assertTrue(previous != null);
                assertTrue(previous.solid);
                assertEquals(previous.id + 1L, current.id);
                assertConnectedSeam(previous, current);
                connectedSeams++;

                if (!current.nearRight.subtract(current.nearLeft).equals(
                        current.farRight.subtract(current.farLeft))) {
                    turningSegments++;
                }
            }
            previous = current;
        }

        assertTrue(connectedSeams > 0);
        assertTrue("the recipe must exercise changing ribbon directions",
                turningSegments > 0);
    }

    @Test
    public void streamingReferenceAdvancesPastAStaleSupportedSegment() {
        StreamingTerrainGenerator generator = generator();
        generator.enqueueIntroSegments();
        generator.generateChunks(-1);
        List<TerrainSegment> segments = generator.snapshot().segments;
        assertTrue(segments.size() > 40);

        TerrainSegment early = segments.get(5);
        TerrainSegment farAhead = segments.get(35);
        Vec3 farCenter = farAhead.nearLeft
                .add(farAhead.nearRight)
                .add(farAhead.farLeft)
                .add(farAhead.farRight)
                .multiply(0.25);

        long resolved = generator.resolveStreamingReferenceSegmentId(
                early.id, farCenter);

        assertTrue(
                "airborne progress must not freeze on lastSupportedSegmentId",
                resolved >= farAhead.id - 1L);
        assertTrue(resolved > early.id);
        assertTrue(
                "lead must shrink once the body has advanced past the old support",
                generator.getCommittedLeadAheadOf(resolved)
                        < generator.getCommittedLeadAheadOf(early.id));
    }

    @Test
    public void rampBrightnessInterpolatesWithoutBoundarySteps() {
        StreamingTerrainGenerator generator = generator();
        for (int level = 0; level < 12; level++) {
            generator.enqueueGameplayLevel(level);
        }
        generator.generateChunks(-1);

        List<TerrainSegment> segments = generator.snapshot().segments;
        boolean sawRampGradient = false;
        boolean sawContinuousRampExit = false;
        for (int i = 1; i < segments.size(); i++) {
            TerrainSegment previous = segments.get(i - 1);
            TerrainSegment current = segments.get(i);
            if (current.connectedToPrevious) {
                assertConnectedSeam(previous, current);
            }
            if (current.surface.kind != SurfaceProperties.Kind.NORMAL) {
                assertTrue(current.farLeftAppearance.brightness
                        >= current.nearLeftAppearance.brightness);
                sawRampGradient |= current.farLeftAppearance.brightness
                        > current.nearLeftAppearance.brightness;
            }
            if (previous.surface.kind != SurfaceProperties.Kind.NORMAL
                    && current.surface.kind == SurfaceProperties.Kind.NORMAL
                    && current.connectedToPrevious) {
                assertEquals(
                        previous.farLeftAppearance,
                        current.nearLeftAppearance);
                assertTrue(current.nearLeftAppearance.brightness
                        > current.farLeftAppearance.brightness);
                sawContinuousRampExit = true;
            }
        }

        assertTrue(sawRampGradient);
        assertTrue(sawContinuousRampExit);
    }

    private static void assertConnectedSeam(
            TerrainSegment previous, TerrainSegment current) {
        assertEquals(previous.farLeft, current.nearLeft);
        assertEquals(previous.farRight, current.nearRight);
        assertEquals(
                previous.farLeftAppearance,
                current.nearLeftAppearance);
        assertEquals(
                previous.farRightAppearance,
                current.nearRightAppearance);
    }

    private static StreamingTerrainGenerator generator() {
        return new StreamingTerrainGenerator(
                3.2, 1.4, new Vec3(0.0, -3.5, -0.5));
    }
}
