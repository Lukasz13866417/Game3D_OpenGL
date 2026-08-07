package com.example.game3d.simulator;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.simulation.JumpDecision;
import com.example.game3d.core.simulation.JumpRuleId;
import com.example.game3d.core.simulation.MotionSegment;
import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.simulation.PlayerSnapshot;
import com.example.game3d.core.simulation.ContactSnapshot;
import com.example.game3d.core.simulation.SimulationEngine;
import com.example.game3d.core.simulation.SimulationEvent;
import com.example.game3d.core.simulation.StepObserver;
import com.example.game3d.core.simulation.StepRecord;
import com.example.game3d.core.simulation.StepResult;
import com.example.game3d.core.terrain.SurfaceMaterial;
import com.example.game3d.core.terrain.TerrainCommit;
import com.example.game3d.core.terrain.TerrainTriangle;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end assertions for the named desktop scenarios. These intentionally run the complete
 * scenario rather than testing only the first few ticks.
 */
public class ScenarioOutcomeTest {
    private static final double POSITION_EPSILON = 1.0e-9;
    private final PhysicsConfig config = new PhysicsConfig();
    private final ScenarioRegistry registry = new ScenarioRegistry();

    @Test
    public void everyBuiltInScenarioReplaysDeterministicallyForItsFullRun() {
        for (Scenario scenario : registry.all()) {
            SimulationEngine left = engine(scenario);
            SimulationEngine right = engine(scenario);
            for (int tick = 0; tick < scenario.defaultTicks; tick++) {
                applyScheduledCommits(left, scenario, tick);
                applyScheduledCommits(right, scenario, tick);
                StepResult leftResult = left.step(scenario.inputForTick(tick));
                StepResult rightResult = right.step(scenario.inputForTick(tick));

                assertSnapshotEquals(scenario.name + " tick " + tick,
                        leftResult.snapshot, rightResult.snapshot);
                assertDecisionEquals(scenario.name + " tick " + tick,
                        leftResult.jumpDecision, rightResult.jumpDecision);
                assertEventsEqual(scenario.name + " tick " + tick,
                        leftResult.events, rightResult.events);

                assertFalse("invariant failure in " + scenario.name + " tick " + tick,
                        hasEvent(leftResult, SimulationEvent.Type.INVARIANT_FAILURE));
                if (leftResult.snapshot.landingJumpArmed) {
                    assertFalse("landing buffer armed while grounded in " + scenario.name,
                            leftResult.snapshot.grounded);
                    assertTrue("landing buffer armed without downward motion in "
                                    + scenario.name + " tick " + tick,
                            leftResult.snapshot.velocity.y < 0.0);
                    assertFalse("landing buffer armed together with impact brake in "
                                    + scenario.name,
                            leftResult.snapshot.impactBrakeArmed);
                    assertTrue("landing buffer armed without visible jump charge in "
                                    + scenario.name,
                            leftResult.snapshot.gestureCharge
                                    >= config.jumpChargeThreshold);
                }
                if (leftResult.snapshot.dead || rightResult.snapshot.dead) {
                    assertEquals(leftResult.snapshot.dead, rightResult.snapshot.dead);
                    break;
                }
            }
        }
    }

    @Test
    public void flatRestSettlesAtRadiusAndCruisesWithoutLeavingTheTrack() {
        RunSummary run = run("flat_rest");

        assertCompletedAlive(run);
        assertTrue(run.finalState.grounded);
        assertEquals(config.cylinderRadius, run.finalState.absolutePosition.y, 0.002);
        assertEquals(0.0, run.finalState.absolutePosition.x, POSITION_EPSILON);
        assertEquals(-config.cruisingSpeed, run.finalState.velocity.z, 0.1);
        assertEquals(0, run.count(SimulationEvent.Type.JUMP));
        assertEquals(0, run.count(SimulationEvent.Type.BOUNCE));
        assertEquals(0, run.count(SimulationEvent.Type.PLAYER_DIED));
        for (Frame frame : run.frames) {
            assertTrue("flat motor path moved backward",
                    frame.result.snapshot.absolutePosition.z
                            <= frame.before.absolutePosition.z + POSITION_EPSILON);
            assertTrue("flat motor path lost support at tick "
                    + frame.result.snapshot.tick, frame.result.snapshot.grounded);
        }
    }

