package com.example.game3d.core.simulation;

import com.example.game3d.core.input.FixedStepInput;
import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.SurfaceMaterial;
import com.example.game3d.core.terrain.TerrainWorld;
import com.example.game3d.core.terrain.TrackBuilder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end dynamics checks. These deliberately assert physical invariants rather than exact
 * per-tick trajectories so tuning constants can change without making the tests meaningless.
 */
public class SimulationDynamicsTest {
    private static final double EPSILON = 1.0e-9;
    private final PhysicsConfig config = new PhysicsConfig();

    @Test
    public void lowSpeedLandingSettlesWithoutBounce() {
        TerrainWorld terrain = new TrackBuilder(20.0).straight(100.0).build();
        SimulationEngine engine = engine(terrain,
                new Vec3(0.0, config.cylinderRadius + 0.010, 1.0),
                new Vec3(0.0, -1.0, 0.0));

        StepResult landing = engine.step(FixedStepInput.EMPTY);

        assertTrue(hasEvent(landing, SimulationEvent.Type.LAND));
        assertFalse(hasEvent(landing, SimulationEvent.Type.BOUNCE));
        assertTrue(landing.snapshot.grounded);
        assertEquals(0.0, landing.snapshot.velocity.y, EPSILON);
        assertEquals(config.cylinderRadius, landing.snapshot.absolutePosition.y, 0.002);
    }

    @Test
    public void hardLandingBouncesAndIsNotReportedAsGroundedWhileMovingUp() {
        TerrainWorld terrain = new TrackBuilder(20.0).straight(100.0).build();
        SimulationEngine engine = engine(terrain,
                new Vec3(0.0, 1.5, 1.0), new Vec3(0.0, -12.0, 0.0));

        StepResult bounce = stepUntilEvent(engine, SimulationEvent.Type.BOUNCE, 60);

        assertNotNull("hard landing never bounced", bounce);
        assertTrue(bounce.snapshot.velocity.y > 0.0);
        assertFalse("a rebounding body cannot simultaneously be supported",
                bounce.snapshot.grounded);
        assertEquals(1, countEvents(bounce, SimulationEvent.Type.BOUNCE));
        SimulationEvent event = findEvent(bounce, SimulationEvent.Type.BOUNCE);
        assertTimedInsideResultTick(bounce, event);
        assertTrue("test drop should impact before the tick endpoint",
                event.tickFraction < 1.0);
        assertEquals(config.cylinderRadius, event.position.y, 1.0e-6);
        assertTrue("remaining tick time was not consumed after rebound",
                bounce.snapshot.absolutePosition.y > event.position.y);
    }

    @Test
    public void bounceThresholdBracketsNormalImpactSpeedAndUsesConfiguredRestitution() {
        TerrainWorld terrain = new TrackBuilder(20.0).straight(100.0).build();
        double margin = 0.01;
        double belowImpactSpeed = config.bounceSpeedThreshold - margin;
        double aboveImpactSpeed = config.bounceSpeedThreshold + margin;
        double gravityStep = config.gravity * PhysicsConfig.FIXED_DT_SECONDS;
        Vec3 start = new Vec3(0.0, config.cylinderRadius + 0.01, 1.0);

        SimulationEngine below = engine(terrain, start,
                new Vec3(0.0, -(belowImpactSpeed - gravityStep), 0.0));
        StepResult settled = below.step(FixedStepInput.EMPTY);
        assertTrue(hasEvent(settled, SimulationEvent.Type.LAND));
        assertFalse("sub-threshold impact unexpectedly bounced",
                hasEvent(settled, SimulationEvent.Type.BOUNCE));
        assertTrue(settled.snapshot.grounded);

        SimulationEngine above = engine(terrain, start,
                new Vec3(0.0, -(aboveImpactSpeed - gravityStep), 0.0));
        StepResult bounced = above.step(FixedStepInput.EMPTY);
        assertTrue("above-threshold impact did not bounce",
                hasEvent(bounced, SimulationEvent.Type.BOUNCE));
        assertFalse(bounced.snapshot.grounded);
        assertEquals(aboveImpactSpeed * config.restitution,
                bounced.snapshot.velocity.y, 1.0e-8);
    }

