package com.example.game3d_opengl.game.player.core;

import com.example.game3d.core.input.PlayerInputEvent;
import com.example.game3d.core.input.TimestampedInputQueue;
import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.simulation.FixedStepAccumulator;
import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.simulation.PlayerSnapshot;
import com.example.game3d.core.simulation.SimulationEvent;
import com.example.game3d.core.simulation.SimulationEngine;
import com.example.game3d.core.simulation.StepObserver;
import com.example.game3d.core.simulation.StepResult;
import com.example.game3d.core.terrain.TerrainWorld;
import com.example.game3d.core.terrain.TrackBuilder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Contract tests for the Android clock adapter. Rendering cadence must not become part of the
 * authoritative simulation, and publishing a new immutable terrain snapshot must not create an
 * artificial player-state/interpolation discontinuity.
 */
public class AndroidSimulationControllerParityTest {
    private static final int SCREEN_HEIGHT = 1200;
    private static final long INPUT_EPOCH_NANOS = 2_000_000_000L;

    @Test
    public void irregularRenderFramesMatchOneFramePerPhysicsTickExactly() {
        TerrainWorld terrain = new TrackBuilder(100.0).straight(500.0).build();
        AndroidSimulationController fixedFrames = controller(terrain, 1);
        AndroidSimulationController irregularFrames = controller(terrain, 1);
        SimulationEngine desktopEngine = engine(terrain, 1);
        TimestampedInputQueue desktopInput = new TimestampedInputQueue();
        enqueueIdenticalInput(fixedFrames);
        enqueueIdenticalInput(irregularFrames);
        enqueueIdenticalInput(desktopInput);

        int expectedTicks = 180;
        for (int i = 0; i < expectedTicks; i++) {
            FixedStepAccumulator.AdvanceResult result =
                    fixedFrames.advanceFrameNanos(PhysicsConfig.FIXED_DT_NANOS);
            assertEquals(1, result.executedTicks);
            assertFalse(result.overrun);
            long tickStart = i * PhysicsConfig.FIXED_DT_NANOS;
            desktopEngine.step(desktopInput.drain(
                    tickStart, tickStart + PhysicsConfig.FIXED_DT_NANOS));
        }

        long remainingNanos = expectedTicks * PhysicsConfig.FIXED_DT_NANOS;
        long[] renderFramePattern = {
                1_000_000L,
                17_000_000L,
                4_444_444L,
                31_000_000L,
                8_000_000L,
                12_345_678L,
                2_000_000L
        };
        int patternIndex = 0;
        int irregularTicks = 0;
        while (remainingNanos > 0L) {
            long elapsed = Math.min(remainingNanos,
                    renderFramePattern[patternIndex++ % renderFramePattern.length]);
            FixedStepAccumulator.AdvanceResult result =
                    irregularFrames.advanceFrameNanos(elapsed);
            irregularTicks += result.executedTicks;
            assertFalse(result.overrun);
            remainingNanos -= elapsed;
        }

        assertEquals(expectedTicks, irregularTicks);
        assertSnapshotExactlyEqual(
                desktopEngine.snapshot(), fixedFrames.currentSnapshot());
        assertSnapshotExactlyEqual(
                fixedFrames.currentSnapshot(), irregularFrames.currentSnapshot());
        assertEquals(0.0, fixedFrames.renderAlpha(), 0.0);
        assertEquals(0.0, irregularFrames.renderAlpha(), 0.0);
    }

    @Test
    public void compatibleTerrainReplacementLeavesCanonicalAndInterpolationStateUntouched() {
        TerrainWorld initial = new TrackBuilder(20.0)
                .straight(20.0)
                .straight(180.0)
                .build();
        AndroidSimulationController controller = controller(initial, 0);

        for (int i = 0; i < 6; i++) {
            controller.advanceFrameNanos(PhysicsConfig.FIXED_DT_NANOS);
        }
        PlayerSnapshot previousBefore = controller.previousSnapshot();
        PlayerSnapshot currentBefore = controller.currentSnapshot();
        double alphaBefore = controller.renderAlpha();
        assertTrue("test precondition: player should have acquired support",
                currentBefore.grounded);
        assertTrue(currentBefore.supportTriangleId >= 0L);

        TerrainWorld extended = new TrackBuilder(20.0)
                .straight(20.0)
                .straight(180.0)
                .straight(100.0)
                .build();
        assertTrue(extended.containsTriangle(currentBefore.supportTriangleId));
        controller.replaceTerrain(extended);

        assertSame("terrain publication must not reset the interpolation history",
                previousBefore, controller.previousSnapshot());
        assertSame("terrain publication must not synthesize a new current snapshot",
                currentBefore, controller.currentSnapshot());
        assertEquals(alphaBefore, controller.renderAlpha(), 0.0);
        assertSnapshotExactlyEqual(currentBefore, controller.currentSnapshot());
    }