    @Test
    public void groundJumpTakesOffAtTheEventMarkerAndReturnsToSupport() {
        RunSummary run = run("ground_jump");
        EventOccurrence jump = run.only(SimulationEvent.Type.JUMP);

        assertCompletedAlive(run);
        assertTrue(run.finalState.grounded);
        assertEquals(JumpRuleId.GROUNDED_RELEASED, jump.decision.rule);
        assertEquals(JumpDecision.Action.JUMP_NOW, jump.decision.action);
        assertEquals(0.0, jump.event.tickFraction, 0.0);
        assertEquals(jump.before.timeNanos, jump.event.timeNanos);
        assertVecEquals(jump.before.absolutePosition, jump.event.position, POSITION_EPSILON);
        assertTrue(jump.after.absolutePosition.y > jump.event.position.y);
        assertTrue(jump.after.velocity.y > 0.0);
        assertTrue("jump apex was never reached", run.maxY > 3.45);
        assertTrue("jump exceeded its intended envelope", run.maxY < 3.75);
        assertTrue(run.count(SimulationEvent.Type.LAND) >= 2);
        assertEquals(0, run.count(SimulationEvent.Type.PLAYER_DIED));
    }

    @Test
    public void jumpChargeRequiresBothPhysicalHorizontalPathGuards() {
        RunSummary accepted = run("jump_charge_x_boundary_accept");
        EventOccurrence jump = accepted.only(SimulationEvent.Type.JUMP);

        assertCompletedAlive(accepted);
        assertEquals(JumpRuleId.GROUNDED_RELEASED, jump.decision.rule);
        assertEquals(JumpDecision.Action.JUMP_NOW, jump.decision.action);
        assertTrue(jump.after.velocity.y > 0.0);

        RunSummary ratioRejected = run("jump_charge_x_ratio_reject");
        assertCompletedAlive(ratioRejected);
        assertEquals(0, ratioRejected.count(SimulationEvent.Type.JUMP));
        assertEquals(0.0, ratioRejected.finalState.gestureCharge, 0.0);
        assertTrue(ratioRejected.finalState.grounded);

        RunSummary absoluteRejected = run("jump_charge_x_absolute_reject");
        assertCompletedAlive(absoluteRejected);
        assertEquals(0, absoluteRejected.count(SimulationEvent.Type.JUMP));
        assertEquals(0.0, absoluteRejected.finalState.gestureCharge, 0.0);
        assertTrue(absoluteRejected.finalState.grounded);
    }

    @Test
    public void groundJumpBounceUsesResolvedToiInsteadOfPenetratedProbeEndpoint() {
        Scenario scenario = registry.require("ground_jump");
        final List<StepRecord> records = new ArrayList<StepRecord>();
        SimulationEngine engine = new SimulationEngine(
                scenario.terrain, config, scenario.initialPosition,
                scenario.initialVelocity, scenario.initialAirJumpCharges,
                new StepObserver() {
                    @Override
                    public void onStep(StepRecord record) {
                        records.add(record);
                    }
                });
        StepRecord bounceRecord = null;
        SimulationEvent bounce = null;
        for (int tick = 0; tick < scenario.defaultTicks && bounce == null; tick++) {
            engine.step(scenario.inputForTick(tick));
            StepRecord latest = records.get(records.size() - 1);
            for (SimulationEvent event : latest.events) {
                if (event.type == SimulationEvent.Type.BOUNCE) {
                    bounceRecord = latest;
                    bounce = event;
                    break;
                }
            }
        }

        assertNotNull("ground jump never reached its configured hard-impact bounce", bounce);
        assertNotNull(bounceRecord);
        assertEquals(config.cylinderRadius, bounce.position.y, 1.0e-6);
        assertTrue(bounceRecord.after.velocity.y > 0.0);
        PlayerSnapshot firstPostBounce = engine.step(
                scenario.inputForTick((int) bounceRecord.after.tick)).snapshot;
        assertTrue("resolved trajectory did not rise after the bounce TOI",
                firstPostBounce.absolutePosition.y > bounce.position.y);

        ContactSnapshot impact = null;
        for (ContactSnapshot contact : bounceRecord.contacts) {
            if (contact.triangleId == bounce.subjectId
                    && Math.abs(contact.tickFraction - bounce.tickFraction) < 1.0e-10
                    && contact.normalImpulse > 0.0) {
                impact = contact;
                break;
            }
        }
        assertNotNull("bounce has no matching swept contact diagnostic", impact);
        assertEquals(ContactSnapshot.TimingQuality.SWEPT_TOI, impact.timingQuality);
        assertVecEquals(bounce.position, impact.resolvedCenter, 1.0e-9);
        assertTrue("descending attempted endpoint should be at or below the resolved impact",
                impact.detectedCenter.y <= impact.resolvedCenter.y + 1.0e-9);
        assertTrue(impact.signedSeparation <= config.toiTolerance * 1.1);
        assertTrue(impact.preVelocity.dot(impact.normal) < 0.0);
        assertTrue(impact.postVelocity.dot(impact.normal) > 0.0);

        boolean eventIsMotionBoundary = false;
        for (MotionSegment segment : bounceRecord.motionSegments) {
            assertTrue("resolved path dipped below its bounce TOI",
                    segment.startPosition.y >= bounce.position.y - 1.0e-8);
            assertTrue("resolved path dipped below its bounce TOI",
                    segment.endPosition.y >= bounce.position.y - 1.0e-8);
            if (Math.abs(segment.endFraction - bounce.tickFraction) < 1.0e-10
                    && segment.endPosition.subtract(bounce.position).lengthSquared()
                    < 1.0e-18) {
                eventIsMotionBoundary = true;
            }
        }
        assertTrue("bounce marker is not anchored to the resolved path", eventIsMotionBoundary);
    }

