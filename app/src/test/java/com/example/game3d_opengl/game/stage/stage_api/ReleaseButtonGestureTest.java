package com.example.game3d_opengl.game.stage.stage_api;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.game3d_opengl.rendering.util3d.rect.Rect;

import org.junit.Test;

public class ReleaseButtonGestureTest {
    private static final Rect BUTTON = new Rect(10f, 20f, 110f, 70f);

    @Test
    public void downOnlyCapturesAndMatchingReleaseActivatesOnce() {
        ReleaseButtonGesture gesture = new ReleaseButtonGesture();

        assertTrue(gesture.begin(BUTTON, 30f, 40f));
        assertTrue(gesture.isTracking());
        assertTrue(gesture.release(30f, 40f));

        assertFalse(gesture.isTracking());
        assertFalse(gesture.release(30f, 40f));
    }

    @Test
    public void downOutsideCannotActivateFromAnUpInside() {
        ReleaseButtonGesture gesture = new ReleaseButtonGesture();

        assertFalse(gesture.begin(BUTTON, 0f, 0f));

        assertFalse(gesture.release(30f, 40f));
    }

    @Test
    public void releaseOutsideDoesNotActivate() {
        ReleaseButtonGesture gesture = new ReleaseButtonGesture();
        gesture.begin(BUTTON, 30f, 40f);

        assertFalse(gesture.release(200f, 40f));
        assertFalse(gesture.isTracking());
    }

    @Test
    public void dragOutPermanentlyDisarmsEvenAfterMovingBack() {
        ReleaseButtonGesture gesture = new ReleaseButtonGesture();
        gesture.begin(BUTTON, 30f, 40f);

        gesture.move(200f, 40f);
        assertTrue(gesture.isTracking());
        gesture.move(30f, 40f);

        assertFalse(gesture.release(30f, 40f));
    }

    @Test
    public void cancelNeverActivatesAndNextGestureStillWorks() {
        ReleaseButtonGesture gesture = new ReleaseButtonGesture();
        gesture.begin(BUTTON, 30f, 40f);

        gesture.cancel();
        assertFalse(gesture.release(30f, 40f));

        assertTrue(gesture.begin(BUTTON, 30f, 40f));
        assertTrue(gesture.release(30f, 40f));
    }

    @Test
    public void newDownReplacesAnyDroppedOrStaleGesture() {
        ReleaseButtonGesture gesture = new ReleaseButtonGesture();
        gesture.begin(BUTTON, 30f, 40f);

        assertFalse(gesture.begin(BUTTON, 0f, 0f));

        assertFalse(gesture.release(30f, 40f));
    }
}
