package com.example.game3d.terrain.editor.ui;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.terrain.editor.importing.BuiltinProviderImporter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrbitCameraStateTest {
    private static final double EPSILON = 1.0e-9;

    @Test void everyBuiltInUsesItsCompleteBoundsAndFitsTheConfiguredViewport() {
        BuiltinProviderImporter importer = new BuiltinProviderImporter();
        for (String provider : importer.providerIds()) {
            PreviewBounds bounds = PreviewBounds.from(
                    importer.materialize(provider, 0L).originalSnapshot());
            for (double[] viewport : List.of(
                    new double[] {900, 600}, new double[] {1366, 768})) {
                OrbitCameraState camera = new OrbitCameraState();
                camera.frame(bounds, viewport[0], viewport[1], 30);

                String context = provider + " @ " + (int) viewport[0]
                        + "x" + (int) viewport[1];
                assertVec(bounds.center(), camera.target(), context + " target");
                assertTrue(camera.distance() > bounds.radius(), context + " distance");
                assertBoundsFit(bounds, camera, viewport[0] / viewport[1], 30.0,
                        context);
            }
        }
    }

    @Test void orbitPreservesTargetAndZoomLimitsScaleWithSceneRadius() {
        PreviewBounds bounds = PreviewBounds.aroundWorld(
                new Vec3(-1000, -30, -2000), new Vec3(1000, 50, 0));
        OrbitCameraState camera = new OrbitCameraState();
        camera.frame(bounds, 900, 650, 30);
        Vec3 target = camera.target();

        camera.orbit(143, -900);
        assertVec(target, camera.target(), "orbit target");
        assertEquals(85.0, camera.pitchDegrees(), EPSILON);

        camera.zoom(100_000);
        assertEquals(bounds.radius() * .05, camera.distance(), 1.0e-7);
        camera.zoom(-100_000);
        assertEquals(bounds.radius() * 50.0, camera.distance(), 1.0e-6);
        assertTrue(camera.distance() > 120.0,
                "large scenes must not inherit the old absolute 120-unit limit");
    }

    @Test void panningMovesTheTargetInTheCurrentCameraPlane() {
        OrbitCameraState camera = new OrbitCameraState();
        camera.frame(PreviewBounds.aroundWorld(
                new Vec3(-2, -1, -4), new Vec3(2, 1, 4)), 800, 600, 30);
        Vec3 before = camera.target();
        double distance = camera.distance();

        camera.pan(30, -20, 600, 30);

        assertNotEquals(before, camera.target());
        assertEquals(distance, camera.distance(), EPSILON);
        assertEquals(0.0, camera.target().subtract(before).dot(camera.forward()), 1.0e-9,
                "pan must remain in the view plane");
    }

    private static void assertBoundsFit(PreviewBounds bounds, OrbitCameraState camera,
                                        double aspect, double verticalFovDegrees,
                                        String message) {
        Vec3 forward = camera.forward();
        Vec3 right = forward.cross(new Vec3(0, -1, 0)).normalized();
        Vec3 down = forward.cross(right).normalized();
        double verticalTangent = Math.tan(Math.toRadians(verticalFovDegrees) * .5);
        double horizontalTangent = verticalTangent * aspect;
        Vec3 min = bounds.minimum();
        Vec3 max = bounds.maximum();
        for (double x : List.of(min.x, max.x)) {
            for (double y : List.of(min.y, max.y)) {
                for (double z : List.of(min.z, max.z)) {
                    Vec3 relative = new Vec3(x, y, z).subtract(camera.position());
                    double depth = relative.dot(forward);
                    assertTrue(depth > 0.0, message + " corner behind camera");
                    assertTrue(Math.abs(relative.dot(right)) <= depth * horizontalTangent,
                            message + " horizontal framing");
                    assertTrue(Math.abs(relative.dot(down)) <= depth * verticalTangent,
                            message + " vertical framing");
                }
            }
        }
    }

    private static void assertVec(Vec3 expected, Vec3 actual, String message) {
        assertEquals(expected.x, actual.x, EPSILON, message + " x");
        assertEquals(expected.y, actual.y, EPSILON, message + " y");
        assertEquals(expected.z, actual.z, EPSILON, message + " z");
    }
}
