package com.example.game3d_opengl;

import static org.junit.Assert.assertTrue;

import android.view.View;

import org.junit.Test;

public class ImmersiveSystemUiTest {
    @Test
    public void legacyModeHidesAndLaysOutBehindBothSystemBars() {
        int flags = ImmersiveSystemUi.legacyFlags();

        assertContains(flags, View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        assertContains(flags, View.SYSTEM_UI_FLAG_FULLSCREEN);
        assertContains(flags, View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        assertContains(flags, View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        assertContains(flags, View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        assertContains(flags, View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private static void assertContains(int actual, int expectedFlag) {
        assertTrue((actual & expectedFlag) == expectedFlag);
    }
}
