package com.example.game3d_opengl.rendering;

public class ScreenInfo {
    private static int screenW = -1, screenH = -1;
    private static volatile int safeInsetLeft;
    private static volatile int safeInsetTop;
    private static volatile int safeInsetRight;
    private static volatile int safeInsetBottom;

    public static void setScreenSize(int w, int h){
        assert w > 0;
        assert h > 0;
        screenW = w;
        screenH = h;
    }

    public static int getScreenW(){
        assert screenW != -1 && screenH != -1;
        return screenW;
    }

    public static int getScreenH(){
        assert screenW != -1 && screenH != -1;
        return screenH;
    }

    /** Called from the Android UI thread when system-bar or cutout insets change. */
    public static void setSafeInsets(int left, int top, int right, int bottom) {
        safeInsetLeft = Math.max(0, left);
        safeInsetTop = Math.max(0, top);
        safeInsetRight = Math.max(0, right);
        safeInsetBottom = Math.max(0, bottom);
    }

    public static int getSafeInsetLeft() {
        return safeInsetLeft;
    }

    public static int getSafeInsetTop() {
        return safeInsetTop;
    }

    public static int getSafeInsetRight() {
        return safeInsetRight;
    }

    public static int getSafeInsetBottom() {
        return safeInsetBottom;
    }
}
