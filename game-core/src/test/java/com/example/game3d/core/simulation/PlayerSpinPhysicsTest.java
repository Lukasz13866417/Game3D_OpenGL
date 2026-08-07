package com.example.game3d.core.simulation;

import com.example.game3d.core.input.FixedStepInput;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.SurfaceMaterial;
import com.example.game3d.core.terrain.TerrainWorld;
import com.example.game3d.core.terrain.TrackBuilder;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PlayerSpinPhysicsTest {
    private final PhysicsConfig config = new PhysicsConfig();

    @Test
    public void supportedSpinIsNoSlipAndIntegratesActualDistance() {
        SimulationEngine engine = engine(
                new TrackBuilder(20.0).straight(500.0).build(),
                restingStart(), Vec3.ZERO, 0.0, StepObserver.NONE);
        settle(engine, 240);

        PlayerSnapshot before = engine.snapshot();
        PlayerSnapshot after = engine.step(FixedStepInput.EMPTY).snapshot;
        Vec3 tangent = rollingTangent(after);
        double signedDistance =
                after.absolutePosition.subtract(before.absolutePosition).dot(tangent);

        assertTrue(after.grounded);
        assertEquals(after.velocity.dot(tangent),
                -after.angularVelocity * config.cylinderRadius, 1.0e-9);
        assertEquals(-signedDistance / config.cylinderRadius,
                after.axleDeltaRadians, 1.0e-9);
        assertEquals(wrap(before.axleRadians + after.axleDeltaRadians),
                after.axleRadians, 1.0e-9);
    }

    @Test
    public void boostSpinFollowsBoostedTranslationNotUnboostedMotorState() {
        SimulationEngine engine = engine(
                new TrackBuilder(20.0).material(SurfaceMaterial.BOOST)
                        .straight(500.0).build(),
                restingStart(), Vec3.ZERO, 0.0, StepObserver.NONE);
        settle(engine, 300);

        PlayerSnapshot snapshot = engine.snapshot();
        Vec3 tangent = rollingTangent(snapshot);
        double tangentSpeed = snapshot.velocity.dot(tangent);

        assertTrue(snapshot.grounded);
        assertTrue(tangentSpeed > snapshot.driveSurfaceSpeed * 1.5);
        assertEquals(tangentSpeed,
                -snapshot.angularVelocity * config.cylinderRadius, 1.0e-9);
    }

    @Test
    public void highSpeedAirSpinRetainsExactMultiTurnTickDelta() {
        double initialOmega = -1000.0;
        SimulationEngine engine = engine(
                new TerrainWorld(Collections.emptyList()),
                new Vec3(0.0, 10.0, 0.0),
                new Vec3(3.0, 0.0, -7.0),
                initialOmega, StepObserver.NONE);

        PlayerSnapshot snapshot = engine.step(FixedStepInput.EMPTY).snapshot;
        double expectedEndOmega =
                initialOmega + config.airAngularAcceleration
                        * PhysicsConfig.FIXED_DT_SECONDS;
        double expectedDelta =
                (initialOmega + expectedEndOmega) * 0.5
                        * PhysicsConfig.FIXED_DT_SECONDS;

        assertTrue(Math.abs(expectedDelta) > Math.PI * 2.0);
        assertEquals(expectedEndOmega, snapshot.angularVelocity, 1.0e-12);
        assertEquals(expectedDelta, snapshot.axleDeltaRadians, 1.0e-12);
        assertEquals(wrap(expectedDelta), snapshot.axleRadians, 1.0e-12);
        assertEquals(3.0, snapshot.velocity.x, 0.0);
        assertEquals(-7.0, snapshot.velocity.z, 0.0);
    }

    @Test
    public void landingRecordsAirMotionThenAnExplicitNoSlipSnap() {
        final StepRecord[] latest = new StepRecord[1];
        SimulationEngine engine = engine(
                new TrackBuilder(20.0).straight(100.0).build(),
                new Vec3(0.0, config.cylinderRadius + 0.05, 1.0),
                new Vec3(0.0, -2.0, -4.0),
                35.0,
                new StepObserver() {
                    @Override
                    public void onStep(StepRecord record) {
                        latest[0] = record;
                    }
                });

        StepResult landing = null;
        for (int tick = 0; tick < 30; tick++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            if (hasEvent(result, SimulationEvent.Type.LAND)) {
                landing = result;
                break;
            }
        }

        assertNotNull(landing);
        SpinSegment snap = null;
        boolean sawAir = false;
        for (SpinSegment segment : latest[0].spinSegments) {
            sawAir |= segment.mode == SpinSegment.Mode.AIR_MOTOR;
            if (segment.mode == SpinSegment.Mode.LANDING_SNAP) {
                snap = segment;
            }
        }
        assertTrue(sawAir);
        assertNotNull(snap);
        assertEquals(snap.startFraction, snap.endFraction, 0.0);
        assertEquals(0.0, snap.deltaRadians, 0.0);
        assertEquals(landing.snapshot.angularVelocity,
                snap.endAngularVelocity, 1.0e-12);
        assertEquals(landing.snapshot.velocity.dot(rollingTangent(landing.snapshot)),
                -landing.snapshot.angularVelocity * config.cylinderRadius, 1.0e-9);
    }

    private SimulationEngine engine(
            TerrainWorld terrain, Vec3 position, Vec3 velocity,
            double initialOmega, StepObserver observer) {
        return new SimulationEngine(
                terrain, config, position, velocity, initialOmega, 0, observer);
    }

    private Vec3 restingStart() {
        return new Vec3(0.0, config.cylinderRadius + 0.002, 1.0);
    }

    private static void settle(SimulationEngine engine, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            engine.step(FixedStepInput.EMPTY);
        }
    }

    private static Vec3 rollingTangent(PlayerSnapshot snapshot) {
        Vec3 tangent = snapshot.supportNormal.cross(snapshot.cylinderAxis).normalized();
        return tangent.dot(snapshot.heading) < 0.0
                ? tangent.multiply(-1.0) : tangent;
    }

    private static double wrap(double radians) {
        return Math.IEEEremainder(radians, Math.PI * 2.0);
    }

    private static boolean hasEvent(StepResult result, SimulationEvent.Type type) {
        for (SimulationEvent event : result.events) {
            if (event.type == type) {
                return true;
            }
        }
        return false;
    }
}