    @Test
    public void gapAndSpikeRecoverySpendOneChargeAndSurvive() {
        assertAirRecovery("gap_recovery", JumpRuleId.AIRBORNE_UNRECOVERABLE);
        assertAirRecovery("spike_avoidance", JumpRuleId.AIRBORNE_SPIKE_FIRST);

        RunSummary spike = run("spike_avoidance");
        assertEquals(0, spike.count(SimulationEvent.Type.SPIKE_HIT));
        assertEquals("spike-first forecast must never arm a landing jump",
                0, spike.count(SimulationEvent.Type.LANDING_JUMP_ARMED));
        assertTrue("player never passed the spike", spike.finalState.absolutePosition.z < -4.0);
    }

    @Test
    public void heldDownwardSwipeBrakesImpactButEarlyReleaseBounces() {
        RunSummary held = run("down_hold_no_bounce");
        EventOccurrence braked = held.only(SimulationEvent.Type.BOUNCE_SUPPRESSED);

        assertCompletedAlive(held);
        assertEquals(0, held.count(SimulationEvent.Type.BOUNCE));
        assertEquals(0, held.count(SimulationEvent.Type.JUMP));
        assertTrue(held.count(SimulationEvent.Type.LAND) >= 1);
        assertTrue("finger was not held through the braked impact", braked.after.touchHeld);
        assertTrue(braked.after.grounded);
        assertFalse(braked.after.impactBrakeArmed);
        assertEquals(0.0, braked.after.velocity.y, 1.0e-9);

        RunSummary released = run("down_release_bounces");
        assertCompletedAlive(released);
        assertTrue(released.count(SimulationEvent.Type.BOUNCE) >= 1);
        assertEquals(0, released.count(SimulationEvent.Type.BOUNCE_SUPPRESSED));
        assertEquals(0, released.count(SimulationEvent.Type.JUMP));
    }

    @Test
    public void heldBrakeCanReverseIntoGroundJumpWithoutAnotherTouchDown() {
        RunSummary run = run("down_hold_then_charge");
        EventOccurrence braked = run.only(SimulationEvent.Type.BOUNCE_SUPPRESSED);
        EventOccurrence jump = run.only(SimulationEvent.Type.JUMP);

        assertCompletedAlive(run);
        assertTrue(braked.after.touchHeld);
        assertTrue(braked.after.grounded);
        assertEquals(JumpRuleId.GROUNDED_RELEASED, jump.decision.rule);
        assertTrue("jump occurred before the brake was absorbed",
                jump.event.timeNanos > braked.event.timeNanos);
        assertTrue("the original touch was not still held before release",
                jump.before.touchHeld);
        for (EventOccurrence occurrence : run.events) {
            if (occurrence.event.type == SimulationEvent.Type.BOUNCE) {
                assertTrue("initial braked impact unexpectedly bounced",
                        occurrence.event.timeNanos > jump.event.timeNanos);
            }
        }
    }

