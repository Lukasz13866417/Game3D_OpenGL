package com.example.game3d_opengl;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;

import com.example.game3d_opengl.screenshot.ScreenshotCaptureController;
import com.example.game3d_opengl.rendering.ScreenInfo;

public class OpenGLES20Activity extends Activity {
    private MyGLSurfaceView glSurfaceView;
    private ScreenshotCaptureController screenshotCaptureController;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        glSurfaceView = new MyGLSurfaceView(this);
        screenshotCaptureController = new ScreenshotCaptureController(this);

        FrameLayout root = new FrameLayout(this);
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            applySafeInsets(windowInsets);
            glSurfaceView.requestRender();
            return windowInsets;
        });
        root.addView(
                glSurfaceView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        Button screenshotButton = makeScreenshotButton();
        FrameLayout.LayoutParams screenshotLayout = new FrameLayout.LayoutParams(
                dp(54f), dp(48f), Gravity.TOP | Gravity.END);
        // The OpenGL HUD owns the first two rows at the top-right. Keep this Android overlay
        // below them so it never obscures pause/settings while still remaining thumb-accessible.
        screenshotLayout.topMargin = dp(112f);
        screenshotLayout.rightMargin = dp(12f);
        root.addView(screenshotButton, screenshotLayout);

        screenshotButton.setOnClickListener(ignored ->
                screenshotCaptureController.capture(
                        glSurfaceView, screenshotButton));
        setContentView(root);
        enterImmersiveMode();
        // setContentView creates the DecorView, but its insets controller may not be attached
        // until this first queued UI pass. Retry once so the bars are hidden on the first frame.
        root.post(this::enterImmersiveMode);
        root.requestApplyInsets();
    }

    private void enterImmersiveMode() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            // Some Android framework builds dereference PhoneWindow's DecorView inside
            // Window#getInsetsController(). During early activity creation that DecorView can
            // still be null, which crashes instead of returning a null controller. Read the
            // already-created view directly and let the later onResume/focus callbacks retry if
            // it is not ready yet.
            View decorView = window.peekDecorView();
            if (decorView == null) {
                return;
            }
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController
                                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                );
            }
            return;
        }
        View decorView = window.getDecorView();
        decorView.setSystemUiVisibility(ImmersiveSystemUi.legacyFlags());
    }

    private static void applySafeInsets(WindowInsets windowInsets) {
        if (windowInsets == null) {
            ScreenInfo.setSafeInsets(0, 0, 0, 0);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets safeInsets = windowInsets.getInsets(
                    WindowInsets.Type.navigationBars()
                            | WindowInsets.Type.displayCutout()
            );
            ScreenInfo.setSafeInsets(
                    safeInsets.left,
                    safeInsets.top,
                    safeInsets.right,
                    safeInsets.bottom
            );
            return;
        }
        ScreenInfo.setSafeInsets(
                windowInsets.getSystemWindowInsetLeft(),
                windowInsets.getSystemWindowInsetTop(),
                windowInsets.getSystemWindowInsetRight(),
                windowInsets.getSystemWindowInsetBottom()
        );
    }

    private Button makeScreenshotButton() {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(R.string.screenshot_button_label);
        button.setContentDescription(getString(
                R.string.screenshot_button_description));
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(4f), 0, dp(4f), 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setElevation(dp(3f));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(205, 10, 14, 20));
        background.setCornerRadius(dp(5f));
        background.setStroke(Math.max(1, dp(1f)), Color.WHITE);
        button.setBackground(background);
        return button;
    }

    private int dp(float value) {
        return Math.round(value * getResources()
                .getDisplayMetrics().density);
    }


    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.onPause();
        if (glSurfaceView.getRenderer().getCurrentStage() != null) {
            glSurfaceView.getRenderer().getCurrentStage().pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        glSurfaceView.onResume();
        if (glSurfaceView.getRenderer().getCurrentStage() != null) {
            glSurfaceView.getRenderer().getCurrentStage().resume();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
        }
    }

    @Override
    protected void onDestroy() {
        if (screenshotCaptureController != null) {
            screenshotCaptureController.close();
            screenshotCaptureController = null;
        }
        super.onDestroy();
    }
}
