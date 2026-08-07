package com.example.game3d.core.terrain;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TrackBuilderTest {
    @Test
    public void publishesStableUpwardTrianglesAndOpenGaps() {
        TerrainWorld terrain = new TrackBuilder(6.0)
                .straight(10.0)
                .gap(4.0)
                .lift(1.0)
                .straight(10.0)
                .build();

        assertEquals(4, terrain.triangles().size());
        Set<Long> ids = new HashSet<Long>();
        for (TerrainTriangle triangle : terrain.triangles()) {
            assertTrue(triangle.normal.y > 0.0);
            assertTrue(ids.add(triangle.id));
        }
        assertTrue(terrain.patches().get(0).features.isEmpty());
    }

    @Test
    public void featuresAreSortedByStableIdEvenWhenSeparateFromSurfaces() {
        TerrainWorld terrain = new TrackBuilder(5.0)
                .straight(2.0)
                .feather(0.0, 0.0, 1.0, 0.2)
                .spike(1.0, 0.0, 0.4, 1.0)
                .straight(2.0)
                .build();

        long previous = -1L;
        int featureCount = 0;
        for (TerrainPatch patch : terrain.patches()) {
            for (TerrainFeature feature : patch.features) {
                assertTrue(feature.id > previous);
                previous = feature.id;
                featureCount++;
            }
        }
        assertEquals(2, featureCount);
    }

    @Test
    public void turnPublishesUpwardTrianglesAndMovesCursorAlongArc() {
        TrackBuilder builder = new TrackBuilder(6.0)
                .straight(2.0)
                .turnDegrees(90.0, 5.0, 12)
                .straight(2.0);
        TerrainWorld terrain = builder.build();

        assertEquals((1 + 12 + 1) * 2, terrain.triangles().size());
        for (TerrainTriangle triangle : terrain.triangles()) {
            assertTrue(triangle.normal.y > 0.999);
        }
        assertTrue(Math.abs(builder.cursor().x) > 1.0);
        assertTrue(builder.cursor().z < 0.0);
    }

    @Test
    public void connectedTurnsShareCanonicalEdgesAndLegacyPatchCorners() {
        TrackBuilder builder = new TrackBuilder(6.0)
                .straight(2.0)
                .turnDegrees(90.0, 5.0, 12)
                .straight(2.0);
        TerrainSnapshot snapshot = builder.buildSnapshot();
        TerrainWorld world = builder.build();
        List<TerrainSegment> segments = snapshot.segments;

        int changingCrossSections = 0;
        for (int i = 1; i < segments.size(); i++) {
            TerrainSegment previous = segments.get(i - 1);
            TerrainSegment current = segments.get(i);
            assertTrue(current.connectedToPrevious);
            assertEquals(previous.farLeft, current.nearLeft);
            assertEquals(previous.farRight, current.nearRight);
            if (!current.nearRight.subtract(current.nearLeft).equals(
                    current.farRight.subtract(current.farLeft))) {
                changingCrossSections++;
            }
        }
        assertTrue(changingCrossSections > 0);

        assertEquals(segments.size(), world.patches().size());
        double supportCosine = Math.cos(Math.toRadians(50.0));
        for (int i = 0; i < segments.size(); i++) {
            TerrainSegment segment = segments.get(i);
            List<TerrainTriangle> triangles =
                    world.patches().get(i).triangles;
            assertEquals(2, triangles.size());
            assertEquals(segment.nearLeft, triangles.get(0).a);
            assertEquals(segment.nearRight, triangles.get(0).b);
            assertEquals(segment.farRight, triangles.get(0).c);
            assertEquals(segment.nearLeft, triangles.get(1).a);
            assertEquals(segment.farRight, triangles.get(1).b);
            assertEquals(segment.farLeft, triangles.get(1).c);
            if (i > 0) {
                List<TerrainTriangle> previousTriangles =
                        world.patches().get(i - 1).triangles;
                assertTrue(world.isWalkableTransition(
                        previousTriangles.get(1).id,
                        triangles.get(0).id,
                        supportCosine));
            }
        }
    }

    @Test
    public void selectedSurfaceMaterialIsCopiedIntoAuthoritativeTriangles() {
        TerrainWorld terrain = new TrackBuilder(4.0)
                .material(SurfaceMaterial.BOOST)
                .straight(5.0)
                .material(SurfaceMaterial.NORMAL)
                .straight(5.0)
                .build();

        assertEquals(SurfaceMaterial.BOOST, terrain.triangles().get(0).material);
        assertEquals(SurfaceMaterial.BOOST, terrain.triangles().get(1).material);
        assertEquals(SurfaceMaterial.NORMAL, terrain.triangles().get(2).material);
        assertEquals(SurfaceMaterial.NORMAL, terrain.triangles().get(3).material);
    }

    @Test
    public void connectedSupportableSegmentsPublishWalkableEdgeAdjacency() {
        TerrainWorld connected = new TrackBuilder(4.0)
                .straight(5.0)
                .slope(5.0, 1.0)
                .build();
        TerrainWorld separated = new TrackBuilder(4.0)
                .straight(5.0)
                .gap(1.0)
                .slope(5.0, 1.0)
                .build();
        double supportCosine = Math.cos(Math.toRadians(50.0));

        assertTrue(connected.isWalkableTransition(2L, 3L, supportCosine));
        assertTrue(!separated.isWalkableTransition(2L, 3L, supportCosine));
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroTrackWidthIsRejected() {
        new TrackBuilder(0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPositiveSegmentLengthIsRejected() {
        new TrackBuilder(4.0).straight(0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidTurnSubdivisionCountIsRejected() {
        new TrackBuilder(4.0).turnDegrees(45.0, 3.0, 0);
    }
}
