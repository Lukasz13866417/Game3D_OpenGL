package com.example.game3d_opengl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class MyGLRendererTest {
    @Test
    public void repeatedSlowFrameWarningsAreLimitedToOncePerSecond() {
        long firstLogNanos = 5_000_000_000L;

        assertTrue(MyGLRenderer.shouldLogSlowFrameWarning(
                Long.MIN_VALUE, firstLogNanos));
        assertFalse(MyGLRenderer.shouldLogSlowFrameWarning(
                firstLogNanos,
                firstLogNanos
                        + MyGLRenderer.SLOW_FRAME_LOG_INTERVAL_NANOS
                        - 1L));
        assertTrue(MyGLRenderer.shouldLogSlowFrameWarning(
                firstLogNanos,
                firstLogNanos
                        + MyGLRenderer.SLOW_FRAME_LOG_INTERVAL_NANOS));
        assertTrue(MyGLRenderer.shouldLogSlowFrameWarning(
                firstLogNanos, firstLogNanos - 1L));
    }

    @Test
    public void skipped120HzSlotsAreDerivedFromRawUncappedVsyncTime() {
        assertEquals(0, MyGLRenderer.skipped120HzSlots(8_333_333L));
        assertEquals(1, MyGLRenderer.skipped120HzSlots(16_666_666L));
        assertEquals(2, MyGLRenderer.skipped120HzSlots(24_999_999L));
        assertEquals(4, MyGLRenderer.skipped120HzSlots(41_666_665L));
    }

    @Test
    public void firstFrameStartsTimelineWithoutInventingElapsedTime() {
        long firstVsync = 9_123_456_789L;

        assertEquals(0L,
                MyGLRenderer.elapsedSinceAcceptedFrame(-1L, firstVsync));
        assertEquals(firstVsync,
                MyGLRenderer.advanceAcceptedFrameTime(-1L, firstVsync));
    }

    @Test
    public void duplicateOrStaleVsyncCannotRewindTimeline() {
        long accepted = 9_123_456_789L;

        assertEquals(0L,
                MyGLRenderer.elapsedSinceAcceptedFrame(accepted, accepted));
        assertEquals(accepted,
                MyGLRenderer.advanceAcceptedFrameTime(accepted, accepted));
        assertEquals(0L,
                MyGLRenderer.elapsedSinceAcceptedFrame(
                        accepted, accepted - 1L));
        assertEquals(accepted,
                MyGLRenderer.advanceAcceptedFrameTime(
                        accepted, accepted - 1L));
    }

    @Test
    public void exactVsyncCadenceReachesRendererWithoutMillisecondQuantization() {
        long first = 9_123_456_789L;
        long second = first + 8_333_333L;
        long third = second + 8_333_334L;

        assertEquals(8_333_333L,
                MyGLRenderer.elapsedSinceAcceptedFrame(first, second));
        assertEquals(8_333_334L,
                MyGLRenderer.elapsedSinceAcceptedFrame(second, third));
    }

    @Test
    public void coalescedVsyncsPublishLatestAtomicSampleAndConsumeItOnce() {
        MyGLRenderer.VsyncHandoff handoff = new MyGLRenderer.VsyncHandoff();

        handoff.publish(100L);
        handoff.publish(200L);
        handoff.publish(300L);

        MyGLRenderer.VsyncHandoff.DrawInput draw = consume(handoff);
        assertTrue(draw.hasSample);
        assertEquals(300L, draw.presentationTimeNanos);
        assertEquals(3L, draw.sequence);
        assertFalse(draw.resetRequested);

        MyGLRenderer.VsyncHandoff.DrawInput duplicateDraw =
                consume(handoff);
        assertFalse(duplicateDraw.hasSample);
        assertFalse(duplicateDraw.resetRequested);
    }

    @Test
    public void vsyncPublishedDuringInFlightDrawIsReservedForNextDraw()
            throws Exception {
        MyGLRenderer.VsyncHandoff handoff = new MyGLRenderer.VsyncHandoff();
        handoff.publish(100L);
        CountDownLatch drawClaimedSample = new CountDownLatch(1);
        CountDownLatch finishDraw = new CountDownLatch(1);
        AtomicReference<MyGLRenderer.VsyncHandoff.DrawInput> inFlight =
                new AtomicReference<>();

        Thread glThread = new Thread(() -> {
            inFlight.set(consume(handoff));
            drawClaimedSample.countDown();
            awaitUninterruptibly(finishDraw);
        });
        glThread.start();

        assertTrue(drawClaimedSample.await(5L, TimeUnit.SECONDS));
        handoff.publish(200L);
        finishDraw.countDown();
        glThread.join(5_000L);
        assertFalse(glThread.isAlive());

        assertTrue(inFlight.get().hasSample);
        assertEquals(100L, inFlight.get().presentationTimeNanos);
        assertEquals(1L, inFlight.get().sequence);

        MyGLRenderer.VsyncHandoff.DrawInput nextDraw =
                consume(handoff);
        assertTrue(nextDraw.hasSample);
        assertEquals(200L, nextDraw.presentationTimeNanos);
        assertEquals(2L, nextDraw.sequence);
        assertFalse(consume(handoff).hasSample);
    }

    @Test
    public void lifecycleResetAtomicallyDropsStaleSampleAndMarksFreshEpoch() {
        MyGLRenderer.VsyncHandoff handoff = new MyGLRenderer.VsyncHandoff();
        handoff.publish(100L);

        handoff.reset();
        handoff.publish(200L);

        MyGLRenderer.VsyncHandoff.DrawInput resumedDraw =
                consume(handoff);
        assertTrue(resumedDraw.resetRequested);
        assertTrue(resumedDraw.hasSample);
        assertEquals(200L, resumedDraw.presentationTimeNanos);
        assertEquals(2L, resumedDraw.sequence);

        MyGLRenderer.VsyncHandoff.DrawInput followingDraw =
                consume(handoff);
        assertFalse(followingDraw.resetRequested);
        assertFalse(followingDraw.hasSample);
    }

    @Test
    public void redundantDirtyDrawCannotInventATimestampInVsyncMode() {
        MyGLRenderer.VsyncHandoff.DrawInput empty =
                new MyGLRenderer.VsyncHandoff.DrawInput();
        MyGLRenderer.VsyncHandoff.DrawInput sampled =
                new MyGLRenderer.VsyncHandoff.DrawInput();
        sampled.hasSample = true;
        sampled.presentationTimeNanos = 100L;
        sampled.sequence = 1L;

        assertFalse(MyGLRenderer.shouldAdvanceTimeline(false, empty));
        assertTrue(MyGLRenderer.shouldAdvanceTimeline(false, sampled));
        assertTrue(MyGLRenderer.shouldAdvanceTimeline(true, empty));
    }

    private static MyGLRenderer.VsyncHandoff.DrawInput consume(
            MyGLRenderer.VsyncHandoff handoff) {
        MyGLRenderer.VsyncHandoff.DrawInput output =
                new MyGLRenderer.VsyncHandoff.DrawInput();
        handoff.consumeLatest(output);
        return output;
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
