package com.example.game3d.authoring;

/** Exact resolved placement command emitted by a handwritten structure. */
public final class CapturedAddonPlacement {
    public final int tileIndex;
    public final int tileEndIndex;
    /** Zero-based physical GRID rows; -1 for segment-local placements. */
    public final int gridRowStart;
    public final int gridRowEnd;
    public final boolean gridPlacement;
    public final int declarationIndex;
    public final boolean poseAligned;
    public final double acrossStart;
    public final double acrossEnd;
    public final double alongStart;
    public final double alongEnd;

    CapturedAddonPlacement(
            int tileIndex, int tileEndIndex,
            int gridRowStart, int gridRowEnd, boolean gridPlacement,
            int declarationIndex,
            boolean poseAligned, double acrossStart, double acrossEnd,
            double alongStart, double alongEnd) {
        this.tileIndex = tileIndex;
        this.tileEndIndex = tileEndIndex;
        this.gridRowStart = gridRowStart;
        this.gridRowEnd = gridRowEnd;
        this.gridPlacement = gridPlacement;
        this.declarationIndex = declarationIndex;
        this.poseAligned = poseAligned;
        this.acrossStart = acrossStart;
        this.acrossEnd = acrossEnd;
        this.alongStart = alongStart;
        this.alongEnd = alongEnd;
    }
}
