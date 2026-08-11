package com.example.game3d_opengl.game.player.core;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.simulation.FixedStepAccumulator;
import com.example.game3d.core.simulation.PhysicsConfig;
import com.example.game3d.core.terrain.TrackBuilder;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AndroidSimulationControllerTest {
    @Test
    public void reusesTheImmutablePresentationFrameBetweenTicks() {
        AndroidSimulationController controller = new AndroidSimulationController(
                new TrackBuilder(8.0).straight(100.0).buildSnapshot(),
                new Vec3(0.0, 5.0, 1.0),
                0, 1000, 1_000_000_000L, null);

        Object initial = controller.currentFrameSnapshot();
        assertSame(initial, controller.currentFrameSnapshot());

        controller.advanceFrameNanos(PhysicsConfig.FIXED_DT_NANOS);

        Object afterTick = controller.currentFrameSnapshot();
        assertNotSame(initial, afterTick);
        assertSame(afterTick, controller.currentFrameSnapshot());
    }

    @Test
    public void normalizesInputAndAdvancesOnlyFixedTicks() {
        AtomicInteger ticks = new AtomicInteger();
        AndroidSimulationController controller = new AndroidSimulationController(
                new TrackBuilder(8.0).straight(100.0).build(),
                new Vec3(0.0, 5.0, 1.0),
                0, 1000, 1_000_000_000L,
                new AndroidSimulationController.TickListener() {
                    @Override
                    public void onPhysicsTick(
                            com.example.game3d.core.simulation.StepResult result) {
                        ticks.incrementAndGet();
                    }

                    @Override
                    public void onSimulationOverrun(long retainedNanos) {
                    }
        });
        assertTrue(controller.touchDown(1_000_000_000L));
        assertTrue(controller.touchMove(
                0f, 500f, 0f, 300f, 1_001_000_000L));
        FixedStepAccumulator.AdvanceResult first = controller.advanceFrameMillis(16.666666f);

        assertEquals(2, first.executedTicks);
        assertEquals(2, ticks.get());
        assertFalse(first.overrun);
        assertEquals(1.0, controller.currentSnapshot().gestureCharge, 1.0e-9);
    }

    @Test
    public void jumpClassificationUsesRawFingerPathNotScaledSteering() {
        AndroidSimulationController lowHorizontal = controller(1000);
        AndroidSimulationController highHorizontal = controller(1000);

        enqueueRawSwipe(lowHorizontal, 4f, -32f, 40f, -40f);
        enqueueRawSwipe(highHorizontal, 40f, -32f, 40f, -40f);

        assertEquals(lowHorizontal.currentSnapshot().gestureCharge,
                highHorizontal.currentSnapshot().gestureCharge, 0.0);
        assertTrue(lowHorizontal.currentSnapshot().gestureCharge
                >= new PhysicsConfig().jumpChargeThreshold);
        assertTrue("scaled steering must still change yaw",
                Math.abs(lowHorizontal.currentSnapshot().yawRadians
                        - highHorizontal.currentSnapshot().yawRadians) > 1.0e-6);
    }

    @Test
    public void rawAbsoluteHorizontalLimitRejectsOtherwiseVerticalScaledInput() {
        AndroidSimulationController controller = controller(1000);

        enqueueRawSwipe(controller, 0f, -100f, 61f, -100f);

        assertEquals(0.0, controller.currentSnapshot().gestureCharge, 0.0);
    }

    @Test
    public void rawHorizontalToVerticalRatioRejectsOtherwiseVerticalScaledInput() {
        AndroidSimulationController controller = controller(1000);

        enqueueRawSwipe(controller, 0f, -32f, 50f, -40f);

        assertEquals(0.0, controller.currentSnapshot().gestureCharge, 0.0);
    }

    private static AndroidSimulationController controller(int screenHeight) {
        return new AndroidSimulationController(
                new TrackBuilder(8.0).straight(100.0).build(),
                new Vec3(0.0, 5.0, 1.0),
                0, screenHeight, 1_000_000_000L, null);
    }

    private static void enqueueRawSwipe(
            AndroidSimulationController controller,
            float scaledDx, float scaledDy,
            float rawDx, float rawDy) {
        assertTrue(controller.touchDown(1_000_000_000L));
        assertTrue(controller.touchMoveDelta(
                scaledDx, scaledDy, rawDx, rawDy, 1_001_000_000L));
        controller.advanceFrameNanos(2L * PhysicsConfig.FIXED_DT_NANOS);
    }
}
