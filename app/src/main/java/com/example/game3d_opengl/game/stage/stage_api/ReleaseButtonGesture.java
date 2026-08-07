package com.example.game3d_opengl.game.stage.stage_api;

import com.example.game3d_opengl.rendering.util3d.rect.Rect;

/**
 * Allocation-free state for a button that activates on release rather than press-down.
 *
 * <p>The gesture must start inside the button and remain inside it. Once a captured pointer
 * leaves the bounds, that gesture stays disarmed even if it later moves back over the button.
 * This avoids accidental actions after drags and ensures an Android cancellation can never be
 * interpreted as a click.</p>
 */
public final class ReleaseButtonGesture {
    private Rect capturedBounds;
    private boolean tracking;
    private boolean armed;

    /** Captures a new gesture if its DOWN is inside {@code bounds}. No action is activated. */
    public boolean begin(Rect bounds, float x, float y) {
        cancel();
        if (bounds == null || !bounds.containsPoint(x, y)) {
            return false;
        }
        capturedBounds = bounds;
        tracking = true;
        armed = true;
        return true;
    }

    /** Permanently disarms the captured gesture once it leaves the original button. */
    public void move(float x, float y) {
        if (tracking && armed
                && !capturedBounds.containsPoint(x, y)) {
            armed = false;
        }
    }

    /**
     * Ends the gesture and reports whether the button should activate on this UP.
     */
    public boolean release(float x, float y) {
        boolean shouldActivate = tracking
                && armed
                && capturedBounds != null
                && capturedBounds.containsPoint(x, y);
        cancel();
        return shouldActivate;
    }

    /** Clears capture without activating. */
    public void cancel() {
        capturedBounds = null;
        tracking = false;
        armed = false;
    }

    /** True while this control owns the current pointer, including after drag-out. */
    public boolean isTracking() {
        return tracking;
    }
}