    @Test
    public void hardLandingBounceRedirectsHorizontalVelocityTowardFacing() {
        TerrainWorld terrain = new TrackBuilder(20.0).straight(100.0).build();
        SimulationEngine engine = engine(
                terrain,
                new Vec3(0.0, 1.5, 1.0),
                new Vec3(0.0, -12.0, -8.0));
        double quarterTurnSwipe =
                (Math.PI * 0.5) / config.facingRadiansPerScreenHeight;

        StepResult turned = engine.step(new FixedStepInput(
                Collections.singletonList(
                        PlayerInputEvent.swipe(
                                0L, 1L, quarterTurnSwipe, 0.0))));
        assertEquals(
                0.0,
                turned.snapshot.velocity.withY(0.0)
                        .normalized()
                        .dot(turned.snapshot.heading),
                EPSILON);

        StepResult bounce =
                stepUntilEvent(engine, SimulationEvent.Type.BOUNCE, 60);

        assertNotNull("hard landing never bounced", bounce);
        Vec3 bounceDirection =
                bounce.snapshot.velocity.withY(0.0).normalized();
        assertEquals(1.0, bounceDirection.dot(bounce.snapshot.heading), EPSILON);
    }

    @Test
    public void connectedWalkableRampSeamTransfersSupportWithoutBounce() {
        TerrainWorld terrain = new TrackBuilder(6.0)
                .straight(18.0)
                .slope(12.0, 5.0)
                .straight(100.0)
                .build();
        SimulationEngine engine = engine(terrain, restingStart(), Vec3.ZERO);
        int bounces = 0;
        boolean climbedRamp = false;
        for (int tick = 0; tick < 180; tick++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            bounces += countEvents(result, SimulationEvent.Type.BOUNCE);
            if (result.snapshot.absolutePosition.y
                    > config.cylinderRadius + 1.0) {
                climbedRamp = true;
                assertTrue("connected ramp transition lost support",
                        result.snapshot.grounded);
                break;
            }
        }

        assertTrue("player never climbed onto the connected ramp", climbedRamp);
        assertEquals("connected support faces must not create restitution bounces",
                0, bounces);
    }

    @Test
    public void motorAcceleratesMonotonicallyWithoutOvershootingCruiseSpeed() {
        TerrainWorld terrain = new TrackBuilder(20.0).straight(300.0).build();
        SimulationEngine engine = engine(terrain, restingStart(), Vec3.ZERO);
        double previousSpeed = 0.0;

        for (int tick = 0; tick < 180; tick++) {
            PlayerSnapshot snapshot = engine.step(FixedStepInput.EMPTY).snapshot;
            double speed = horizontalSpeed(snapshot.velocity);
            assertTrue("motor reversed or lost speed at tick " + tick,
                    speed + 1.0e-8 >= previousSpeed);
            assertTrue("motor overshot its configured cruise speed",
                    speed <= config.cruisingSpeed + 0.02);
            previousSpeed = speed;
        }

        assertEquals(config.cruisingSpeed, previousSpeed, 0.1);
        assertEquals(config.cruisingSpeed,
                -engine.snapshot().angularVelocity * config.cylinderRadius, 0.1);
    }

    @Test
    public void airborneMotorSpinDoesNotAccelerateHorizontalTranslation() {
        TerrainWorld empty = new TerrainWorld(Collections.emptyList());
        Vec3 initialVelocity = new Vec3(3.0, 0.0, -7.0);
        SimulationEngine engine = engine(empty, new Vec3(0.0, 10.0, 0.0),
                initialVelocity);

        for (int tick = 0; tick < 30; tick++) {
            engine.step(FixedStepInput.EMPTY);
        }

        assertEquals(initialVelocity.x, engine.snapshot().velocity.x, EPSILON);
        assertEquals(initialVelocity.z, engine.snapshot().velocity.z, EPSILON);
        assertTrue("the axle motor should still spin up in air",
                engine.snapshot().angularVelocity < 0.0);
    }

    @Test
    public void boostSurfaceHasHigherBoundedSteadySpeedThanNormalSurface() {
        SimulationEngine normal = engine(
                new TrackBuilder(20.0).straight(500.0).build(), restingStart(), Vec3.ZERO);
        SimulationEngine boost = engine(
                new TrackBuilder(20.0).material(SurfaceMaterial.BOOST)
                        .straight(500.0).build(),
                restingStart(), Vec3.ZERO);

        for (int tick = 0; tick < 240; tick++) {
            normal.step(FixedStepInput.EMPTY);
            boost.step(FixedStepInput.EMPTY);
        }

        double normalSpeed = horizontalSpeed(normal.snapshot().velocity);
        double boostSpeed = horizontalSpeed(boost.snapshot().velocity);
        assertTrue(normal.snapshot().grounded);
        assertTrue(boost.snapshot().grounded);
        assertTrue("boost must materially increase translation speed",
                boostSpeed > normalSpeed * 1.5);
        assertTrue("boost must remain bounded by its configured multiplier",
                boostSpeed <= config.cruisingSpeed
                        * SurfaceMaterial.BOOST.motorSpeedMultiplier + 0.1);
    }

