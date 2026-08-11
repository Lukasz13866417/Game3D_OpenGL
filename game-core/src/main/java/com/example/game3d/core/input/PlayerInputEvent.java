package com.example.game3d.core.input;

/**
 * Timestamped, resolution-independent player input. Swipe values are fractions of screen height.
 * The primary deltas have gameplay sensitivity applied; the raw deltas preserve the physical
 * finger path for sensitivity-independent gesture classification.
 */
public final class PlayerInputEvent implements Comparable<PlayerInputEvent> {
    public enum Type {
        TOUCH_DOWN,
        SWIPE,
        TOUCH_UP,
        CANCEL_GESTURE
    }

    public final long timeNanos;
    public final long sequence;
    public final Type type;
    public final double deltaXScreenHeights;
    public final double deltaYScreenHeights;
    public final double rawDeltaXScreenHeights;
    public final double rawDeltaYScreenHeights;

    private PlayerInputEvent(long timeNanos, long sequence, Type type,
                             double deltaXScreenHeights, double deltaYScreenHeights,
                             double rawDeltaXScreenHeights,
                             double rawDeltaYScreenHeights) {
        if (timeNanos < 0L) {
            throw new IllegalArgumentException("Input time cannot be negative");
        }
        this.timeNanos = timeNanos;
        this.sequence = sequence;
        this.type = type;
        this.deltaXScreenHeights = deltaXScreenHeights;
        this.deltaYScreenHeights = deltaYScreenHeights;
        this.rawDeltaXScreenHeights = rawDeltaXScreenHeights;
        this.rawDeltaYScreenHeights = rawDeltaYScreenHeights;
    }

    public static PlayerInputEvent down(long timeNanos, long sequence) {
        return new PlayerInputEvent(
                timeNanos, sequence, Type.TOUCH_DOWN, 0.0, 0.0, 0.0, 0.0);
    }

    public static PlayerInputEvent swipe(long timeNanos, long sequence,
                                         double deltaXScreenHeights,
                                         double deltaYScreenHeights) {
        return swipe(timeNanos, sequence,
                deltaXScreenHeights, deltaYScreenHeights,
                deltaXScreenHeights, deltaYScreenHeights);
    }

    /**
     * Creates a swipe with separate sensitivity-scaled motion and raw physical classification
     * motion. All four deltas use screen-height units.
     */
    public static PlayerInputEvent swipe(long timeNanos, long sequence,
                                         double deltaXScreenHeights,
                                         double deltaYScreenHeights,
                                         double rawDeltaXScreenHeights,
                                         double rawDeltaYScreenHeights) {
        return new PlayerInputEvent(timeNanos, sequence, Type.SWIPE,
                deltaXScreenHeights, deltaYScreenHeights,
                rawDeltaXScreenHeights, rawDeltaYScreenHeights);
    }

    public static PlayerInputEvent up(long timeNanos, long sequence) {
        return new PlayerInputEvent(
                timeNanos, sequence, Type.TOUCH_UP, 0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Cancels a held gesture without interpreting it as a jump request.
     * Used for lifecycle interruptions and Android ACTION_CANCEL.
     */
    public static PlayerInputEvent cancel(long timeNanos, long sequence) {
        return new PlayerInputEvent(
                timeNanos, sequence, Type.CANCEL_GESTURE,
                0.0, 0.0, 0.0, 0.0);
    }

    @Override
    public int compareTo(PlayerInputEvent other) {
        int timeOrder = Long.compare(timeNanos, other.timeNanos);
        return timeOrder != 0 ? timeOrder : Long.compare(sequence, other.sequence);
    }
}
