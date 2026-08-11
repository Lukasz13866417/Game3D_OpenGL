package com.example.game3d.core.simulation;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.SurfaceMaterial;
import com.example.game3d.core.terrain.TerrainTriangle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Scale, tolerance-shell, and oblique-motion regressions for analytic cylinder-triangle CCD. */
public class CylinderTriangleCastEdgeCaseTest {
    @Test
    public void customTargetDistanceIsAppliedToAnEdgeCast() {
        double radius = 0.25;
        double targetDistance = 0.10;
        double centerHeight = 0.05;
        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.0, centerHeight, 2.0),
                new Vec3(0.0, 0.0, -4.0),
                new Vec3(1.0, 0.0, 0.0),
                0.5, radius, boundaryTriangle(), 0b001, targetDistance);

        assertEquals(CylinderTriangleCast.Status.HIT, hit.status);
        double expectedRadialZ = Math.sqrt(
                (radius + targetDistance) * (radius + targetDistance)
                        - centerHeight * centerHeight);
        assertEquals((2.0 - expectedRadialZ) / 4.0, hit.fraction, 1.0e-8);
        assertEquals(targetDistance, hit.signedSeparation, 1.0e-8);
    }

    @Test
    public void customTargetDistanceIsAppliedToAVertexCast() {
        double radius = 0.25;
        double targetDistance = 0.10;
        double centerHeight = 0.05;
        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.5, centerHeight, 2.0),
                new Vec3(0.0, 0.0, -4.0),
                new Vec3(1.0, 0.0, 0.0),
                0.5, radius, cornerTriangle(), 0b101, targetDistance);

        assertEquals(CylinderTriangleCast.Status.HIT, hit.status);
        double expectedRadialZ = Math.sqrt(
                (radius + targetDistance) * (radius + targetDistance)
                        - centerHeight * centerHeight);
        assertEquals((2.0 - expectedRadialZ) / 4.0, hit.fraction, 1.0e-8);
        assertEquals(targetDistance, hit.signedSeparation, 1.0e-8);
    }

    @Test
    public void edgeAlreadyInsideTargetShellIsReportedAtFrameStart() {
        double radius = 0.25;
        double targetDistance = 0.10;
        double radialDistance = radius + targetDistance * 0.5;
        double centerHeight = 0.05;
        double centerZ = Math.sqrt(
                radialDistance * radialDistance - centerHeight * centerHeight);
        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.0, centerHeight, centerZ),
                new Vec3(0.0, 0.0, -1.0),
                new Vec3(1.0, 0.0, 0.0),
                0.5, radius, boundaryTriangle(), 0b001, targetDistance);

        assertEquals(CylinderTriangleCast.Status.HIT, hit.status);
        assertEquals(0.0, hit.fraction, 0.0);
        assertEquals(targetDistance * 0.5, hit.signedSeparation, 1.0e-9);
    }

    @Test
    public void smallButValidTriangleStillGetsAFaceHit() {
        TerrainTriangle small = new TerrainTriangle(
                10L,
                new Vec3(-0.0005, 0.0, 0.0005),
                new Vec3(0.0005, 0.0, 0.0005),
                new Vec3(0.0, 0.0, -0.0005),
                SurfaceMaterial.NORMAL);
        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.0, 1.0, 0.0),
                new Vec3(0.0, -2.0, 0.0),
                new Vec3(1.0, 0.0, 0.0),
                0.0001, 0.0001, small, 0);

        assertEquals(CylinderTriangleCast.Status.HIT, hit.status);
        assertEquals(CylinderTriangleCast.Feature.FACE, hit.feature);
    }

    @Test
    public void scalingTheAxisDoesNotChangeTheCast() {
        TerrainTriangle triangle = boundaryTriangle();
        Vec3 start = new Vec3(0.0, 0.05, 2.0);
        Vec3 translation = new Vec3(0.0, 0.0, -4.0);
        CylinderTriangleCast.SweepHit unit = CylinderTriangleCast.cast(
                start, translation, new Vec3(1.0, 0.0, 0.0),
                0.5, 0.25, triangle, 0b111);
        CylinderTriangleCast.SweepHit scaled = CylinderTriangleCast.cast(
                start, translation, new Vec3(17.0, 0.0, 0.0),
                0.5, 0.25, triangle, 0b111);

        assertEquals(unit.status, scaled.status);
        assertEquals(unit.feature, scaled.feature);
        assertEquals(unit.fraction, scaled.fraction, 0.0);
        assertEquals(unit.normal.x, scaled.normal.x, 0.0);
        assertEquals(unit.normal.y, scaled.normal.y, 0.0);
        assertEquals(unit.normal.z, scaled.normal.z, 0.0);
    }

    @Test
    public void activeEdgeHighSpeedSweepDoesNotTunnelAtObliqueAxis() {
        Vec3 obliqueAxis = new Vec3(1.0, 0.2, 0.4).normalized();
        CylinderTriangleCast.SweepHit hit = CylinderTriangleCast.cast(
                new Vec3(0.0, 0.12, 100.0),
                new Vec3(0.0, -0.03, -200.0),
                obliqueAxis, 0.5, 0.25, boundaryTriangle(), 0b001);

        assertEquals(CylinderTriangleCast.Status.HIT, hit.status);
        assertTrue(hit.fraction > 0.0 && hit.fraction < 1.0);
        assertTrue(hit.normal.dot(Vec3.UP) > 0.0);
    }

    private static TerrainTriangle boundaryTriangle() {
        return new TerrainTriangle(
                3L,
                new Vec3(-2.0, 0.0, 0.0),
                new Vec3(2.0, 0.0, 0.0),
                new Vec3(0.0, 0.0, -4.0),
                SurfaceMaterial.NORMAL);
    }

    private static TerrainTriangle cornerTriangle() {
        return new TerrainTriangle(
                4L,
                Vec3.ZERO,
                new Vec3(0.0, 0.0, -4.0),
                new Vec3(-4.0, 0.0, 0.0),
                SurfaceMaterial.NORMAL);
    }
}
