package com.example.game3d.terrain.editor.ui;

import com.example.game3d.core.math.Vec3;
import javafx.scene.PerspectiveCamera;
import javafx.scene.transform.Affine;

/** Applies a stable look direction to JavaFX's +Z-facing perspective camera. */
final class PreviewCameraTransforms {
    private static final Vec3 FX_UP = new Vec3(0, -1, 0);

    private PreviewCameraTransforms() {
    }

    static void apply(PerspectiveCamera camera, Vec3 position,
                      Vec3 requestedForward, double sceneRadius,
                      double targetDistance) {
        Vec3 forward = requestedForward.normalized();
        if (forward.lengthSquared() < 1.0e-12) forward = new Vec3(0, 0, 1);
        Vec3 right = forward.cross(FX_UP).normalized();
        if (right.lengthSquared() < 1.0e-12) right = new Vec3(1, 0, 0);
        Vec3 down = forward.cross(right).normalized();

        camera.setTranslateX(position.x);
        camera.setTranslateY(position.y);
        camera.setTranslateZ(position.z);
        camera.getTransforms().setAll(new Affine(
                right.x, down.x, forward.x, 0,
                right.y, down.y, forward.y, 0,
                right.z, down.z, forward.z, 0));

        double radius = Math.max(.05, sceneRadius);
        double distance = Math.max(.1, targetDistance);
        camera.setNearClip(Math.max(.02, Math.min(radius * .05,
                Math.max(.02, distance - radius * 1.5))));
        camera.setFarClip(Math.max(100.0, distance + radius * 4.0));
    }
}