    @Test
    public void supportedSlopeMaintainsRadiusAndTangentialVelocity() {
        double angleRadians = Math.toRadians(30.0);
        double risePerForwardUnit = Math.tan(angleRadians);
        TerrainWorld terrain = new TrackBuilder(20.0)
                .slope(150.0, risePerForwardUnit * 150.0)
                .build();
        Vec3 normal = new Vec3(0.0, Math.cos(angleRadians),
                Math.sin(angleRadians));
        double forwardDistance = 20.0;
        Vec3 surfacePoint = new Vec3(0.0, risePerForwardUnit * forwardDistance,
                2.0 - forwardDistance);
        SimulationEngine engine = engine(terrain,
                surfacePoint.add(normal.multiply(config.cylinderRadius)), Vec3.ZERO);

        for (int tick = 0; tick < 60; tick++) {
            engine.step(FixedStepInput.EMPTY);
        }

        PlayerSnapshot snapshot = engine.snapshot();
        double signedDistance = snapshot.absolutePosition.subtract(
                new Vec3(0.0, 0.0, 2.0)).dot(normal);
        assertTrue(snapshot.grounded);
        assertEquals(config.cylinderRadius, signedDistance, 0.003);
        assertEquals("supported velocity should be tangent to the slope",
                0.0, snapshot.velocity.dot(normal), 0.02);
        assertTrue("the motor should move the body uphill", snapshot.velocity.y > 0.0);
        Vec3 rollingTangent =
                snapshot.supportNormal.cross(snapshot.cylinderAxis).normalized();
        if (rollingTangent.dot(snapshot.heading) < 0.0) {
            rollingTangent = rollingTangent.multiply(-1.0);
        }
        assertEquals("slope spin must use actual tangent speed",
                snapshot.velocity.dot(rollingTangent),
                -snapshot.angularVelocity * config.cylinderRadius, 1.0e-9);
    }

    @Test
    public void slopeAboveSupportLimitDoesNotBecomeGrounded() {
        double angleRadians = Math.toRadians(55.0);
        double risePerForwardUnit = Math.tan(angleRadians);
        TerrainWorld terrain = new TrackBuilder(20.0)
                .slope(150.0, risePerForwardUnit * 150.0)
                .build();
        Vec3 normal = new Vec3(0.0, Math.cos(angleRadians),
                Math.sin(angleRadians));
        double forwardDistance = 40.0;
        Vec3 surfacePoint = new Vec3(0.0, risePerForwardUnit * forwardDistance,
                2.0 - forwardDistance);
        SimulationEngine engine = engine(terrain,
                surfacePoint.add(normal.multiply(config.cylinderRadius)), Vec3.ZERO);

        for (int tick = 0; tick < 30; tick++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            assertFalse("55 degree face must not count as support", result.snapshot.grounded);
            assertFalse(hasEvent(result, SimulationEvent.Type.LAND));
        }

        assertTrue("gravity should slide the body toward the low end",
                engine.snapshot().absolutePosition.z > surfacePoint.z);
    }

    @Test
    public void shortGapProducesARealAirborneIntervalAndOneLanding() {
        TerrainWorld terrain = new TrackBuilder(20.0)
                .straight(30.0)
                .gap(1.0)
                .straight(250.0)
                .build();
        SimulationEngine engine = engine(terrain, restingStart(), Vec3.ZERO);
        for (int tick = 0; tick < 5; tick++) {
            engine.step(FixedStepInput.EMPTY);
        }
        assertTrue("pre-gap track did not establish support", engine.snapshot().grounded);
        boolean wasGrounded = true;
        boolean becameAirborneAfterGrounding = false;
        int landings = 0;

        for (int tick = 0; tick < 360; tick++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            if (wasGrounded && !result.snapshot.grounded) {
                becameAirborneAfterGrounding = true;
            }
            wasGrounded = result.snapshot.grounded;
            landings += countEvents(result, SimulationEvent.Type.LAND);
            if (becameAirborneAfterGrounding && landings == 1
                    && result.snapshot.grounded) {
                break;
            }
        }

        assertTrue("gap was invisibly bridged by support", becameAirborneAfterGrounding);
        assertEquals("gap traversal should produce one landing", 1, landings);
        assertTrue(engine.snapshot().grounded);
        assertFalse(engine.snapshot().dead);
    }

