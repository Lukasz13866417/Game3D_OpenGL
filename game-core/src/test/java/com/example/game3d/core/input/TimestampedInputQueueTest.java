package com.example.game3d.core.input;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TimestampedInputQueueTest {
    @Test
    public void preservesAllEdgesWithoutApplyingThemBeforeTheirTimestamp() {
        TimestampedInputQueue queue = new TimestampedInputQueue();
        queue.enqueue(PlayerInputEvent.down(1L, 1L));
        queue.enqueue(PlayerInputEvent.up(5L, 3L));
        queue.enqueue(PlayerInputEvent.swipe(3L, 2L, 0.1, -0.2));
        queue.enqueue(PlayerInputEvent.down(10L, 4L));

        FixedStepInput first = queue.drain(0L, 10L);
        assertEquals(0, first.events.size());

        FixedStepInput second = queue.drain(10L, 20L);
        assertEquals(4, second.events.size());
        assertEquals(PlayerInputEvent.Type.TOUCH_DOWN, second.events.get(0).type);
        assertEquals(PlayerInputEvent.Type.SWIPE, second.events.get(1).type);
        assertEquals(PlayerInputEvent.Type.TOUCH_UP, second.events.get(2).type);
        assertEquals(10L, second.events.get(3).timeNanos);
    }

    @Test
    public void lateEdgesAreAppliedInsteadOfDiscarded() {
        TimestampedInputQueue queue = new TimestampedInputQueue();
        queue.enqueue(PlayerInputEvent.up(5L, 1L));

        FixedStepInput input = queue.drain(10L, 20L);

        assertEquals(1, input.events.size());
        assertEquals(PlayerInputEvent.Type.TOUCH_UP, input.events.get(0).type);
    }

    @Test
    public void equalTimestampEventsUseSequenceOrder() {
        TimestampedInputQueue queue = new TimestampedInputQueue();
        queue.enqueue(PlayerInputEvent.up(10L, 30L));
        queue.enqueue(PlayerInputEvent.swipe(10L, 20L, 0.1, -0.2));
        queue.enqueue(PlayerInputEvent.down(10L, 10L));

        FixedStepInput input = queue.drain(10L, 20L);

        assertEquals(PlayerInputEvent.Type.TOUCH_DOWN, input.events.get(0).type);
        assertEquals(PlayerInputEvent.Type.SWIPE, input.events.get(1).type);
        assertEquals(PlayerInputEvent.Type.TOUCH_UP, input.events.get(2).type);
    }

    @Test
    public void futureEventsRemainQueuedAndClearRemovesThem() {
        TimestampedInputQueue queue = new TimestampedInputQueue();
        queue.enqueue(PlayerInputEvent.down(50L, 1L));

        assertEquals(0, queue.drain(10L, 20L).events.size());
        assertEquals(1, queue.size());
        queue.clear();
        assertEquals(0, queue.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidTickIntervalIsRejected() {
        new TimestampedInputQueue().drain(10L, 10L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeInputTimestampIsRejected() {
        PlayerInputEvent.down(-1L, 0L);
    }
}
