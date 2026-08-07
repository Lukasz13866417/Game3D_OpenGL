package com.example.game3d_opengl.game.terrain.presentation;

import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.TrackBuilder;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class CanonicalTerrainPresentationTest {
    @Test
    public void appliesTheSameAppendAndRetirementRevisionsAsSimulation() {
        TerrainSnapshot complete = new TrackBuilder(6.0)
                .straight(10.0)
                .feather(0.0, 0.0, 0.25, 0.22)
                .straight(10.0)
                .buildSnapshot();
        TerrainSegment first = complete.segments.get(0);
        TerrainSegment second = complete.segments.get(1);
        CanonicalTerrainPresentation presentation =
                new CanonicalTerrainPresentation(new TerrainSnapshot(
                        0L, 0L, 0L, Collections.singletonList(first)));

        assertEquals(0L, presentation.terrainRevision());
        assertEquals(1, presentation.visibleSegmentCount());
        assertEquals(1, presentation.visibleFeatureCount());

        presentation.applyTerrainCommit(new TerrainCommit(
                0L, 1L, 1L, 0L, Collections.singletonList(second)));

        assertEquals(1L, presentation.terrainRevision());
        assertEquals(2, presentation.visibleSegmentCount());
        assertEquals(1, presentation.visibleFeatureCount());

        presentation.applyTerrainCommit(new TerrainCommit(
                1L, 2L, 1L, 1L,
                Collections.<TerrainSegment>emptyList()));

        assertEquals(2L, presentation.terrainRevision());
        assertEquals(1, presentation.visibleSegmentCount());
        assertEquals(0, presentation.visibleFeatureCount());
    }

    @Test
    public void appliesCommitBurstInOrderAndExposesFinalRevision() {
        TerrainSnapshot complete = new TrackBuilder(6.0)
                .straight(4.0)
                .straight(4.0)
                .straight(4.0)
                .buildSnapshot();
        CanonicalTerrainPresentation presentation =
                new CanonicalTerrainPresentation(new TerrainSnapshot(
                        0L, 0L, 0L,
                        Collections.singletonList(complete.segments.get(0))));

        presentation.applyTerrainCommits(Arrays.asList(
                new TerrainCommit(
                        0L, 1L, 1L, 0L,
                        Collections.singletonList(complete.segments.get(1))),
                new TerrainCommit(
                        1L, 2L, 2L, 0L,
                        Collections.singletonList(complete.segments.get(2)))));

        assertEquals(2L, presentation.terrainRevision());
        assertEquals(3, presentation.visibleSegmentCount());
    }
}