    @Test
    public void pauseCancelsGestureAndExcludesOnlyThePausedWallInterval() {
        TerrainWorld terrain = new TrackBuilder(20.0).straight(200.0).build();
        final int[] jumps = {0};
        AndroidSimulationController controller = new AndroidSimulationController(
                terrain,
                new Vec3(0.0, new PhysicsConfig().cylinderRadius + 0.002, 1.0),
                0,
                SCREEN_HEIGHT,
                INPUT_EPOCH_NANOS,
                new AndroidSimulationController.TickListener() {
                    @Override
                    public void onPhysicsTick(StepResult result) {
                        for (SimulationEvent event : result.events) {
                            if (event.type == SimulationEvent.Type.JUMP) {
                                jumps[0]++;
                            }
                        }
                    }

                    @Override
                    public void onSimulationOverrun(long retainedNanos) {
                    }
                });

        long activeBeforePause = 6L * PhysicsConfig.FIXED_DT_NANOS;
        controller.advanceFrameNanos(activeBeforePause);
        long pauseTime = INPUT_EPOCH_NANOS + activeBeforePause;
        controller.touchDown(pauseTime - 2_000_000L);
        controller.touchMoveDelta(0f, -400f, pauseTime - 1_000_000L);
        controller.pauseAt(pauseTime);
        long tickBeforePause = controller.currentSnapshot().tick;

        assertEquals(0, controller.advanceFrameNanos(5_000_000_000L).executedTicks);
        controller.resumeAt(pauseTime + 5_000_000_000L);
        FixedStepAccumulator.AdvanceResult resumed =
                controller.advanceFrameNanos(PhysicsConfig.FIXED_DT_NANOS);

        assertEquals(1, resumed.executedTicks);
        assertEquals(tickBeforePause + 1, controller.currentSnapshot().tick);
        assertEquals(0, jumps[0]);
        assertFalse(controller.currentSnapshot().touchHeld);
        assertEquals(0.0, controller.currentSnapshot().gestureCharge, 0.0);
    }

    @Test
    public void delayedTouchUpTimestampedInsideNonAlignedPauseCannotBeatCancellation() {
        TerrainWorld terrain = new TrackBuilder(20.0).straight(200.0).build();
        final int[] jumps = {0};
        AndroidSimulationController controller = new AndroidSimulationController(
                terrain,
                new Vec3(0.0, new PhysicsConfig().cylinderRadius + 0.002, 1.0),
                0,
                SCREEN_HEIGHT,
                INPUT_EPOCH_NANOS,
                new AndroidSimulationController.TickListener() {
                    @Override
                    public void onPhysicsTick(StepResult result) {
                        for (SimulationEvent event : result.events) {
                            if (event.type == SimulationEvent.Type.JUMP) {
                                jumps[0]++;
                            }
                        }
                    }

                    @Override
                    public void onSimulationOverrun(long retainedNanos) {
                    }
                });

        controller.touchDown(INPUT_EPOCH_NANOS + 5_000_000L);
        controller.touchMoveDelta(
                0f, -400f, INPUT_EPOCH_NANOS + 6_000_000L);
        long activeBeforePause =
                6L * PhysicsConfig.FIXED_DT_NANOS + 3_000_000L;
        controller.advanceFrameNanos(activeBeforePause);
        assertTrue(controller.currentSnapshot().touchHeld);
        assertTrue(controller.currentSnapshot().gestureCharge
                >= new PhysicsConfig().jumpChargeThreshold);

        long pauseTime = INPUT_EPOCH_NANOS + activeBeforePause;
        long resumeTime = pauseTime + 5_000_000_000L;
        controller.pauseAt(pauseTime);
        controller.resumeAt(resumeTime);

        // A lifecycle-stalled Stage queue may deliver this only after resume, even though the
        // MotionEvent occurred during the pause. It must not be compressed to time zero and
        // reordered ahead of the pause cancellation.
        assertFalse(controller.touchUp(
                pauseTime + 1_000_000_000L));
        controller.advanceFrameNanos(3L * PhysicsConfig.FIXED_DT_NANOS);

        assertEquals(0, jumps[0]);
        assertFalse(controller.currentSnapshot().touchHeld);
        assertEquals(0.0, controller.currentSnapshot().gestureCharge, 0.0);
    }