    @Test
    public void landingBufferArmsOnlyWhileFallingInsideTheBoundedWindow() {
        RunSummary near = run("landing_buffer_near_safe");
        EventOccurrence armed = near.only(SimulationEvent.Type.LANDING_JUMP_ARMED);
        EventOccurrence landingJump = near.only(SimulationEvent.Type.JUMP);

        assertCompletedAlive(near);
        assertTrue(armed.before.velocity.y < 0.0);
        assertEquals(JumpRuleId.LANDING_CHARGED, landingJump.decision.rule);
        assertEquals("landing jump consumed a persistent air charge",
                1, near.finalState.airJumpCharges);
        assertTrue(landingJump.event.timeNanos > armed.event.timeNanos);

        RunSummary tooEarly = run("landing_buffer_too_early");
        EventOccurrence earlyJump = tooEarly.only(SimulationEvent.Type.JUMP);
        assertCompletedAlive(tooEarly);
        assertEquals(0, tooEarly.count(SimulationEvent.Type.LANDING_JUMP_ARMED));
        assertEquals(JumpRuleId.AIRBORNE_UNRECOVERABLE, earlyJump.decision.rule);
        assertTrue(earlyJump.decision.consumesAirCharge);
        assertEquals(0, tooEarly.finalState.airJumpCharges);

        RunSummary rising = run("landing_buffer_rising");
        EventOccurrence risingJump = rising.only(SimulationEvent.Type.JUMP);
        assertCompletedAlive(rising);
        assertEquals(0, rising.count(SimulationEvent.Type.LANDING_JUMP_ARMED));
        assertEquals(JumpRuleId.AIRBORNE_RELEASED, risingJump.decision.rule);
        assertTrue(risingJump.before.velocity.y > 0.0);
        assertEquals(0, rising.finalState.airJumpCharges);

        RunSummary rebound = run("landing_buffer_rising_bounce");
        EventOccurrence bounce = null;
        for (EventOccurrence occurrence : rebound.events) {
            if (occurrence.event.type == SimulationEvent.Type.BOUNCE) {
                bounce = occurrence;
                break;
            }
        }
        assertNotNull("rebound scenario never bounced", bounce);
        EventOccurrence reboundJump = rebound.only(SimulationEvent.Type.JUMP);
        assertCompletedAlive(rebound);
        assertEquals(0, rebound.count(SimulationEvent.Type.LANDING_JUMP_ARMED));
        assertTrue("air jump did not occur after the rebound",
                reboundJump.event.timeNanos > bounce.event.timeNanos);
        assertTrue("rebound release was not evaluated during ascent",
                reboundJump.before.velocity.y > 0.0);
        assertEquals(JumpRuleId.AIRBORNE_RELEASED, reboundJump.decision.rule);
        assertTrue(reboundJump.decision.consumesAirCharge);
        assertEquals(0, rebound.finalState.airJumpCharges);

        RunSummary ramp = run("landing_buffer_rising_ramp");
        assertCompletedAlive(ramp);
        assertEquals(0, ramp.count(SimulationEvent.Type.LANDING_JUMP_ARMED));
        assertEquals(0, ramp.count(SimulationEvent.Type.JUMP));
        assertEquals(0, ramp.count(SimulationEvent.Type.BOUNCE));
        assertTrue("ramp takeoff never returned to safe support",
                ramp.count(SimulationEvent.Type.LAND) >= 2);
        int risingNoChargeRejects = 0;
        boolean sawRampSupport = false;
        boolean leftRampBeforeReject = false;
        for (Frame frame : ramp.frames) {
            if (frame.before.grounded) {
                sawRampSupport = true;
            } else if (sawRampSupport) {
                leftRampBeforeReject = true;
            }
            if (frame.result.jumpDecision.rule
                    == JumpRuleId.AIRBORNE_NO_CHARGE_REJECT) {
                risingNoChargeRejects++;
                assertTrue("ramp release was not evaluated during ascent",
                        frame.before.velocity.y > 0.0);
                assertTrue("ramp release was rejected before takeoff",
                        leftRampBeforeReject);
                assertEquals(0.0, frame.result.snapshot.gestureCharge, 0.0);
            }
        }
        assertEquals(1, risingNoChargeRejects);
    }

