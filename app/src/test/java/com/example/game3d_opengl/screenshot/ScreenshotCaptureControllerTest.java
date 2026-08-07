package com.example.game3d_opengl.screenshot;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ScreenshotCaptureControllerTest {
    @Test
    public void captureGateAllowsOnlyOneCaptureThroughSaving() {
        ScreenshotCaptureController.CaptureGate gate =
                new ScreenshotCaptureController.CaptureGate();

        assertTrue(gate.tryBegin());
        assertTrue(gate.isInFlight());
        assertFalse(gate.tryBegin());

        gate.finish();
        assertFalse(gate.isInFlight());
        assertTrue(gate.tryBegin());
    }

    @Test
    public void displayNameIsDeterministicAndGalleryFriendly() {
        long timestamp = 1_735_689_600_123L;
        String first = ScreenshotCaptureController.buildDisplayName(timestamp);
        String second = ScreenshotCaptureController.buildDisplayName(timestamp);

        assertTrue(first.equals(second));
        assertTrue(first.matches(
                "Game3D_[0-9]{8}_[0-9]{6}_[0-9]{3}\\.png"));
    }
}
