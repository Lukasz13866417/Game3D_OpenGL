package com.example.game3d_opengl.game.stage.stages.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SettingsScrollMathTest {
    private static final float EPSILON = 1e-6f;

    @Test
    public void contentHitCoordinateIsInverseOfVisualScroll() {
        assertEquals(430f, SettingsScrollMath.contentY(310f, 120f), EPSILON);
    }

    @Test
    public void verticalIntentMustBeatTouchSlopAndHorizontalMotion() {
        assertFalse(SettingsScrollMath.shouldStartVerticalScroll(1f, 7f, 8f));
        assertFalse(SettingsScrollMath.shouldStartVerticalScroll(12f, 10f, 8f));
        assertTrue(SettingsScrollMath.shouldStartVerticalScroll(4f, -10f, 8f));
    }

    @Test
    public void upwardFingerDragScrollsDownAndClampsAtContentEnd() {
        assertEquals(
                70f,
                SettingsScrollMath.scrollOffsetForDrag(20f, 300f, 250f, 100f),
                EPSILON
        );
        assertEquals(
                100f,
                SettingsScrollMath.scrollOffsetForDrag(80f, 300f, 100f, 100f),
                EPSILON
        );
    }

    @Test
    public void downwardFingerDragClampsAtContentStart() {
        assertEquals(
                0f,
                SettingsScrollMath.scrollOffsetForDrag(10f, 300f, 400f, 100f),
                EPSILON
        );
    }

    @Test
    public void maximumOffsetReservesSystemNavigationAndBottomPadding() {
        assertEquals(
                176f,
                SettingsScrollMath.maxScrollOffset(1_100f, 1_080f, 120f, 36f),
                EPSILON
        );
    }

    @Test
    public void existingOffsetIsClampedWhenSafeAreaChanges() {
        assertEquals(
                140f,
                SettingsScrollMath.clampScrollOffset(220f, 140f),
                EPSILON
        );
        assertEquals(
                0f,
                SettingsScrollMath.clampScrollOffset(-20f, 140f),
                EPSILON
        );
    }
}