    @Test
    public void inputMethodsReportWhetherLifecycleFilteringAcceptedTheEvent() {
        AndroidSimulationController controller = controller(
                new TrackBuilder(20.0).straight(200.0).build(), 0);
        long pauseTime = INPUT_EPOCH_NANOS + 20_000_000L;
        long resumeTime = pauseTime + 1_000_000_000L;

        controller.pauseAt(pauseTime);
        assertFalse(controller.touchDown(pauseTime + 1L));
        assertFalse(controller.touchMoveDelta(
                20f, 0f, pauseTime + 2L));
        assertFalse(controller.touchUp(pauseTime + 3L));
        assertFalse(controller.cancelGesture(pauseTime + 4L));

        controller.resumeAt(resumeTime);
        assertTrue(controller.touchDown(resumeTime + 1L));
        assertTrue(controller.touchMoveDelta(
                20f, 0f, resumeTime + 2L));
        assertTrue(controller.touchUp(resumeTime + 3L));
        assertTrue(controller.cancelGesture(resumeTime + 4L));
    }

    @Test
    public void positiveAndroidYSwipeHeldThroughImpactSuppressesBounce() {
        TerrainWorld terrain = new TrackBuilder(20.0).straight(200.0).build();
        final int[] bounced = {0};
        final int[] suppressed = {0};
        final int[] jumped = {0};
        AndroidSimulationController controller = new AndroidSimulationController(
                terrain,
                new Vec3(0.0, 3.0, 1.0),
                0,
                SCREEN_HEIGHT,
                INPUT_EPOCH_NANOS,
                new AndroidSimulationController.TickListener() {
                    @Override
                    public void onPhysicsTick(StepResult result) {
                        for (SimulationEvent event : result.events) {
                            if (event.type == SimulationEvent.Type.BOUNCE) {
                                bounced[0]++;
                            } else if (event.type
                                    == SimulationEvent.Type.BOUNCE_SUPPRESSED) {
                                suppressed[0]++;
                            } else if (event.type == SimulationEvent.Type.JUMP) {
                                jumped[0]++;
                            }
                        }
                    }

                    @Override
                    public void onSimulationOverrun(long retainedNanos) {
                    }
                });

        controller.advanceFrameNanos(PhysicsConfig.FIXED_DT_NANOS);
        long gestureTime = INPUT_EPOCH_NANOS + PhysicsConfig.FIXED_DT_NANOS + 1_000_000L;
        controller.touchDown(gestureTime);
        controller.touchMoveDelta(0f, SCREEN_HEIGHT * 0.12f, gestureTime + 1L);
        for (int i = 0; i < 120 && suppressed[0] == 0; i++) {
            controller.advanceFrameNanos(PhysicsConfig.FIXED_DT_NANOS);
        }

        assertEquals(1, suppressed[0]);
        assertEquals(0, bounced[0]);
        assertTrue(controller.currentSnapshot().grounded);
        assertTrue(controller.currentSnapshot().touchHeld);
        assertFalse(controller.currentSnapshot().impactBrakeArmed);

        long rechargeTime = INPUT_EPOCH_NANOS
                + controller.currentSnapshot().timeNanos + 1_000_000L;
        controller.touchMoveDelta(
                0f, -SCREEN_HEIGHT * 0.12f, rechargeTime);
        controller.advanceFrameNanos(PhysicsConfig.FIXED_DT_NANOS);
        controller.advanceFrameNanos(PhysicsConfig.FIXED_DT_NANOS);

        assertTrue("same Android touch did not charge after braked landing",
                controller.currentSnapshot().gestureCharge
                        >= new PhysicsConfig().jumpChargeThreshold);
        assertTrue(controller.currentSnapshot().touchHeld);

        long releaseTime = INPUT_EPOCH_NANOS
                + controller.currentSnapshot().timeNanos + 1_000_000L;
        controller.touchUp(releaseTime);
        controller.advanceFrameNanos(PhysicsConfig.FIXED_DT_NANOS);
        controller.advanceFrameNanos(PhysicsConfig.FIXED_DT_NANOS);

        assertEquals(1, jumped[0]);
        assertFalse(controller.currentSnapshot().touchHeld);
    }

    private static AndroidSimulationController controller(
            TerrainWorld terrain, int initialAirJumpCharges) {
        PhysicsConfig config = new PhysicsConfig();
        return new AndroidSimulationController(
                terrain,
                new Vec3(0.0, config.cylinderRadius + 0.002, 1.0),
                initialAirJumpCharges,
                SCREEN_HEIGHT,
                INPUT_EPOCH_NANOS,
                null);
    }

