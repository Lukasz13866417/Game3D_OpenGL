package com.example.game3d_opengl.rendering;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

public class ScreenInfoTest {
    @After
    public void clearInsets() {
        ScreenInfo.setSafeInsets(0, 0, 0, 0);
    }

    @Test
    public void safeInsetsAreStoredAndNegativeValuesAreClamped() {
        ScreenInfo.setSafeInsets(-1, 12, 34, 96);

        assertEquals(0, ScreenInfo.getSafeInsetLeft());
        assertEquals(12, ScreenInfo.getSafeInsetTop());
        assertEquals(34, ScreenInfo.getSafeInsetRight());
        assertEquals(96, ScreenInfo.getSafeInsetBottom());
    }
}
