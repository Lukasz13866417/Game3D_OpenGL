package com.example.game3d.core.simulation;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.SurfaceMaterial;
import com.example.game3d.core.terrain.TerrainTriangle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CylinderTriangleCastTest {
    private final PhysicsConfig config = new PhysicsConfig();
    private final Vec3 axis = new Vec3(1.0, 0.0, 0.0);

    @Test
    public void verticalDropMatchesAnalyticFlatPlaneTimeOfImpact() {
        TerrainTriangle triangle = flatTriangle();
        Vec3 start = new Vec3(0.0, 1.0, 0.0);
        Vec3 translation = new Vec3(0.0, -2.0, 0.0);

        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                start, translation, axis,
                config.cylinderHalfLength, config.cylinderRadius,
                triangle, 0b111);

        assertEquals(CylinderTriangleCast.Status.HIT, hit.status);
        double expected = (1.0 - config.cylinderRadius) / 2.0;
        assertEquals(expected, hit.fraction, 2.0e-7);
        assertEquals(config.cylinderRadius, hit.centerAtImpact.y, 5.0e-7);
        assertEquals(0.0, hit.terrainPoint.y, 1.0e-9);
        assertEquals(1.0, hit.normal.y, 1.0e-8);
        assertEquals(CylinderTriangleCast.Feature.FACE, hit.feature);
    }

    @Test
    public void highSpeedDropCannotTunnelThroughTriangle() {
        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.0, 20.0, 0.0),
                new Vec3(0.0, -100.0, 0.0),
                axis, config.cylinderHalfLength, config.cylinderRadius,
                flatTriangle(), 0b111);

        assertEquals(CylinderTriangleCast.Status.HIT, hit.status);
        assertTrue(hit.fraction > 0.0 && hit.fraction < 1.0);
        assertEquals(config.cylinderRadius, hit.centerAtImpact.y, 5.0e-7);
        assertTrue(hit.iterations <= 24);
    }

    @Test
    public void movingAwayFromFrontFaceDoesNotHit() {
        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.0, 1.0, 0.0),
                new Vec3(0.0, 2.0, 0.0),
                axis, config.cylinderHalfLength, config.cylinderRadius,
                flatTriangle(), 0b111);

        assertEquals(CylinderTriangleCast.Status.MISS, hit.status);
    }

    @Test
    public void crossingOneSidedTriangleFromBelowDoesNotHit() {
        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.0, -1.0, 0.0),
                new Vec3(0.0, 2.0, 0.0),
                axis, config.cylinderHalfLength, config.cylinderRadius,
                flatTriangle(), 0b111);

        assertEquals(CylinderTriangleCast.Status.MISS, hit.status);
    }

    @Test
    public void returnedWitnessesAreFiniteAndCoincidentAtImpact() {
        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.0, 3.0, 0.0),
                new Vec3(0.0, -4.0, 0.0),
                axis, config.cylinderHalfLength, config.cylinderRadius,
                flatTriangle(), 0b111);

        assertEquals(CylinderTriangleCast.Status.HIT, hit.status);
        assertNotNull(hit.cylinderPoint);
        assertNotNull(hit.terrainPoint);
        assertTrue(hit.cylinderPoint.subtract(hit.terrainPoint).length() < 1.0e-6);
    }

    @Test
    public void axialFaceSegmentUsesItsFullIntervalRatherThanThreeSamples() {
        double halfLength = 1.0;
        double radius = 0.25;
        TerrainTriangle narrowTriangle = new TerrainTriangle(
                2L,
                new Vec3(0.20, 0.0, 0.10),
                new Vec3(0.30, 0.0, 0.10),
                new Vec3(0.25, 0.0, -0.10),
                SurfaceMaterial.NORMAL);

        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.0, 1.0, 0.0),
                new Vec3(0.0, -2.0, 0.0),
                axis, halfLength, radius, narrowTriangle, 0b111);

        assertEquals(CylinderTriangleCast.Status.HIT, hit.status);
        assertEquals((1.0 - radius) / 2.0, hit.fraction, 2.0e-7);
        assertEquals(CylinderTriangleCast.Feature.FACE, hit.feature);
        assertTrue("face witness must lie between the cylinder's axial endpoints",
                hit.cylinderPoint.x >= -halfLength
                        && hit.cylinderPoint.x <= halfLength);
        assertTrue("face witness must project into the narrow triangle",
                narrowTriangle.containsProjectedPoint(hit.terrainPoint, 1.0e-8));
    }

    @Test
    public void lateralSweepHitsActiveTriangleEdgeWithEdgeNormal() {
        double halfLength = 0.50;
        double radius = 0.25;
        double centerHeight = radius * 0.25;
        TerrainTriangle triangle = boundaryTriangle();

        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.0, centerHeight, 10.0),
                new Vec3(0.0, 0.0, -20.0),
                axis, halfLength, radius, triangle, 0b111);

        assertEquals(CylinderTriangleCast.Status.HIT, hit.status);
        assertEquals(CylinderTriangleCast.Feature.EDGE_AB, hit.feature);
        double expectedContactZ =
                Math.sqrt(radius * radius - centerHeight * centerHeight);
        assertEquals((10.0 - expectedContactZ) / 20.0, hit.fraction, 2.0e-6);
        assertEquals(0.0, hit.terrainPoint.y, 1.0e-8);
        assertEquals(0.0, hit.terrainPoint.z, 1.0e-8);
        assertTrue("edge contact must point upward", hit.normal.y > 0.0);
        assertTrue("edge contact must point out of the triangle", hit.normal.z > 0.0);
    }

    @Test
    public void lateralSweepHitsTriangleVertexAtCylinderCap() {
        double halfLength = 0.50;
        double radius = 0.25;
        double centerHeight = radius * 0.40;
        TerrainTriangle triangle = cornerTriangle();

        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(halfLength, centerHeight, 10.0),
                new Vec3(0.0, 0.0, -20.0),
                axis, halfLength, radius, triangle, 0b111);

        assertEquals(CylinderTriangleCast.Status.HIT, hit.status);
        assertEquals(CylinderTriangleCast.Feature.VERTEX_A, hit.feature);
        double expectedContactZ =
                Math.sqrt(radius * radius - centerHeight * centerHeight);
        assertEquals((10.0 - expectedContactZ) / 20.0, hit.fraction, 2.0e-6);
        assertVecEquals(Vec3.ZERO, hit.terrainPoint, 1.0e-7);
        assertTrue("vertex contact must point upward", hit.normal.y > 0.0);
        assertTrue("vertex contact must point toward positive z", hit.normal.z > 0.0);
    }

    @Test
    public void inactiveSharedEdgeCannotProduceALateralBlockingNormal() {
        double radius = 0.25;
        double centerHeight = radius * 0.25;

        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.0, centerHeight, 1.0),
                new Vec3(0.0, 0.0, -2.0),
                axis, 0.50, radius, boundaryTriangle(),
                0b110); // AB is an inactive tessellation edge.

        assertTrue("an inactive edge must not make the cast fail",
                hit.status == CylinderTriangleCast.Status.MISS
                        || hit.status == CylinderTriangleCast.Status.HIT);
        if (hit.status == CylinderTriangleCast.Status.HIT) {
            assertTrue("an inactive shared edge may retain face support, "
                            + "but must not push the player laterally",
                    hit.normal.dot(Vec3.UP) > 1.0 - 1.0e-8
                            && Math.abs(hit.normal.z) < 1.0e-8);
        }
    }

    @Test
    public void capParallelToFaceHandlesDiskOverlapWhenCapCenterIsOutside() {
        double halfLength = 0.50;
        double radius = 0.25;
        Vec3 verticalAxis = Vec3.UP;
        TerrainTriangle triangle = boundaryTriangle();

        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.0, 2.0, 0.15),
                new Vec3(0.0, -3.0, 0.0),
                verticalAxis, halfLength, radius, triangle,
                0); // The cap overlaps the face; no physical boundary edge is required.

        assertEquals(CylinderTriangleCast.Status.HIT, hit.status);
        assertEquals((2.0 - halfLength) / 3.0, hit.fraction, 2.0e-7);
        assertEquals(halfLength, hit.centerAtImpact.y, 5.0e-7);
        assertEquals(1.0, hit.normal.y, 1.0e-8);
    }

    @Test
    public void startOverlapMovingIntoFaceIsReportedAtStart() {
        double penetration = 0.02;
        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.0, config.cylinderRadius - penetration, 0.0),
                new Vec3(0.0, -1.0, 0.0),
                axis, config.cylinderHalfLength, config.cylinderRadius,
                flatTriangle(), 0b111);

        assertEquals(CylinderTriangleCast.Status.START_OVERLAPPED, hit.status);
        assertEquals(0.0, hit.fraction, 0.0);
        assertEquals(CylinderTriangleCast.Feature.FACE, hit.feature);
        assertEquals(-penetration, hit.signedSeparation, 1.0e-9);
    }

    @Test
    public void startOverlapMovingAwayFromFaceDoesNotStick() {
        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.0, config.cylinderRadius - 0.02, 0.0),
                new Vec3(0.0, 1.0, 0.0),
                axis, config.cylinderHalfLength, config.cylinderRadius,
                flatTriangle(), 0b111);

        assertEquals(CylinderTriangleCast.Status.MISS, hit.status);
    }

    private static TerrainTriangle flatTriangle() {
        return new TerrainTriangle(
                1L,
                new Vec3(-10.0, 0.0, 10.0),
                new Vec3(10.0, 0.0, 10.0),
                new Vec3(0.0, 0.0, -10.0),
                SurfaceMaterial.NORMAL);
    }

    /** Upward-facing triangle whose AB edge is the finite boundary at z=0. */
    private static TerrainTriangle boundaryTriangle() {
        return new TerrainTriangle(
                3L,
                new Vec3(-2.0, 0.0, 0.0),
                new Vec3(2.0, 0.0, 0.0),
                new Vec3(0.0, 0.0, -4.0),
                SurfaceMaterial.NORMAL);
    }

    /** Upward-facing negative-x/negative-z quadrant with its exposed corner at A. */
    private static TerrainTriangle cornerTriangle() {
        return new TerrainTriangle(
                4L,
                Vec3.ZERO,
                new Vec3(0.0, 0.0, -4.0),
                new Vec3(-4.0, 0.0, 0.0),
                SurfaceMaterial.NORMAL);
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual, double tolerance) {
        assertEquals(expected.x, actual.x, tolerance);
        assertEquals(expected.y, actual.y, tolerance);
        assertEquals(expected.z, actual.z, tolerance);
    }
}
