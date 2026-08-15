package com.example.game3d.authoring;

/** Session-wide dimensions used to turn dimension-neutral commands into terrain. */
public final class TrackProfile {
    public static final String GAMEPLAY_PROFILE_ID = "gameplay-default-v1";

    public final String id;
    public final double width;
    public final double tileLength;
    public final int gridColumns;
    public final double rowSpacing;

    public TrackProfile(
            String id, double width, double tileLength,
            int gridColumns, double rowSpacing) {
        if (id == null || id.isEmpty()
                || !finitePositive(width) || !finitePositive(tileLength)
                || gridColumns < 1 || !finitePositive(rowSpacing)) {
            throw new IllegalArgumentException("Invalid track profile");
        }
        this.id = id;
        this.width = width;
        this.tileLength = tileLength;
        this.gridColumns = gridColumns;
        this.rowSpacing = rowSpacing;
    }

    public static TrackProfile gameplayDefault() {
        return new TrackProfile(GAMEPLAY_PROFILE_ID, 3.2, 1.4, 6, 1.0);
    }

    private static boolean finitePositive(double value) {
        return value > 0.0 && Double.isFinite(value);
    }
}
