package com.example.game3d.terrain.editor.ui;

import com.example.game3d.core.math.Vec3;
import com.example.game3d.core.terrain.SurfaceProperties;
import com.example.game3d.core.terrain.TerrainSegment;
import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.core.terrain.TerrainVertexAppearance;
import com.example.game3d.core.terrain.addon.Addon;
import javafx.scene.Camera;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the preview exactly as a user does: visible mode buttons, viewport focus, and keys. */
@ExtendWith(ApplicationExtension.class)
class TerrainPreviewNavigationInteractionTest {
    private static final double EPSILON = 1.0e-9;
    private static final Vec3 TRACK_FORWARD = new Vec3(.6, 0.0, -.8);
    private static final Vec3 TRACK_RIGHT = new Vec3(.8, 0.0, .6);
    private static final Vec3 SOLID_CENTER = new Vec3(11.2, 2.0, 18.4);

    private TerrainPreviewPane preview;

    @Start
    void start(Stage stage) {
        preview = new TerrainPreviewPane();
        preview.show(snapshotWithLeadingGapAndDiagonalSolid(),
                Collections.emptyMap(), Collections.emptyMap(), ignored -> { });
        stage.setScene(new Scene(preview, 900, 650));
        stage.show();
        stage.toFront();
    }

    @Test
    void walkModeRespondsToAllKeysAndOrbitRemainsAnIndependentMode(FxRobot robot) {
        awaitPreviewBuild();
        robot.clickOn("#preview-walk-mode");
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(preview.isFocused(),
                "Choosing Walk must immediately give the preview keyboard focus");

        assertEquals(TerrainPreviewPane.NavigationMode.WALK,
                preview.navigationModeForTesting());
        Vec3 start = preview.walkPositionForTesting();
        assertVecEquals(SOLID_CENTER.add(Vec3.UP.multiply(
                WalkCameraState.DEFAULT_EYE_HEIGHT)), start);
        assertEquals(Math.toDegrees(Math.atan2(TRACK_FORWARD.x, TRACK_FORWARD.z)),
                preview.walkYawForTesting(), EPSILON,
                "Walk mode must initially face the first solid tile's forward direction");
        assertCameraRepresents(start, preview.cameraForTesting());

        double beforeLookYaw = preview.walkYawForTesting();
        double beforeLookPitch = preview.walkPitchForTesting();
        robot.interact(() -> dragViewport(30, 20));
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(preview.walkYawForTesting() < beforeLookYaw,
                "dragging right must mouse-look to the right");
        assertTrue(preview.walkPitchForTesting() > beforeLookPitch,
                "dragging down must look farther down");
        press(robot, KeyCode.R);
        assertEquals(beforeLookYaw, preview.walkYawForTesting(), EPSILON);

        press(robot, KeyCode.W);
        Vec3 forward = preview.walkPositionForTesting();
        assertVecEquals(start.add(TRACK_FORWARD.multiply(.8)), forward);
        robot.interact(() -> preview.show(snapshotWithLeadingGapAndDiagonalSolid(),
                Collections.emptyMap(), Collections.emptyMap(), ignored -> { }));
        awaitPreviewBuild();
        assertVecEquals(forward, preview.walkPositionForTesting());
        press(robot, KeyCode.S);
        assertVecEquals(start, preview.walkPositionForTesting());

        double startingYaw = preview.walkYawForTesting();
        press(robot, KeyCode.A);
        assertEquals(startingYaw + 5.0, preview.walkYawForTesting(), EPSILON,
                "A must turn left");
        press(robot, KeyCode.W);
        assertTrue(preview.walkPositionForTesting().subtract(start).dot(TRACK_RIGHT) < 0.0,
                "Moving after A must head toward the tile's left side");
        press(robot, KeyCode.S);
        press(robot, KeyCode.D);
        assertEquals(startingYaw, preview.walkYawForTesting(), EPSILON,
                "D must undo the corresponding left turn");

        press(robot, KeyCode.D);
        press(robot, KeyCode.W);
        assertTrue(preview.walkPositionForTesting().subtract(start).dot(TRACK_RIGHT) > 0.0,
                "Moving after D must head toward the tile's right side");
        press(robot, KeyCode.S);
        press(robot, KeyCode.A);
        assertEquals(startingYaw, preview.walkYawForTesting(), EPSILON);

        press(robot, KeyCode.Q);
        Vec3 raised = preview.walkPositionForTesting();
        assertEquals(start.y + .35, raised.y, EPSILON, "Q must raise the camera");
        assertEquals(-raised.y, preview.cameraForTesting().getTranslateY(), EPSILON);
        press(robot, KeyCode.E);
        assertVecEquals(start, preview.walkPositionForTesting());

        press(robot, KeyCode.W);
        press(robot, KeyCode.R);
        assertVecEquals(start, preview.walkPositionForTesting());
        assertEquals(WalkCameraState.DEFAULT_PITCH_DEGREES,
                preview.walkPitchForTesting(), EPSILON);

        robot.clickOn("#preview-orbit-mode");
        clickViewport(robot);
        assertEquals(TerrainPreviewPane.NavigationMode.ORBIT,
                preview.navigationModeForTesting());
        Vec3 savedWalkPose = preview.walkPositionForTesting();
        press(robot, KeyCode.W);
        assertVecEquals(savedWalkPose, preview.walkPositionForTesting());

        robot.clickOn("#preview-walk-mode");
        clickViewport(robot);
        assertEquals(TerrainPreviewPane.NavigationMode.WALK,
                preview.navigationModeForTesting());
        assertVecEquals(savedWalkPose, preview.walkPositionForTesting());
        assertCameraRepresents(savedWalkPose, preview.cameraForTesting());
    }

