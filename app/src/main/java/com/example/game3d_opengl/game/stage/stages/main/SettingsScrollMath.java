package com.example.game3d_opengl.game.stage.stages.main;

/** Pure coordinate helpers for the custom OpenGL settings scroller. */
final class SettingsScrollMath {
    private SettingsScrollMath() {}

    static float contentY(float screenY, float scrollOffsetPx) {
        return screenY + scrollOffsetPx;
    }

    static boolean shouldStartVerticalScroll(
            float deltaX,
            float deltaY,
            float touchSlopPx) {
        return Math.abs(deltaY) >= Math.max(0f, touchSlopPx)
                && Math.abs(deltaY) > Math.abs(deltaX);
    }

    static float scrollOffsetForDrag(
            float offsetAtTouchDown,
            float touchDownY,
            float currentY,
            float maxOffsetPx) {
        return clamp(
                offsetAtTouchDown + touchDownY - currentY,
                0f,
                Math.max(0f, maxOffsetPx)
        );
    }

    static float maxScrollOffset(
            float contentBottomPx,
            float viewportHeightPx,
            float safeBottomInsetPx,
            float bottomPaddingPx) {
        return Math.max(
                0f,
                contentBottomPx
                        + Math.max(0f, safeBottomInsetPx)
                        + Math.max(0f, bottomPaddingPx)
                        - viewportHeightPx
        );
    }

    static float clampScrollOffset(float offsetPx, float maxOffsetPx) {
        return clamp(offsetPx, 0f, Math.max(0f, maxOffsetPx));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
