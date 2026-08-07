package com.example.game3d.core.simulation;

import com.example.game3d.core.input.FixedStepInput;
import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.SurfaceMaterial;
import com.example.game3d.core.terrain.TerrainTriangle;
import com.example.game3d.core.terrain.TerrainWorld;
import com.example.game3d.core.terrain.TrackBuilder;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Focused terrain-boundary contacts that are not covered by the basic cylinder support tests.
 */
public class CylinderTerrainContactTest {
    private final PhysicsConfig config = new PhysicsConfig();

    @Test
    public void bothTrianglesOwnTheirSharedDiagonalWithoutAContactSeam() {
        TerrainWorld terrain = new TrackBuilder(20.0)
                .straight(10.0)
                .straight(100.0)
                .build();
        TerrainTriangle first = terrain.triangles().get(0);
        TerrainTriangle second = terrain.triangles().get(1);
        Vec3 centerOnDiagonal = new Vec3(
                0.0, config.cylinderRadius - 0.01, -3.0);

        CylinderCollider.ContactCandidate firstContact = CylinderCollider.contact(
                centerOnDiagonal, new Vec3(1.0, 0.0, 0.0), config, first);
        CylinderCollider.ContactCandidate secondContact = CylinderCollider.contact(
                centerOnDiagonal, new Vec3(1.0, 0.0, 0.0), config, second);

        assertNotNull("first half of the quad rejected its shared diagonal", firstContact);
        assertNotNull("second half of the quad rejected its shared diagonal", secondContact);
        assertVecEquals(firstContact.normal, secondContact.normal, 1.0e-12);
        assertEquals(firstContact.penetration, secondContact.penetration, 1.0e-12);

        SimulationEngine engine = new SimulationEngine(terrain, config,
                new Vec3(0.0, config.cylinderRadius + 0.002, 1.0),
                0, StepObserver.NONE);
        double maximumHeightError = 0.0;
        int bounces = 0;
        for (int tick = 0; tick < 100; tick++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            maximumHeightError = Math.max(maximumHeightError,
                    Math.abs(result.snapshot.absolutePosition.y - config.cylinderRadius));
            bounces += countEvents(result, SimulationEvent.Type.BOUNCE);
            if (result.snapshot.absolutePosition.z < -4.0) {
                break;
            }
        }

        assertTrue("simulation never crossed the shared-diagonal region",
                engine.snapshot().absolutePosition.z < -4.0);
        assertEquals("a coplanar internal edge must not produce a bounce", 0, bounces);
        assertTrue("shared diagonal introduced a vertical position discontinuity",
                maximumHeightError < 0.004);
        assertTrue(engine.snapshot().grounded);
    }

    @Test
    public void cylinderSideContactsTriangleBoundaryEdgeWhenFaceProjectionIsOutside() {
        TerrainTriangle triangle = new TerrainTriangle(
                1L,
                new Vec3(-2.0, 0.0, 0.0),
                new Vec3(2.0, 0.0, 0.0),
                new Vec3(0.0, 0.0, -4.0),
                SurfaceMaterial.NORMAL);
        double radius = config.cylinderRadius;
        Vec3 center = new Vec3(0.0, radius * 0.80, radius * 0.30);

        CylinderCollider.ContactCandidate contact = CylinderCollider.contact(
                center, new Vec3(1.0, 0.0, 0.0), config, triangle);

        assertNotNull("cylinder overlaps the z=0 triangle edge but no contact was generated",
                contact);
        assertTrue(contact.penetration > 0.0);
        assertTrue("edge normal should point both upward and out of the triangle",
                contact.normal.y > 0.5 && contact.normal.z > 0.1);
    }

    @Test
    public void cylinderSideContactsTriangleVertexWhenAllFaceSamplesAreOutside() {
        TerrainTriangle triangle = new TerrainTriangle(
                1L,
                new Vec3(0.0, 0.0, 0.0),
                new Vec3(0.0, 0.0, -4.0),
                new Vec3(-4.0, 0.0, 0.0),
                SurfaceMaterial.NORMAL);
        double radius = config.cylinderRadius;
        Vec3 center = new Vec3(
                config.cylinderHalfLength - radius * 0.05,
                radius * 0.75,
                radius * 0.35);

        CylinderCollider.ContactCandidate contact = CylinderCollider.contact(
                center, new Vec3(1.0, 0.0, 0.0), config, triangle);

        assertNotNull("triangle origin lies inside the finite cylinder but was missed",
                contact);
        assertTrue(contact.penetration > 0.0);
        assertTrue("vertex normal should point upward and toward positive z",
                contact.normal.y > 0.5 && contact.normal.z > 0.1);
    }

