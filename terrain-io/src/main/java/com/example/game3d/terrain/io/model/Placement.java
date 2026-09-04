package com.example.game3d.terrain.io.model;

import java.util.Objects;

/** Either an existing grid reservation or an exact segment-local normalized placement. */
public final class Placement {
    public enum Mode { GRID, SEGMENT_NORMALIZED }

    private final Mode mode;
    private final String segmentSourceId;
    private final int rowStart;
    private final int rowEnd;
    private final int columnStart;
    private final int columnEnd;
    private final double across;
    private final double along;

    private Placement(Mode mode, String segmentSourceId, int rowStart, int rowEnd,
                      int columnStart, int columnEnd, double across, double along) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.segmentSourceId = segmentSourceId;
        this.rowStart = rowStart;
        this.rowEnd = rowEnd;
        this.columnStart = columnStart;
        this.columnEnd = columnEnd;
        this.across = across;
        this.along = along;
    }

    public static Placement grid(int rowStart, int rowEnd, int columnStart, int columnEnd) {
        return new Placement(Mode.GRID, null, rowStart, rowEnd, columnStart, columnEnd, 0, 0);
    }

    public static Placement normalized(String segmentSourceId, double across, double along) {
        if (!Double.isFinite(across) || !Double.isFinite(along)) {
            throw new IllegalArgumentException(
                    "Normalized placement coordinates must be finite");
        }
        return new Placement(Mode.SEGMENT_NORMALIZED,
                Objects.requireNonNull(segmentSourceId, "segmentSourceId"), 0, 0, 0, 0,
                across, along);
    }

    public Mode mode() { return mode; }
    public String segmentSourceId() { return segmentSourceId; }
    public int rowStart() { return rowStart; }
    public int rowEnd() { return rowEnd; }
    public int columnStart() { return columnStart; }
    public int columnEnd() { return columnEnd; }
    public double across() { return across; }
    public double along() { return along; }
}