    private void clickViewport(FxRobot robot) {
        // A real click away from the top-left controls exercises the same focus path as the user.
        robot.clickOn(preview, MouseButton.PRIMARY);
        WaitForAsyncUtils.waitForFxEvents();
        assertTrue(preview.isFocused(), "Clicking the viewport must give navigation keyboard focus");
    }

    private void dragViewport(double deltaX, double deltaY) {
        Node viewport = preview.lookup("#preview-viewport");
        viewport.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 100, 100, true));
        viewport.fireEvent(mouseEvent(
                MouseEvent.MOUSE_DRAGGED, 100 + deltaX, 100 + deltaY, true));
        viewport.fireEvent(mouseEvent(
                MouseEvent.MOUSE_RELEASED, 100 + deltaX, 100 + deltaY, false));
    }

    private static MouseEvent mouseEvent(
            javafx.event.EventType<MouseEvent> type,
            double x,
            double y,
            boolean primaryDown) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false,
                primaryDown, false, false,
                false, false, false, null);
    }

    private static void press(FxRobot robot, KeyCode key) {
        robot.press(key).release(key);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void awaitPreviewBuild() {
        try {
            WaitForAsyncUtils.waitFor(5, TimeUnit.SECONDS,
                    () -> preview.completedBuildTicketForTesting()
                            == preview.requestedBuildTicketForTesting());
            WaitForAsyncUtils.waitForFxEvents();
        } catch (TimeoutException timeout) {
            throw new AssertionError("Preview build did not attach", timeout);
        }
    }

    private static void assertCameraRepresents(Vec3 worldPosition, Camera camera) {
        assertEquals(worldPosition.x, camera.getTranslateX(), EPSILON);
        assertEquals(-worldPosition.y, camera.getTranslateY(), EPSILON);
        assertEquals(worldPosition.z, camera.getTranslateZ(), EPSILON);
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }

    private static TerrainSnapshot snapshotWithLeadingGapAndDiagonalSolid() {
        TerrainSegment gap = segment(0L,
                Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, false);
        Vec3 nearCenter = new Vec3(10.0, 2.0, 20.0);
        Vec3 farCenter = nearCenter.add(TRACK_FORWARD.multiply(4.0));
        Vec3 halfWidth = TRACK_RIGHT.multiply(1.6);
        TerrainSegment solid = segment(1L,
                nearCenter.subtract(halfWidth), nearCenter.add(halfWidth),
                farCenter.subtract(halfWidth), farCenter.add(halfWidth), true);
        return new TerrainSnapshot(0L, 1L, 0L, List.of(gap, solid));
    }

    private static TerrainSegment segment(long id, Vec3 nearLeft, Vec3 nearRight,
                                          Vec3 farLeft, Vec3 farRight, boolean solid) {
        return new TerrainSegment(id, nearLeft, nearRight, farLeft, farRight,
                solid, false, SurfaceProperties.NORMAL,
                TerrainVertexAppearance.DEFAULT, TerrainVertexAppearance.DEFAULT,
                TerrainVertexAppearance.DEFAULT, TerrainVertexAppearance.DEFAULT,
                Collections.<Addon>emptyList());
    }
}
