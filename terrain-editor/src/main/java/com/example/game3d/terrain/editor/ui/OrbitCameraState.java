package com.example.game3d.terrain.editor.ui;

import com.example.game3d.core.math.Vec3;

/** Target-relative orbit state; independent of JavaFX so framing math is unit-testable. */
final class OrbitCameraState {
    static final double DEFAULT_YAW_DEGREES = -18.0;
    static final double DEFAULT_PITCH_DEGREES = 24.0;
    private static final double MIN_PITCH = -85.0;
    private static final double MAX_PITCH = 85.0;
    private static final double FIT_MARGIN = 1.15;

    private Vec3 target = Vec3.ZERO;
    private double distance = 14.0;
    private double sceneRadius = 1.0;
    private double yawDegrees = DEFAULT_YAW_DEGREES;
    private double pitchDegrees = DEFAULT_PITCH_DEGREES;
    private boolean initialized;

    boolean isInitialized() {
        return initialized;
    }

    Vec3 target() {
        return target;
    }

    double distance() {
        return distance;
    }

    double yawDegrees() {
        return yawDegrees;
    }

    double pitchDegrees() {
        return pitchDegrees;
    }

    double sceneRadius() {
        return sceneRadius;
    }

    Vec3 forward() {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double horizontal = Math.cos(pitch);
        return new Vec3(Math.sin(yaw) * horizontal,
                Math.sin(pitch), Math.cos(yaw) * horizontal).normalized();
    }

    Vec3 position() {
        return target.subtract(forward().multiply(distance));
    }

    void frame(PreviewBounds bounds, double viewportWidth,
               double viewportHeight, double verticalFovDegrees) {
        if (bounds == null || bounds.isEmpty()) return;
        target = bounds.center();
        sceneRadius = bounds.radius();
        distance = fitDistance(sceneRadius, viewportWidth, viewportHeight,
                verticalFovDegrees);
        initialized = true;
    }

    void resetAndFrame(PreviewBounds bounds, double viewportWidth,
                       double viewportHeight, double verticalFovDegrees) {
        yawDegrees = DEFAULT_YAW_DEGREES;
        pitchDegrees = DEFAULT_PITCH_DEGREES;
        frame(bounds, viewportWidth, viewportHeight, verticalFovDegrees);
    }

    void orbit(double deltaX, double deltaY) {
        yawDegrees = normalizeDegrees(yawDegrees + deltaX * .3);
        pitchDegrees = clamp(pitchDegrees - deltaY * .3,
                MIN_PITCH, MAX_PITCH);
    }

    void zoom(double wheelDelta) {
        if (!initialized || wheelDelta == 0.0) return;
        distance = clamp(distance * Math.exp(-wheelDelta * .0015),
                Math.max(.1, sceneRadius * .05),
                Math.max(20.0, sceneRadius * 50.0));
    }

    void pan(double deltaX, double deltaY, double viewportHeight,
             double verticalFovDegrees) {
        if (!initialized) return;
        Vec3 forward = forward();
        Vec3 fxUp = new Vec3(0, -1, 0);
        Vec3 right = forward.cross(fxUp).normalized();
        if (right.lengthSquared() < 1.0e-12) right = new Vec3(1, 0, 0);
        Vec3 up = right.cross(forward).normalized();
        double height = Math.max(1.0, viewportHeight);
        double unitsPerPixel = 2.0 * distance
                * Math.tan(Math.toRadians(verticalFovDegrees) * .5) / height;
        target = target.add(right.multiply(-deltaX * unitsPerPixel))
                .add(up.multiply(-deltaY * unitsPerPixel));
    }

    void updateSceneRadius(double radius) {
        sceneRadius = Math.max(.05, radius);
        distance = clamp(distance, Math.max(.1, sceneRadius * .05),
                Math.max(20.0, sceneRadius * 50.0));
    }

    static double fitDistance(double radius, double viewportWidth,
                              double viewportHeight, double verticalFovDegrees) {
        double safeRadius = Math.max(.05, radius);
        double aspect = Math.max(.1, viewportWidth) / Math.max(.1, viewportHeight);
        double verticalHalf = Math.toRadians(verticalFovDegrees) * .5;
        double horizontalHalf = Math.atan(Math.tan(verticalHalf) * aspect);
        double limitingHalf = Math.max(Math.toRadians(2.0),
                Math.min(verticalHalf, horizontalHalf));
        return Math.max(.2, safeRadius / Math.sin(limitingHalf) * FIT_MARGIN);
    }

    private static double normalizeDegrees(double value) {
        double normalized = value % 360.0;
        if (normalized <= -180.0) normalized += 360.0;
        if (normalized > 180.0) normalized -= 360.0;
        return normalized;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
