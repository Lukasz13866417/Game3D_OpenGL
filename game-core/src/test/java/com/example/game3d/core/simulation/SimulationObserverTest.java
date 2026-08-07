package com.example.game3d.core.simulation;

import com.example.game3d.core.input.FixedStepInput;
import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainPatch;
import com.example.game3d.core.terrain.TerrainWorld;
import com.example.game3d.core.terrain.TrackBuilder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SimulationObserverTest {
    @Test
    public void observerReceivesConsistentBeforeAfterAndContactFacts() {
        final List<StepRecord> records = new ArrayList<StepRecord>();
        PhysicsConfig config = new PhysicsConfig();
        TerrainWorld terrain = new TrackBuilder(8.0).straight(20.0).build();
        SimulationEngine engine = new SimulationEngine(terrain, config,
                new Vec3(0.0, config.cylinderRadius + 0.001, 1.0),
                0, new StepObserver() {
                    @Override
                    public void onStep(StepRecord record) {
                        records.add(record);
                    }
                });

        engine.step(FixedStepInput.EMPTY);

        assertEquals(1, records.size());
        StepRecord record = records.get(0);
        assertEquals(0L, record.before.tick);
        assertEquals(1L, record.after.tick);
        assertFalse(record.queriedTriangles.isEmpty());
        assertFalse(record.contacts.isEmpty());
        assertFalse(record.motionSegments.isEmpty());
        assertFalse(record.spinSegments.isEmpty());
        MotionSegment firstMotion = record.motionSegments.get(0);
        MotionSegment lastMotion =
                record.motionSegments.get(record.motionSegments.size() - 1);
        assertEquals(0.0, firstMotion.startFraction, 0.0);
        assertEquals(1.0, lastMotion.endFraction, 0.0);
        assertEquals(record.before.absolutePosition.x, firstMotion.startPosition.x, 0.0);
        assertEquals(record.before.absolutePosition.y, firstMotion.startPosition.y, 0.0);
        assertEquals(record.before.absolutePosition.z, firstMotion.startPosition.z, 0.0);
        assertEquals(record.after.absolutePosition.x, lastMotion.endPosition.x, 0.0);
        assertEquals(record.after.absolutePosition.y, lastMotion.endPosition.y, 0.0);
        assertEquals(record.after.absolutePosition.z, lastMotion.endPosition.z, 0.0);
        assertEquals(engine.snapshot().stateHash, record.after.stateHash);
    }

    @Test
    public void enablingObserverCannotChangeSimulationResult() {
        PhysicsConfig config = new PhysicsConfig();
        TerrainWorld terrain = new TrackBuilder(8.0).straight(200.0).build();
        Vec3 start = new Vec3(0.0, 2.0, 1.0);
        SimulationEngine silent = new SimulationEngine(
                terrain, config, start, 1, StepObserver.NONE);
        final StepRecord[] latestRecord = new StepRecord[1];
        SimulationEngine observed = new SimulationEngine(
                terrain, config, start, 1, new StepObserver() {
                    @Override
                    public void onStep(StepRecord record) {
                        // Intentionally inspect all lists to exercise trace construction.
                        record.contacts.size();
                        record.motionSegments.size();
                        record.events.size();
                        record.jumpEvaluations.size();
                        latestRecord[0] = record;
                    }
                });

        boolean sawJump = false;
        boolean sawLanding = false;
        for (int i = 0; i < 240; i++) {
            FixedStepInput input;
            if (i == 0) {
                input = new FixedStepInput(Arrays.asList(
                        PlayerInputEvent.down(0L, 1L),
                        PlayerInputEvent.swipe(0L, 2L, 0.0, -0.30),
                        PlayerInputEvent.up(0L, 3L)));
            } else {
                input = FixedStepInput.EMPTY;
            }
            StepResult silentResult = silent.step(input);
            StepResult observedResult = observed.step(input);
            assertStepResultEquals(silentResult, observedResult);
            for (SimulationEvent event : silentResult.events) {
                sawJump |= event.type == SimulationEvent.Type.JUMP;
                sawLanding |= event.type == SimulationEvent.Type.LAND;
            }
        }
        assertTrue("scenario must exercise a jump event", sawJump);
        assertTrue("scenario must exercise a later landing event", sawLanding);
        assertNotNull(latestRecord[0]);
        assertFalse(latestRecord[0].motionSegments.isEmpty());
    }

    @Test
    public void cachedSafeSupportForecastMatchesForcedRecomputationEveryTick() {
        PhysicsConfig config = new PhysicsConfig();
        TerrainWorld terrain = new TrackBuilder(8.0).straight(200.0).build();
        Vec3 start = new Vec3(0.0, 1.4, 1.0);
        final StepRecord[] cachedRecord = new StepRecord[1];
        final StepRecord[] recomputedRecord = new StepRecord[1];
        SimulationEngine cached = new SimulationEngine(
                terrain, config, start, new Vec3(0.0, -1.0, 0.0),
                1, new StepObserver() {
                    @Override
                    public void onStep(StepRecord record) {
                        cachedRecord[0] = record;
                    }
                });
        SimulationEngine recomputed = new SimulationEngine(
                terrain, config, start, new Vec3(0.0, -1.0, 0.0),
                1, new StepObserver() {
                    @Override
                    public void onStep(StepRecord record) {
                        recomputedRecord[0] = record;
                    }
                });
        int safeDeferrals = 0;

        for (int i = 0; i < 180; i++) {
            FixedStepInput input;
            if (i == 0) {
                input = new FixedStepInput(Arrays.asList(
                        PlayerInputEvent.down(0L, 1L),
                        PlayerInputEvent.swipe(0L, 2L, 0.0, -0.30),
                        PlayerInputEvent.up(0L, 3L)));
            } else {
                // Setting even the same target conservatively invalidates the reference cache.
                recomputed.setCruisingSpeed(recomputed.cruisingSpeed());
                if (i == 8) {
                    input = new FixedStepInput(Collections.singletonList(
                            PlayerInputEvent.swipe(
                                    cached.snapshot().timeNanos, 4L, 0.10, 0.0)));
                } else {
                    input = FixedStepInput.EMPTY;
                }
                if (i == 12) {
                    cached.setCruisingSpeed(28.0);
                    recomputed.setCruisingSpeed(28.0);
                }
            }

            StepResult cachedResult = cached.step(input);
            StepResult recomputedResult = recomputed.step(input);
            assertStepResultEquals(recomputedResult, cachedResult);
            assertStepRecordEquals(recomputedRecord[0], cachedRecord[0]);
            if (cachedResult.jumpDecision.rule
                    == JumpRuleId.AIRBORNE_SAFE_SUPPORT_DEFER) {
                safeDeferrals++;
            }
        }

        assertTrue("scenario must offer multiple opportunities to reuse the safe suffix",
                safeDeferrals > 5);
    }

    @Test
    public void terrainReplacementInvalidatesSafeSupportForecast() {
        PhysicsConfig config = new PhysicsConfig();
        SimulationEngine engine = new SimulationEngine(
                new TrackBuilder(8.0).straight(200.0).build(), config,
                new Vec3(0.0, 1.4, 1.0), new Vec3(0.0, -1.0, 0.0),
                1, StepObserver.NONE);
        StepResult initiallySafe = engine.step(new FixedStepInput(Arrays.asList(
                PlayerInputEvent.down(0L, 1L),
                PlayerInputEvent.swipe(0L, 2L, 0.0, -0.30),
                PlayerInputEvent.up(0L, 3L))));
        assertEquals(JumpRuleId.AIRBORNE_SAFE_SUPPORT_DEFER,
                initiallySafe.jumpDecision.rule);

        engine.replaceTerrain(new TerrainWorld(
                Collections.<TerrainPatch>emptyList()));
        StepResult noLongerSafe = engine.step(FixedStepInput.EMPTY);

        assertEquals(JumpRuleId.AIRBORNE_UNRECOVERABLE,
                noLongerSafe.jumpDecision.rule);
        assertEquals(JumpDecision.Action.JUMP_NOW,
                noLongerSafe.jumpDecision.action);
        assertTrue(hasEvent(noLongerSafe, SimulationEvent.Type.JUMP));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void observerRecordListsAreImmutable() {
        final StepRecord[] captured = new StepRecord[1];
        PhysicsConfig config = new PhysicsConfig();
        SimulationEngine engine = new SimulationEngine(
                new TrackBuilder(8.0).straight(20.0).build(), config,
                new Vec3(0.0, config.cylinderRadius, 1.0),
                0, new StepObserver() {
                    @Override
                    public void onStep(StepRecord record) {
                        captured[0] = record;
                    }
                });
        engine.step(FixedStepInput.EMPTY);

        assertTrue(captured[0] != null);
        captured[0].events.add(new SimulationEvent(
                SimulationEvent.Type.LAND, -1L, "mutation"));
    }

    private static void assertStepResultEquals(StepResult expected, StepResult actual) {
        assertEquals(expected.snapshot.stateHash, actual.snapshot.stateHash);
        assertEquals(expected.jumpDecision.rule, actual.jumpDecision.rule);
        assertEquals(expected.jumpDecision.action, actual.jumpDecision.action);
        assertEquals(expected.jumpDecision.consumesAirCharge,
                actual.jumpDecision.consumesAirCharge);
        assertEquals(expected.jumpDecision.reason, actual.jumpDecision.reason);
        assertEquals(expected.events.size(), actual.events.size());
        for (int i = 0; i < expected.events.size(); i++) {
            SimulationEvent expectedEvent = expected.events.get(i);
            SimulationEvent actualEvent = actual.events.get(i);
            assertEquals(expectedEvent.type, actualEvent.type);
            assertEquals(expectedEvent.subjectId, actualEvent.subjectId);
            assertEquals(expectedEvent.detail, actualEvent.detail);
            assertEquals(expectedEvent.timeNanos, actualEvent.timeNanos);
            assertEquals(Double.doubleToLongBits(expectedEvent.tickFraction),
                    Double.doubleToLongBits(actualEvent.tickFraction));
            if (expectedEvent.position == null) {
                assertEquals(null, actualEvent.position);
            } else {
                assertNotNull(actualEvent.position);
                assertEquals(expectedEvent.position.x, actualEvent.position.x, 0.0);
                assertEquals(expectedEvent.position.y, actualEvent.position.y, 0.0);
                assertEquals(expectedEvent.position.z, actualEvent.position.z, 0.0);
            }
        }
    }

    private static boolean hasEvent(StepResult result, SimulationEvent.Type type) {
        for (SimulationEvent event : result.events) {
            if (event.type == type) {
                return true;
            }
        }
        return false;
    }

    private static void assertStepRecordEquals(StepRecord expected, StepRecord actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertEquals(expected.before.stateHash, actual.before.stateHash);
        assertEquals(expected.after.stateHash, actual.after.stateHash);
        assertEquals(expected.inputs.size(), actual.inputs.size());
        for (int i = 0; i < expected.inputs.size(); i++) {
            PlayerInputEvent expectedInput = expected.inputs.get(i);
            PlayerInputEvent actualInput = actual.inputs.get(i);
            assertEquals(expectedInput.timeNanos, actualInput.timeNanos);
            assertEquals(expectedInput.sequence, actualInput.sequence);
            assertEquals(expectedInput.type, actualInput.type);
            assertSameDouble(expectedInput.deltaXScreenHeights,
                    actualInput.deltaXScreenHeights);
            assertSameDouble(expectedInput.deltaYScreenHeights,
                    actualInput.deltaYScreenHeights);
            assertSameDouble(expectedInput.rawDeltaXScreenHeights,
                    actualInput.rawDeltaXScreenHeights);
            assertSameDouble(expectedInput.rawDeltaYScreenHeights,
                    actualInput.rawDeltaYScreenHeights);
        }
        assertEquals(expected.queriedTriangles.size(), actual.queriedTriangles.size());
        for (int i = 0; i < expected.queriedTriangles.size(); i++) {
            assertEquals(expected.queriedTriangles.get(i).id,
                    actual.queriedTriangles.get(i).id);
        }
        assertEquals(expected.contacts.size(), actual.contacts.size());
        for (int i = 0; i < expected.contacts.size(); i++) {
            ContactSnapshot expectedContact = expected.contacts.get(i);
            ContactSnapshot actualContact = actual.contacts.get(i);
            assertEquals(expectedContact.triangleId, actualContact.triangleId);
            assertVecEquals(expectedContact.point, actualContact.point);
            assertVecEquals(expectedContact.normal, actualContact.normal);
            assertSameDouble(expectedContact.penetration, actualContact.penetration);
            assertSameDouble(expectedContact.normalImpulse, actualContact.normalImpulse);
            assertVecEquals(expectedContact.detectedCenter, actualContact.detectedCenter);
            assertVecEquals(expectedContact.resolvedCenter, actualContact.resolvedCenter);
            assertSameDouble(expectedContact.signedSeparation,
                    actualContact.signedSeparation);
            assertSameDouble(expectedContact.tickFraction, actualContact.tickFraction);
            assertVecEquals(expectedContact.preVelocity, actualContact.preVelocity);
            assertVecEquals(expectedContact.postVelocity, actualContact.postVelocity);
            assertSameDouble(expectedContact.preAngularVelocity,
                    actualContact.preAngularVelocity);
            assertSameDouble(expectedContact.postAngularVelocity,
                    actualContact.postAngularVelocity);
            assertEquals(expectedContact.feature, actualContact.feature);
            assertEquals(expectedContact.castIterations, actualContact.castIterations);
            assertEquals(expectedContact.timingQuality, actualContact.timingQuality);
        }
        assertEquals(expected.spinSegments.size(), actual.spinSegments.size());
        for (int i = 0; i < expected.spinSegments.size(); i++) {
            SpinSegment expectedSpin = expected.spinSegments.get(i);
            SpinSegment actualSpin = actual.spinSegments.get(i);
            assertSameDouble(expectedSpin.startFraction, actualSpin.startFraction);
            assertSameDouble(expectedSpin.endFraction, actualSpin.endFraction);
            assertEquals(expectedSpin.mode, actualSpin.mode);
            assertSameDouble(expectedSpin.deltaRadians, actualSpin.deltaRadians);
            assertSameDouble(expectedSpin.startAngularVelocity,
                    actualSpin.startAngularVelocity);
            assertSameDouble(expectedSpin.endAngularVelocity,
                    actualSpin.endAngularVelocity);
            assertSameDouble(expectedSpin.signedDistance, actualSpin.signedDistance);
            assertEquals(expectedSpin.supportTriangleId,
                    actualSpin.supportTriangleId);
            assertVecEquals(expectedSpin.supportNormal, actualSpin.supportNormal);
        }
        assertEquals(expected.motionSegments.size(), actual.motionSegments.size());
        for (int i = 0; i < expected.motionSegments.size(); i++) {
            MotionSegment expectedMotion = expected.motionSegments.get(i);
            MotionSegment actualMotion = actual.motionSegments.get(i);
            assertSameDouble(expectedMotion.startFraction, actualMotion.startFraction);
            assertSameDouble(expectedMotion.endFraction, actualMotion.endFraction);
            assertVecEquals(expectedMotion.startPosition, actualMotion.startPosition);
            assertVecEquals(expectedMotion.endPosition, actualMotion.endPosition);
            assertEquals(expectedMotion.phase, actualMotion.phase);
        }
        assertEquals(expected.jumpEvaluations.size(), actual.jumpEvaluations.size());
        for (int i = 0; i < expected.jumpEvaluations.size(); i++) {
            JumpDecision expectedDecision = expected.jumpEvaluations.get(i);
            JumpDecision actualDecision = actual.jumpEvaluations.get(i);
            assertEquals(expectedDecision.rule, actualDecision.rule);
            assertEquals(expectedDecision.action, actualDecision.action);
            assertEquals(expectedDecision.consumesAirCharge,
                    actualDecision.consumesAirCharge);
            assertEquals(expectedDecision.reason, actualDecision.reason);
        }
        assertEquals(expected.events.size(), actual.events.size());
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        if (expected == null) {
            assertEquals(null, actual);
            return;
        }
        assertNotNull(actual);
        assertSameDouble(expected.x, actual.x);
        assertSameDouble(expected.y, actual.y);
        assertSameDouble(expected.z, actual.z);
    }

    private static void assertSameDouble(double expected, double actual) {
        assertEquals(Double.doubleToLongBits(expected),
                Double.doubleToLongBits(actual));
    }
}