    @Test
    public void featherCollectionIsPersistentAndEmittedExactlyOnce() {
        RunSummary run = run("feather_collection");
        EventOccurrence collection = run.only(SimulationEvent.Type.FEATHER_COLLECTED);

        assertCompletedAlive(run);
        assertTrue(collection.event.subjectId >= 0L);
        assertEquals(0, collection.before.airJumpCharges);
        assertEquals(1, collection.after.airJumpCharges);
        assertEquals(1.0, collection.event.tickFraction, 0.0);
        assertEquals(collection.after.timeNanos, collection.event.timeNanos);
        assertVecEquals(collection.after.absolutePosition,
                collection.event.position, POSITION_EPSILON);
        assertEquals(1, run.finalState.airJumpCharges);
        assertEquals(0, run.count(SimulationEvent.Type.JUMP));
        assertEquals(0, run.count(SimulationEvent.Type.PLAYER_DIED));
    }

    @Test
    public void airborneRedirectWaitsForFirstGroundContactThenSnaps() {
        Scenario scenario = registry.require("airborne_redirect");
        SimulationEngine engine = engine(scenario);
        boolean jumped = false;
        boolean redirectedContact = false;
        boolean redirectedLanding = false;

        for (int tick = 0; tick < scenario.defaultTicks; tick++) {
            StepResult result = engine.step(scenario.inputForTick(tick));
            if (hasEvent(result, SimulationEvent.Type.JUMP)) {
                jumped = true;
            }
            boolean bounced = jumped
                    && hasEvent(result, SimulationEvent.Type.BOUNCE);
            boolean landed = jumped && hasEvent(result, SimulationEvent.Type.LAND);
            if (jumped && !redirectedContact && !bounced && !landed) {
                assertEquals("airborne yaw steered translational velocity at tick " + tick,
                        0.0, result.snapshot.velocity.x, 1.0e-9);
            }
            if (bounced || landed) {
                Vec3 movement = result.snapshot.velocity.horizontalNormalized();
                assertEquals(result.snapshot.heading.x, movement.x, 1.0e-9);
                assertEquals(result.snapshot.heading.z, movement.z, 1.0e-9);
                assertTrue("ground-contact redirect did not face laterally",
                        Math.abs(result.snapshot.velocity.x) > 1.0);
                redirectedContact = true;
            }
            if (landed) {
                redirectedLanding = true;
                break;
            }
        }

        assertTrue("scenario never jumped", jumped);
        assertTrue("scenario never redirected at ground contact", redirectedContact);
        assertTrue("scenario never performed its redirected landing", redirectedLanding);

        RunSummary fullRun = run("airborne_redirect");
        assertCompletedAlive(fullRun);
        assertTrue(fullRun.finalState.absolutePosition.x > 1.0);
    }

    @Test
    public void slopeBoostCrossesTheElevationChangeAndExceedsCruisingSpeed() {
        RunSummary run = run("slope_boost");

        assertCompletedAlive(run);
        assertTrue(run.finalState.grounded);
        assertEquals(2.0 + config.cylinderRadius,
                run.finalState.absolutePosition.y, 0.005);
        assertTrue("boost never accelerated beyond normal cruise",
                run.maxHorizontalSpeed > config.cruisingSpeed * 1.10);
        assertTrue("scenario did not cross the constructed slope/boost section",
                run.finalState.absolutePosition.z < -70.0);
        assertEquals("connected walkable slope seams must transfer support without bouncing",
                0, run.count(SimulationEvent.Type.BOUNCE));
        assertTrue("boost material was never contacted while supported",
                hasGroundedBoostContact(run.scenario));
        assertEquals(0, run.count(SimulationEvent.Type.JUMP));
        assertEquals(0, run.count(SimulationEvent.Type.PLAYER_DIED));
    }

