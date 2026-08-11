package com.example.game3d_opengl;

import android.view.View;

/** Legacy immersive-sticky flags for Android 9 and 10. */
final class ImmersiveSystemUi {
    private ImmersiveSystemUi() {}

    static int legacyFlags() {
        return View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
    }
}