    @Test
    public void crossingOneSidedTerrainFromBelowCreatesNoContact() {
        TerrainWorld terrain = new TrackBuilder(20.0).straight(100.0).build();
        final List<ContactSnapshot> contacts = new ArrayList<ContactSnapshot>();
        StepObserver observer = new StepObserver() {
            @Override
            public void onStep(StepRecord record) {
                contacts.addAll(record.contacts);
            }
        };
        SimulationEngine engine = new SimulationEngine(terrain, config,
                new Vec3(0.0, -0.30, 1.0), new Vec3(0.0, 20.0, 0.0),
                0, observer);

        for (int tick = 0; tick < 4; tick++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            assertFalse("back-face crossing must not create support", result.snapshot.grounded);
        }

        assertTrue("back-face crossing must not create a collision manifold",
                contacts.isEmpty());
        assertTrue("the body should pass completely through the authored one-sided face",
                engine.snapshot().absolutePosition.y > config.cylinderRadius);
    }

    @Test
    public void stateHashChangesWhenFutureAffectingTouchStateChanges() {
        TerrainWorld empty = new TerrainWorld(Collections.emptyList());
        SimulationEngine untouched = engine(empty, new Vec3(0.0, 10.0, 0.0), Vec3.ZERO);
        SimulationEngine held = engine(empty, new Vec3(0.0, 10.0, 0.0), Vec3.ZERO);

        untouched.step(FixedStepInput.EMPTY);
        held.step(new FixedStepInput(Collections.singletonList(
                PlayerInputEvent.down(0L, 1L))));

        assertFalse("state hashes must include touchHeld because it changes future input behavior",
                untouched.snapshot().stateHash == held.snapshot().stateHash);
    }

    @Test
    public void inputListOrderDoesNotChangeDeterministicState() {
        TerrainWorld empty = new TerrainWorld(Collections.emptyList());
        SimulationEngine ordered = engine(empty, new Vec3(0.0, 10.0, 0.0), Vec3.ZERO);
        SimulationEngine shuffled = engine(empty, new Vec3(0.0, 10.0, 0.0), Vec3.ZERO);
        PlayerInputEvent down = PlayerInputEvent.down(0L, 1L);
        PlayerInputEvent swipe = PlayerInputEvent.swipe(0L, 2L, 0.15, -0.30);
        PlayerInputEvent up = PlayerInputEvent.up(0L, 3L);

        FixedStepInput chronological = new FixedStepInput(
                java.util.Arrays.asList(down, swipe, up));
        FixedStepInput reverseInsertion = new FixedStepInput(
                java.util.Arrays.asList(up, swipe, down));

        assertEquals(ordered.step(chronological).snapshot.stateHash,
                shuffled.step(reverseInsertion).snapshot.stateHash);
    }

    private SimulationEngine engine(TerrainWorld terrain, Vec3 position, Vec3 velocity) {
        return new SimulationEngine(terrain, config, position, velocity,
                0, StepObserver.NONE);
    }

    private Vec3 restingStart() {
        return new Vec3(0.0, config.cylinderRadius + 0.002, 1.0);
    }

    private static StepResult stepUntilEvent(SimulationEngine engine,
                                             SimulationEvent.Type type, int maxTicks) {
        for (int tick = 0; tick < maxTicks; tick++) {
            StepResult result = engine.step(FixedStepInput.EMPTY);
            if (hasEvent(result, type)) {
                return result;
            }
        }
        return null;
    }

    private static boolean hasEvent(StepResult result, SimulationEvent.Type type) {
        return countEvents(result, type) > 0;
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

    private static SimulationEvent findEvent(
            StepResult result, SimulationEvent.Type type) {
        for (SimulationEvent event : result.events) {
            if (event.type == type) {
                return event;
            }
        }
        throw new AssertionError("Missing event " + type);
    }

    private static void assertTimedInsideResultTick(
            StepResult result, SimulationEvent event) {
        assertNotNull(event.position);
        assertTrue(event.tickFraction > 0.0 && event.tickFraction <= 1.0);
        assertTrue(event.timeNanos
                > result.snapshot.timeNanos - PhysicsConfig.FIXED_DT_NANOS);
        assertTrue(event.timeNanos <= result.snapshot.timeNanos);
    }

    private static double horizontalSpeed(Vec3 velocity) {
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }
}
