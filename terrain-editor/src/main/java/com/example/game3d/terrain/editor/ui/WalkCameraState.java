package com.example.game3d.terrain.editor.ui;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;

/** World-space state for the terrain preview's keyboard navigation mode. */
final class WalkCameraState {
    static final double DEFAULT_EYE_HEIGHT = 1.65;

    private Vec3 position = Vec3.ZERO;
    private double yawDegrees;
    private boolean initialized;

    boolean initializeIfNeeded(TerrainSnapshot snapshot) {
        if (initialized || snapshot == null) return initialized;
        for (TerrainSegment segment : snapshot.segments) {
            if (!segment.solid) continue;
            Vec3 nearCenter = segment.nearLeft.add(segment.nearRight).multiply(.5);
            Vec3 farCenter = segment.farLeft.add(segment.farRight).multiply(.5);
            Vec3 forward = farCenter.subtract(nearCenter).horizontalNormalized();
            if (forward.lengthSquared() < 1.0e-12) continue;

            Vec3 center = segment.nearLeft.add(segment.nearRight)
                    .add(segment.farLeft).add(segment.farRight).multiply(.25);
            position = center.add(Vec3.UP.multiply(DEFAULT_EYE_HEIGHT));
            yawDegrees = Math.toDegrees(Math.atan2(forward.x, forward.z));
            initialized = true;
            return true;
        }
        return false;
    }

    boolean isInitialized() {
        return initialized;
    }

    Vec3 position() {
        return position;
    }

    double yawDegrees() {
        return yawDegrees;
    }

    void move(double distance) {
        requireInitialized();
        double yawRadians = Math.toRadians(yawDegrees);
        position = position.add(new Vec3(
                Math.sin(yawRadians) * distance,
                0.0,
                Math.cos(yawRadians) * distance));
    }

    void turn(double degrees) {
        requireInitialized();
        yawDegrees = normalizeDegrees(yawDegrees + degrees);
    }

    void elevate(double distance) {
        requireInitialized();
        position = position.add(Vec3.UP.multiply(distance));
    }

    private void requireInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Walk camera has no solid terrain to start from");
        }
    }

    private static double normalizeDegrees(double value) {
        double normalized = value % 360.0;
        if (normalized <= -180.0) normalized += 360.0;
        if (normalized > 180.0) normalized -= 360.0;
        return normalized;
    }
}