    @Test
    public void openLiftHasNoHiddenRiserAndFallsBeneathTheElevatedTop() {
        RunSummary run = run("open_lift");
        int landsAfterLeavingInitialSupport = 0;
        boolean leftInitialSupport = false;
        double lastSupportedY = Double.NaN;
        for (Frame frame : run.frames) {
            if (frame.result.snapshot.grounded) {
                lastSupportedY = frame.result.snapshot.absolutePosition.y;
            }
            if (!frame.result.snapshot.grounded) {
                leftInitialSupport = true;
            }
            if (leftInitialSupport && hasEvent(frame.result, SimulationEvent.Type.LAND)) {
                landsAfterLeavingInitialSupport++;
            }
        }

        double expectedDeathFloor = lastSupportedY + config.deathY;
        assertTrue(run.finalState.dead);
        assertTrue(run.finalState.absolutePosition.y < expectedDeathFloor);
        assertTrue("terminal point should be beneath the elevated platform, not in the gap",
                run.finalState.absolutePosition.z < -13.0);
        assertEquals(1, run.count(SimulationEvent.Type.PLAYER_DIED));
        assertEquals(0, run.count(SimulationEvent.Type.JUMP));
        assertEquals(0, run.count(SimulationEvent.Type.BOUNCE));
        assertEquals("an implicit riser or support caught the player in the open lift",
                0, landsAfterLeavingInitialSupport);
        EventOccurrence death = run.only(SimulationEvent.Type.PLAYER_DIED);
        assertTrue(death.before.absolutePosition.y >= expectedDeathFloor);
        assertTrue(death.after.absolutePosition.y < expectedDeathFloor);
        assertEquals(1.0, death.event.tickFraction, 0.0);
        assertEquals(death.after.timeNanos, death.event.timeNanos);
        assertVecEquals(death.after.absolutePosition,
                death.event.position, POSITION_EPSILON);
    }

    @Test
    public void streamingCommitIsAppliedBeforeCrossingTheInitialFrontier() {
        Scenario scenario = registry.require("streaming_commit");
        SimulationEngine engine = engine(scenario);
        boolean supportedByStreamedSegment = false;

        assertEquals(0L, engine.terrainRevision());
        for (int tick = 0; tick < scenario.defaultTicks; tick++) {
            applyScheduledCommits(engine, scenario, tick);
            StepResult result = engine.step(scenario.inputForTick(tick));
            if (result.snapshot.supportSegmentId == 1L) {
                supportedByStreamedSegment = true;
            }
            assertFalse("streaming track lost the player at tick " + tick,
                    result.snapshot.dead);
        }

        assertEquals(1L, engine.terrainRevision());
        assertTrue("player never crossed onto the committed extension",
                supportedByStreamedSegment);
        assertTrue(engine.snapshot().grounded);
    }

    @Test
    public void productionGeneratorStreamSupportsACompleteGameplayRun() {
        Scenario scenario = registry.require("generated_gameplay_stream");
        SimulationEngine engine = engine(scenario);
        boolean reachedStreamedTerrain = false;

        for (int tick = 0; tick < scenario.defaultTicks; tick++) {
            applyScheduledCommits(engine, scenario, tick);
            StepResult result = engine.step(scenario.inputForTick(tick));
            reachedStreamedTerrain |= result.snapshot.supportSegmentId >= 48L;
            assertFalse("generated gameplay terrain lost the player at tick " + tick,
                    result.snapshot.dead);
        }

        assertEquals(2L, engine.terrainRevision());
        assertTrue("player never reached a segment emitted by the live commit",
                reachedStreamedTerrain);
        assertTrue(engine.snapshot().grounded);
        assertEquals(0, engine.snapshot().airJumpCharges);
    }

    private boolean hasGroundedBoostContact(Scenario scenario) {
        final Set<Long> boostTriangleIds = new HashSet<Long>();
        for (TerrainTriangle triangle : scenario.terrain.triangles()) {
            if (triangle.material == SurfaceMaterial.BOOST) {
                boostTriangleIds.add(triangle.id);
            }
        }
        final boolean[] found = {false};
        SimulationEngine engine = new SimulationEngine(
                scenario.terrain, config, scenario.initialPosition,
                scenario.initialVelocity, scenario.initialAirJumpCharges,
                new StepObserver() {
                    @Override
                    public void onStep(StepRecord record) {
                        if (!record.after.grounded) {
                            return;
                        }
                        for (ContactSnapshot contact : record.contacts) {
                            if (boostTriangleIds.contains(contact.triangleId)) {
                                found[0] = true;
                            }
                        }
                    }
                });
        for (int tick = 0; tick < scenario.defaultTicks && !engine.snapshot().dead; tick++) {
            applyScheduledCommits(engine, scenario, tick);
            engine.step(scenario.inputForTick(tick));
        }
        return found[0];
    }