    private static SimulationEngine engine(TerrainWorld terrain, int initialAirJumpCharges) {
        PhysicsConfig config = new PhysicsConfig();
        return new SimulationEngine(
                terrain,
                config,
                new Vec3(0.0, config.cylinderRadius + 0.002, 1.0),
                initialAirJumpCharges,
                StepObserver.NONE);
    }

    private static void enqueueIdenticalInput(AndroidSimulationController controller) {
        controller.touchDown(INPUT_EPOCH_NANOS + 5_000_000L);
        controller.touchMoveDelta(
                96.0f, -360.0f, INPUT_EPOCH_NANOS + 12_000_000L);
        controller.touchUp(INPUT_EPOCH_NANOS + 42_000_000L);

        controller.touchDown(INPUT_EPOCH_NANOS + 260_000_000L);
        controller.touchMoveDelta(
                -36.0f, -400.0f, INPUT_EPOCH_NANOS + 270_000_000L);
        controller.touchUp(INPUT_EPOCH_NANOS + 290_000_000L);
    }

    private static void enqueueIdenticalInput(TimestampedInputQueue input) {
        input.enqueue(PlayerInputEvent.down(5_000_000L, 0L));
        input.enqueue(PlayerInputEvent.swipe(
                12_000_000L, 1L,
                96.0 / SCREEN_HEIGHT, -360.0 / SCREEN_HEIGHT));
        input.enqueue(PlayerInputEvent.up(42_000_000L, 2L));

        input.enqueue(PlayerInputEvent.down(260_000_000L, 3L));
        input.enqueue(PlayerInputEvent.swipe(
                270_000_000L, 4L,
                -36.0 / SCREEN_HEIGHT, -400.0 / SCREEN_HEIGHT));
        input.enqueue(PlayerInputEvent.up(290_000_000L, 5L));
    }

    private static void assertSnapshotExactlyEqual(
            PlayerSnapshot expected, PlayerSnapshot actual) {
        assertEquals(expected.stateHash, actual.stateHash);
        assertEquals(expected.tick, actual.tick);
        assertEquals(expected.timeNanos, actual.timeNanos);
        assertVecExactlyEqual(expected.position, actual.position);
        assertVecExactlyEqual(expected.absolutePosition, actual.absolutePosition);
        assertVecExactlyEqual(expected.renderOrigin, actual.renderOrigin);
        assertVecExactlyEqual(expected.velocity, actual.velocity);
        assertVecExactlyEqual(expected.heading, actual.heading);
        assertVecExactlyEqual(expected.cylinderAxis, actual.cylinderAxis);
        assertEquals(expected.yawRadians, actual.yawRadians, 0.0);
        assertEquals(expected.axleRadians, actual.axleRadians, 0.0);
        assertEquals(expected.axleDeltaRadians, actual.axleDeltaRadians, 0.0);
        assertEquals(expected.angularVelocity, actual.angularVelocity, 0.0);
        assertEquals(expected.driveSurfaceSpeed, actual.driveSurfaceSpeed, 0.0);
        assertEquals(expected.gestureCharge, actual.gestureCharge, 0.0);
        assertEquals(expected.gestureChargePotential,
                actual.gestureChargePotential, 0.0);
        assertEquals(expected.gestureRawDeltaX,
                actual.gestureRawDeltaX, 0.0);
        assertEquals(expected.gestureRawUpwardDistance,
                actual.gestureRawUpwardDistance, 0.0);
        assertEquals(expected.gestureMaxAbsRawDeltaX,
                actual.gestureMaxAbsRawDeltaX, 0.0);
        assertEquals(expected.jumpChargePathEligible,
                actual.jumpChargePathEligible);
        assertEquals(expected.airJumpCharges, actual.airJumpCharges);
        assertEquals(expected.grounded, actual.grounded);
        assertEquals(expected.supportTriangleId, actual.supportTriangleId);
        assertVecExactlyEqual(expected.supportNormal, actual.supportNormal);
        assertEquals(expected.touchHeld, actual.touchHeld);
        assertEquals(expected.landingJumpArmed, actual.landingJumpArmed);
        assertEquals(expected.impactBrakeArmed, actual.impactBrakeArmed);
        assertEquals(expected.dead, actual.dead);
    }

    private static void assertVecExactlyEqual(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 0.0);
        assertEquals(expected.y, actual.y, 0.0);
        assertEquals(expected.z, actual.z, 0.0);
    }
}