    @Test
    public void leavingLateralTrackEdgeRemovesSupportAtCylinderCap() {
        double trackWidth = 2.0;
        double halfWidth = trackWidth * 0.5;
        TerrainWorld terrain = new TrackBuilder(trackWidth).straight(200.0).build();
        SimulationEngine engine = new SimulationEngine(terrain, config,
                new Vec3(0.0, config.cylinderRadius + 0.002, 1.0),
                0, StepObserver.NONE);
        double quarterTurnScreenHeights =
                (Math.PI * 0.5) / config.facingRadiansPerScreenHeight;
        boolean establishedSupport = false;
        boolean leftSupport = false;

        for (int tick = 0; tick < 120; tick++) {
            FixedStepInput input = tick == 0
                    ? new FixedStepInput(Collections.singletonList(
                    PlayerInputEvent.swipe(0L, 1L, quarterTurnScreenHeights, 0.0)))
                    : FixedStepInput.EMPTY;
            StepResult result = engine.step(input);
            if (result.snapshot.grounded) {
                establishedSupport = true;
            } else if (establishedSupport) {
                leftSupport = true;
                assertTrue("support disappeared before the trailing cap passed the edge",
                        result.snapshot.absolutePosition.x
                                >= halfWidth + config.cylinderHalfLength - 0.03);
                break;
            }
        }

        assertTrue("the body never established initial track support", establishedSupport);
        assertTrue("the body remained grounded beyond the lateral edge", leftSupport);
        assertFalse(engine.snapshot().grounded);
    }

    @Test
    public void gentleSlopeToFlatSeamDoesNotBounceOrPenetrateTheFlat() {
        double slopeLength = 12.0;
        double rise = 0.60;
        double seamZ = 2.0 - slopeLength;
        TerrainWorld terrain = new TrackBuilder(20.0)
                .slope(slopeLength, rise)
                .straight(150.0)
                .build();
        double slopeAngle = Math.atan2(rise, slopeLength);
        Vec3 slopeNormal = new Vec3(
                0.0, Math.cos(slopeAngle), Math.sin(slopeAngle));
        double initialForwardDistance = 9.0;
        Vec3 surfacePoint = new Vec3(
                0.0,
                rise * initialForwardDistance / slopeLength,
                2.0 - initialForwardDistance);
        SimulationEngine engine = new SimulationEngine(terrain, config,
                surfacePoint.add(slopeNormal.multiply(config.cylinderRadius)),
                0, StepObserver.NONE);
        int bounces = 0;
        boolean crossedSeam = false;
        double maximumHeightAfterSeam = Double.NEGATIVE_INFINITY;
        double minimumHeightAfterSeam = Double.POSITIVE_INFINITY;

        for (int tick = 0; tick < 90; tick++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            bounces += countEvents(result, SimulationEvent.Type.BOUNCE);
            if (result.snapshot.absolutePosition.z < seamZ) {
                crossedSeam = true;
                maximumHeightAfterSeam = Math.max(maximumHeightAfterSeam,
                        result.snapshot.absolutePosition.y);
                minimumHeightAfterSeam = Math.min(minimumHeightAfterSeam,
                        result.snapshot.absolutePosition.y);
            }
            if (result.snapshot.absolutePosition.z < seamZ - 8.0
                    && result.snapshot.grounded) {
                break;
            }
        }

        double restingHeight = rise + config.cylinderRadius;
        assertTrue("simulation never crossed the slope-to-flat seam", crossedSeam);
        assertEquals("gentle continuous seam must not generate bounce impulses", 0, bounces);
        assertTrue("body penetrated through the flat after crossing the seam",
                minimumHeightAfterSeam >= restingHeight - 0.003);
        assertTrue("seam generated an implausible upward launch",
                maximumHeightAfterSeam <= restingHeight + 0.10);
        assertTrue("body did not settle back onto the flat", engine.snapshot().grounded);
    }

    private static int countEvents(StepResult result, SimulationEvent.Type type) {
        int count = 0;
        for (SimulationEvent event : result.events) {
            if (event.type == type) {
                count++;
            }
        }
        return count;
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual, double tolerance) {
        assertEquals(expected.x, actual.x, tolerance);
        assertEquals(expected.y, actual.y, tolerance);
        assertEquals(expected.z, actual.z, tolerance);
    }
}