    private void assertAirRecovery(String scenarioName, JumpRuleId expectedRule) {
        RunSummary run = run(scenarioName);
        EventOccurrence jump = run.only(SimulationEvent.Type.JUMP);

        assertCompletedAlive(run);
        assertEquals(expectedRule, jump.decision.rule);
        assertTrue(jump.decision.consumesAirCharge);
        assertEquals(0, run.finalState.airJumpCharges);
        assertTrue(run.count(SimulationEvent.Type.LAND) >= 1);
        assertEquals(0, run.count(SimulationEvent.Type.PLAYER_DIED));
    }

    private void assertCompletedAlive(RunSummary run) {
        assertEquals(run.scenario.defaultTicks, run.finalState.tick);
        assertFalse(run.finalState.dead);
    }

    private RunSummary run(String scenarioName) {
        Scenario scenario = registry.require(scenarioName);
        SimulationEngine engine = engine(scenario);
        RunSummary summary = new RunSummary(scenario);
        for (int tick = 0; tick < scenario.defaultTicks; tick++) {
            PlayerSnapshot before = engine.snapshot();
            applyScheduledCommits(engine, scenario, tick);
            StepResult result = engine.step(scenario.inputForTick(tick));
            summary.add(before, result);
            if (result.snapshot.dead) {
                break;
            }
        }
        summary.finalState = engine.snapshot();
        return summary;
    }

    private SimulationEngine engine(Scenario scenario) {
        if (scenario.usesCanonicalTerrain()) {
            return new SimulationEngine(
                    scenario.terrainSnapshot, config,
                    scenario.initialPosition, scenario.initialVelocity,
                    scenario.initialAngularVelocity,
                    scenario.initialAirJumpCharges, StepObserver.NONE);
        }
        return new SimulationEngine(
                scenario.terrain, config,
                scenario.initialPosition, scenario.initialVelocity,
                scenario.initialAngularVelocity,
                scenario.initialAirJumpCharges, StepObserver.NONE);
    }

    private static void applyScheduledCommits(
            SimulationEngine engine, Scenario scenario, long tick) {
        for (TerrainCommit commit : scenario.commitsForTick(tick)) {
            engine.applyTerrainCommit(commit);
        }
    }

    private static void assertSnapshotEquals(
            String message, PlayerSnapshot left, PlayerSnapshot right) {
        assertEquals(message, left.stateHash, right.stateHash);
        assertEquals(message, left.tick, right.tick);
        assertEquals(message, left.timeNanos, right.timeNanos);
        assertVecEquals(left.absolutePosition, right.absolutePosition, 0.0);
        assertVecEquals(left.position, right.position, 0.0);
        assertVecEquals(left.velocity, right.velocity, 0.0);
        assertVecEquals(left.heading, right.heading, 0.0);
        assertVecEquals(left.cylinderAxis, right.cylinderAxis, 0.0);
        assertEquals(message, left.yawRadians, right.yawRadians, 0.0);
        assertEquals(message, left.axleRadians, right.axleRadians, 0.0);
        assertEquals(message, left.angularVelocity, right.angularVelocity, 0.0);
        assertEquals(message, left.gestureCharge, right.gestureCharge, 0.0);
        assertEquals(message, left.gestureChargePotential,
                right.gestureChargePotential, 0.0);
        assertEquals(message, left.gestureRawDeltaX,
                right.gestureRawDeltaX, 0.0);
        assertEquals(message, left.gestureRawUpwardDistance,
                right.gestureRawUpwardDistance, 0.0);
        assertEquals(message, left.gestureMaxAbsRawDeltaX,
                right.gestureMaxAbsRawDeltaX, 0.0);
        assertEquals(message, left.jumpChargePathEligible,
                right.jumpChargePathEligible);
        assertEquals(message, left.airJumpCharges, right.airJumpCharges);
        assertEquals(message, left.grounded, right.grounded);
        assertEquals(message, left.touchHeld, right.touchHeld);
        assertEquals(message, left.landingJumpArmed, right.landingJumpArmed);
        assertEquals(message, left.impactBrakeArmed, right.impactBrakeArmed);
        assertEquals(message, left.dead, right.dead);
    }

    private static void assertDecisionEquals(
            String message, JumpDecision left, JumpDecision right) {
        assertEquals(message, left.rule, right.rule);
        assertEquals(message, left.action, right.action);
        assertEquals(message, left.consumesAirCharge, right.consumesAirCharge);
        assertEquals(message, left.reason, right.reason);
    }

    private static void assertEventsEqual(
            String message, List<SimulationEvent> left, List<SimulationEvent> right) {
        assertEquals(message, left.size(), right.size());
        for (int i = 0; i < left.size(); i++) {
            SimulationEvent leftEvent = left.get(i);
            SimulationEvent rightEvent = right.get(i);
            assertEquals(message, leftEvent.type, rightEvent.type);
            assertEquals(message, leftEvent.subjectId, rightEvent.subjectId);
            assertEquals(message, leftEvent.detail, rightEvent.detail);
            assertEquals(message, leftEvent.timeNanos, rightEvent.timeNanos);
            if (leftEvent.position == null || rightEvent.position == null) {
                assertEquals(message, leftEvent.position, rightEvent.position);
            } else {
                assertVecEquals(leftEvent.position, rightEvent.position, 0.0);
            }
            if (Double.isNaN(leftEvent.tickFraction)
                    || Double.isNaN(rightEvent.tickFraction)) {
                assertTrue(message, Double.isNaN(leftEvent.tickFraction)
                        && Double.isNaN(rightEvent.tickFraction));
            } else {
                assertEquals(message, leftEvent.tickFraction,
                        rightEvent.tickFraction, 0.0);
            }
        }
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual, double epsilon) {
        assertNotNull(actual);
        assertEquals(expected.x, actual.x, epsilon);
        assertEquals(expected.y, actual.y, epsilon);
        assertEquals(expected.z, actual.z, epsilon);
    }

    private static boolean hasEvent(StepResult result, SimulationEvent.Type type) {
        for (SimulationEvent event : result.events) {
            if (event.type == type) {
                return true;
            }
        }
        return false;
    }

    private static final class Frame {
        final PlayerSnapshot before;
        final StepResult result;

        Frame(PlayerSnapshot before, StepResult result) {
            this.before = before;
            this.result = result;
        }
    }

    private static final class EventOccurrence {
        final PlayerSnapshot before;
        final PlayerSnapshot after;
        final JumpDecision decision;
        final SimulationEvent event;

        EventOccurrence(PlayerSnapshot before, PlayerSnapshot after,
                        JumpDecision decision, SimulationEvent event) {
            this.before = before;
            this.after = after;
            this.decision = decision;
            this.event = event;
        }
    }

    private static final class RunSummary {
        final Scenario scenario;
        final List<Frame> frames = new ArrayList<Frame>();
        final List<EventOccurrence> events = new ArrayList<EventOccurrence>();
        PlayerSnapshot finalState;
        double maxY = -Double.MAX_VALUE;
        double maxHorizontalSpeed;

        RunSummary(Scenario scenario) {
            this.scenario = scenario;
        }

        void add(PlayerSnapshot before, StepResult result) {
            frames.add(new Frame(before, result));
            maxY = Math.max(maxY, result.snapshot.absolutePosition.y);
            maxHorizontalSpeed = Math.max(maxHorizontalSpeed,
                    result.snapshot.velocity.withY(0.0).length());
            for (SimulationEvent event : result.events) {
                events.add(new EventOccurrence(before, result.snapshot,
                        result.jumpDecision, event));
            }
        }

        int count(SimulationEvent.Type type) {
            int count = 0;
            for (EventOccurrence occurrence : events) {
                if (occurrence.event.type == type) {
                    count++;
                }
            }
            return count;
        }

        EventOccurrence only(SimulationEvent.Type type) {
            EventOccurrence found = null;
            for (EventOccurrence occurrence : events) {
                if (occurrence.event.type != type) {
                    continue;
                }
                assertTrue("multiple " + type + " events in " + scenario.name,
                        found == null);
                found = occurrence;
            }
            assertNotNull("missing " + type + " event in " + scenario.name, found);
            return found;
        }
    }
}
