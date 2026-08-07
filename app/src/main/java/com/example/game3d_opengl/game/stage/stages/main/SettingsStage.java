package com.example.game3d_opengl.game.stage.stages.main;

import android.content.Context;
import android.content.res.AssetManager;
import android.opengl.GLES20;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.progress_bar.ProgressBar;
import com.example.game3d_opengl.game.settings.PortalTestSettings;
import com.example.game3d_opengl.game.settings.SlowFrameStats;
import com.example.game3d_opengl.game.settings.SlowFrameStatsSettings;
import com.example.game3d_opengl.game.settings.TouchSensitivitySettings;
import com.example.game3d_opengl.game.stage.stage_api.ReleaseButtonGesture;
import com.example.game3d_opengl.game.stage.stage_api.Stage;
import com.example.game3d_opengl.rendering.ScreenInfo;
import com.example.game3d_opengl.rendering.text.BitmapFont;
import com.example.game3d_opengl.rendering.text.Button;
import com.example.game3d_opengl.rendering.text.TextRenderer;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.rect.Rect;

/**
 * Presents gameplay and developer settings that can be changed with touch controls.
 */
public final class SettingsStage extends Stage {
    private static final int FONT_PX = 48;
    private static final float CONTENT_CLIP_TOP_PX = 174f;
    private static final float SCROLL_TOUCH_SLOP_PX = 10f;
    private static final float CONTENT_BOTTOM_PADDING_PX = 36f;

    /**
     * Identifies the button currently held by the shared release gesture.
     */
    private enum PendingButton {
        NONE,
        BACK,
        PORTAL_TOGGLE,
        SLOW_FRAME_CAPTURE,
        PRINT_SLOW_FRAMES
    }

    private enum DraggingSlider {
        NONE,
        HORIZONTAL,
        VERTICAL
    }

    private TextRenderer textRenderer;
    private TextRenderer headerTextRenderer;
    private TextRenderer.TextLabel titleLabel;
    private TextRenderer.TextLabel gameplaySectionLabel;
    private TextRenderer.TextLabel developerSectionLabel;
    private TextRenderer.TextLabel horizontalSensitivityLabel;
    private TextRenderer.TextLabel horizontalValueLabel;
    private TextRenderer.TextLabel horizontalMinLabel;
    private TextRenderer.TextLabel horizontalMaxLabel;
    private TextRenderer.TextLabel verticalSensitivityLabel;
    private TextRenderer.TextLabel verticalValueLabel;
    private TextRenderer.TextLabel verticalMinLabel;
    private TextRenderer.TextLabel verticalMaxLabel;
    private TextRenderer.TextLabel hintLabel;
    private TextRenderer.TextLabel slowFrameCaptureLabel;
    private TextRenderer.TextLabel slowFrameStatsCountLabel;
    private Button portalToggleButton;
    private Button developerSeparator;
    private Button slowFrameCaptureCheckboxButton;
    private Button printSlowFrameStatsButton;
    private Rect portalToggleRectPx;
    private Rect slowFrameCaptureCheckboxRectPx;
    private Rect slowFrameCaptureTouchRectPx;
    private Rect printSlowFrameStatsRectPx;
    private ProgressBar horizontalSensitivitySlider;
    private ProgressBar verticalSensitivitySlider;
    private Button backButton;
    private Rect backButtonRectPx;
    private Rect horizontalSliderRectPx;
    private Rect horizontalSliderTouchRectPx;
    private Rect verticalSliderRectPx;
    private Rect verticalSliderTouchRectPx;
    private final ReleaseButtonGesture buttonGesture =
            new ReleaseButtonGesture();
    private PendingButton pendingButton = PendingButton.NONE;
    private DraggingSlider draggingSlider = DraggingSlider.NONE;
    private float uiScale = 1f;
    private int screenWidthPx;
    private int screenHeightPx;
    private float contentClipTopPx;
    private float contentBottomPx;
    private float scrollOffsetPx;
    private float maxScrollOffsetPx;
    private float touchDownX;
    private float touchDownY;
    private float scrollOffsetAtTouchDown;
    private boolean contentGesture;
    private boolean scrolling;

    public SettingsStage(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
    }

    @Override
    protected void onTouchDown(float x, float y) {
        resetContentGesture();
        cancelButtonGesture();
        if (buttonGesture.begin(backButtonRectPx, x, y)) {
            pendingButton = PendingButton.BACK;
            return;
        }
        if (!isInsideScrollableViewport(y)) {
            return;
        }

        contentGesture = true;
        touchDownX = x;
        touchDownY = y;
        scrollOffsetAtTouchDown = scrollOffsetPx;
        float contentY = SettingsScrollMath.contentY(y, scrollOffsetPx);
        if (horizontalSliderTouchRectPx != null
                && horizontalSliderTouchRectPx.containsPoint(x, contentY)) {
            draggingSlider = DraggingSlider.HORIZONTAL;
            return;
        }
        if (verticalSliderTouchRectPx != null
                && verticalSliderTouchRectPx.containsPoint(x, contentY)) {
            draggingSlider = DraggingSlider.VERTICAL;
            return;
        }
        if (buttonGesture.begin(portalToggleRectPx, x, contentY)) {
            pendingButton = PendingButton.PORTAL_TOGGLE;
            return;
        }
        if (buttonGesture.begin(
                slowFrameCaptureTouchRectPx, x, contentY)) {
            pendingButton = PendingButton.SLOW_FRAME_CAPTURE;
            return;
        }
        if (buttonGesture.begin(printSlowFrameStatsRectPx, x, contentY)) {
            pendingButton = PendingButton.PRINT_SLOW_FRAMES;
        }
    }

    @Override
    protected void onTouchUp(float x, float y) {
        if (contentGesture) {
            if (scrolling) {
                cancelButtonGesture();
                resetContentGesture();
                return;
            }
            if (draggingSlider != DraggingSlider.NONE) {
                updateDraggedSensitivityFromTouchX(x);
                cancelButtonGesture();
                resetContentGesture();
                return;
            }
            PendingButton releasedButton = pendingButton;
            pendingButton = PendingButton.NONE;
            float contentY = SettingsScrollMath.contentY(y, scrollOffsetPx);
            if (buttonGesture.release(x, contentY)) {
                activateButton(releasedButton);
            }
            resetContentGesture();
            return;
        }
        PendingButton releasedButton = pendingButton;
        pendingButton = PendingButton.NONE;
        if (buttonGesture.release(x, y)) {
            activateButton(releasedButton);
        }
    }

    @Override
    protected void onTouchMove(float x1, float y1, float x2, float y2) {
        if (!contentGesture) {
            buttonGesture.move(x2, y2);
            return;
        }

        float deltaX = x2 - touchDownX;
        float deltaY = y2 - touchDownY;
        if (!scrolling && SettingsScrollMath.shouldStartVerticalScroll(
                deltaX,
                deltaY,
                SCROLL_TOUCH_SLOP_PX * uiScale)) {
            scrolling = true;
            draggingSlider = DraggingSlider.NONE;
            cancelButtonGesture();
        }
        if (scrolling) {
            scrollOffsetPx = SettingsScrollMath.scrollOffsetForDrag(
                    scrollOffsetAtTouchDown,
                    touchDownY,
                    y2,
                    maxScrollOffsetPx
            );
        } else if (draggingSlider != DraggingSlider.NONE) {
            updateDraggedSensitivityFromTouchX(x2);
        } else {
            buttonGesture.move(
                    x2,
                    SettingsScrollMath.contentY(y2, scrollOffsetPx)
            );
        }
    }

    @Override
    protected void onTouchCancelTimed(
            float x, float y, long timeNanos, long sequence) {
        resetContentGesture();
        cancelButtonGesture();
    }

    @Override
    protected void setupAssets(AssetManager assetManager) {
        // No-op.
    }

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        screenWidthPx = screenWidth;
        screenHeightPx = screenHeight;
        uiScale = screenHeight / 1080f;
        contentClipTopPx = CONTENT_CLIP_TOP_PX * uiScale;
        scrollOffsetPx = 0f;
        BitmapFont font = BitmapFont.loadShared(context.getAssets(), FONT_PX);
        textRenderer = new TextRenderer(font);
        headerTextRenderer = new TextRenderer(font);

        float padding = 24f * uiScale;
        float backButtonW = 168f * uiScale;
        float backButtonH = 46f * uiScale;
        backButtonRectPx = new Rect(
                padding,
                padding,
                padding + backButtonW,
                padding + backButtonH
        );
        backButton = new Button.Builder()
                .bboxPx(backButtonRectPx)
                .text("Back")
                .textScale(0.90f * uiScale)
                .autoFitText(12f * uiScale, 6f * uiScale, 0.60f)
                .textRenderer(headerTextRenderer)
                .fillColor(FColor.CLR(0.14f, 0.14f, 0.14f, 1f))
                .outlineColor(FColor.CLR(1f, 1f, 1f, 1f))
                .textColor(FColor.CLR(1f, 1f, 1f, 1f))
                .edgePixels(Math.max(1f, 1.2f * uiScale))
                .build();

        float contentMarginX = 96f * uiScale;
        float contentW = Math.max(240f * uiScale, screenWidth - 2f * contentMarginX);
        float contentX = 0.5f * (screenWidth - contentW);
        float sliderH = 42f * uiScale;
        float horizontalSliderY = screenHeight * 0.30f;
        horizontalSliderRectPx = new Rect(
                contentX,
                horizontalSliderY,
                contentX + contentW,
                horizontalSliderY + sliderH
        );
        horizontalSliderTouchRectPx = expandedSliderTouchRect(
                horizontalSliderRectPx);
        float verticalSliderY = horizontalSliderRectPx.y2 + 148f * uiScale;
        verticalSliderRectPx = new Rect(
                contentX,
                verticalSliderY,
                contentX + contentW,
                verticalSliderY + sliderH
        );
        verticalSliderTouchRectPx = expandedSliderTouchRect(
                verticalSliderRectPx);

        horizontalSensitivitySlider = new ProgressBar.Builder()
                .range(
                        TouchSensitivitySettings.MIN_SENSITIVITY,
                        TouchSensitivitySettings.MAX_SENSITIVITY
                )
                .bboxPx(
                        horizontalSliderRectPx.x1,
                        horizontalSliderRectPx.y1,
                        horizontalSliderRectPx.w,
                        horizontalSliderRectPx.h
                )
                .fillColor(FColor.CLR(0.18f, 0.60f, 0.92f, 1f))
                .outlineColor(FColor.CLR(1f, 1f, 1f, 1f))
                .milestones(new float[]{TouchSensitivitySettings.DEFAULT_SENSITIVITY})
                .dentSizePx(10f * uiScale, 10f * uiScale)
                .outlinePixels(Math.max(1f, 1.2f * uiScale))
                .build();
        verticalSensitivitySlider = new ProgressBar.Builder()
                .range(
                        TouchSensitivitySettings.MIN_SENSITIVITY,
                        TouchSensitivitySettings.MAX_SENSITIVITY
                )
                .bboxPx(
                        verticalSliderRectPx.x1,
                        verticalSliderRectPx.y1,
                        verticalSliderRectPx.w,
                        verticalSliderRectPx.h
                )
                .fillColor(FColor.CLR(0.42f, 0.48f, 0.94f, 1f))
                .outlineColor(FColor.CLR(1f, 1f, 1f, 1f))
                .milestones(new float[]{TouchSensitivitySettings.DEFAULT_SENSITIVITY})
                .dentSizePx(10f * uiScale, 10f * uiScale)
                .outlinePixels(Math.max(1f, 1.2f * uiScale))
                .build();

        float toggleButtonW = Math.max(240f * uiScale, contentW * 0.58f);
        float toggleButtonH = 46f * uiScale;
        float toggleButtonX = 0.5f * (screenWidth - toggleButtonW);
        float portalButtonY = verticalSliderRectPx.y2 + 120f * uiScale;
        portalToggleRectPx = new Rect(
                toggleButtonX,
                portalButtonY,
                toggleButtonX + toggleButtonW,
                portalButtonY + toggleButtonH
        );
        portalToggleButton = new Button.Builder()
                .bboxPx(portalToggleRectPx)
                .text("")
                .textScale(0.82f * uiScale)
                .autoFitText(14f * uiScale, 8f * uiScale, 0.55f)
                .textRenderer(textRenderer)
                .fillColor(FColor.CLR(0.14f, 0.14f, 0.14f, 1f))
                .outlineColor(FColor.CLR(1f, 1f, 1f, 1f))
                .textColor(FColor.CLR(1f, 1f, 1f, 1f))
                .edgePixels(Math.max(1f, 1.2f * uiScale))
                .build();

        float separatorY = portalToggleRectPx.y2 + 68f * uiScale;
        Rect developerSeparatorRectPx = new Rect(
                contentX,
                separatorY,
                contentX + contentW,
                separatorY + Math.max(2f, 2f * uiScale)
        );
        developerSeparator = new Button.Builder()
                .bboxPx(developerSeparatorRectPx)
                .text("")
                .textScale(0.01f)
                .textRenderer(textRenderer)
                .fillColor(FColor.CLR(0.30f, 0.30f, 0.30f, 1f))
                .outlineColor(FColor.CLR(0.30f, 0.30f, 0.30f, 1f))
                .textColor(FColor.CLR(0.30f, 0.30f, 0.30f, 1f))
                .edgePixels(Math.max(1f, 1.0f * uiScale))
                .build();

        float checkboxSize = 40f * uiScale;
        float slowFrameCaptureLabelY = separatorY + 78f * uiScale;
        float checkboxX = 0.5f * (screenWidth - checkboxSize);
        float checkboxY = slowFrameCaptureLabelY + 52f * uiScale;
        slowFrameCaptureCheckboxRectPx = new Rect(
                checkboxX,
                checkboxY,
                checkboxX + checkboxSize,
                checkboxY + checkboxSize
        );
        slowFrameCaptureTouchRectPx = new Rect(
                contentX,
                slowFrameCaptureLabelY - 12f * uiScale,
                contentX + contentW,
                checkboxY + checkboxSize + 12f * uiScale
        );
        slowFrameCaptureCheckboxButton = new Button.Builder()
                .bboxPx(slowFrameCaptureCheckboxRectPx)
                .text("")
                .textScale(0.78f * uiScale)
                .textRenderer(textRenderer)
                .fillColor(FColor.CLR(0.14f, 0.14f, 0.14f, 1f))
                .outlineColor(FColor.CLR(1f, 1f, 1f, 1f))
                .textColor(FColor.CLR(1f, 1f, 1f, 1f))
                .edgePixels(Math.max(1f, 1.2f * uiScale))
                .build();

        float printButtonW = Math.max(260f * uiScale, contentW * 0.62f);
        float printButtonH = 46f * uiScale;
        float printButtonX = 0.5f * (screenWidth - printButtonW);
        float printButtonY = checkboxY + checkboxSize + 104f * uiScale;
        printSlowFrameStatsRectPx = new Rect(
                printButtonX,
                printButtonY,
                printButtonX + printButtonW,
                printButtonY + printButtonH
        );
        printSlowFrameStatsButton = new Button.Builder()
                .bboxPx(printSlowFrameStatsRectPx)
                .text("Print Slow Frame Stats")
                .textScale(0.78f * uiScale)
                .autoFitText(16f * uiScale, 8f * uiScale, 0.45f)
                .textRenderer(textRenderer)
                .fillColor(FColor.CLR(0.14f, 0.14f, 0.14f, 1f))
                .outlineColor(FColor.CLR(1f, 1f, 1f, 1f))
                .textColor(FColor.CLR(1f, 1f, 1f, 1f))
                .edgePixels(Math.max(1f, 1.2f * uiScale))
                .build();

        contentBottomPx = printSlowFrameStatsRectPx.y2;
        refreshScrollBounds();

        titleLabel = headerTextRenderer
                .createLabel("Settings", screenWidth * 0.5f, 90f * uiScale, FColor.CLR(1f, 1f, 1f, 1f))
                .setAnchor(TextRenderer.Anchor.TOP_CENTER)
                .setScale(1.12f * uiScale);
        gameplaySectionLabel = textRenderer
                .createLabel(
                        "Gameplay",
                        screenWidth * 0.5f,
                        horizontalSliderRectPx.y1 - 110f * uiScale,
                        FColor.CLR(0.88f, 0.88f, 0.88f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.BOTTOM_CENTER)
                .setScale(0.78f * uiScale);
        horizontalSensitivityLabel = textRenderer
                .createLabel(
                        "Horizontal Sensitivity",
                        screenWidth * 0.5f,
                        horizontalSliderRectPx.y1 - 20f * uiScale,
                        FColor.CLR(1f, 1f, 1f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.BOTTOM_CENTER)
                .setScale(0.82f * uiScale);
        horizontalValueLabel = textRenderer
                .createLabel(
                        "",
                        screenWidth * 0.5f,
                        horizontalSliderRectPx.y2 + 20f * uiScale,
                        FColor.CLR(1f, 1f, 1f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.TOP_CENTER)
                .setScale(0.76f * uiScale);
        horizontalMinLabel = textRenderer
                .createLabel(
                        formatSensitivity(TouchSensitivitySettings.MIN_SENSITIVITY),
                        horizontalSliderRectPx.x1,
                        horizontalSliderRectPx.y2 + 20f * uiScale,
                        FColor.CLR(0.85f, 0.85f, 0.85f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.TOP_LEFT)
                .setScale(0.64f * uiScale);
        horizontalMaxLabel = textRenderer
                .createLabel(
                        formatSensitivity(TouchSensitivitySettings.MAX_SENSITIVITY),
                        horizontalSliderRectPx.x2,
                        horizontalSliderRectPx.y2 + 20f * uiScale,
                        FColor.CLR(0.85f, 0.85f, 0.85f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.TOP_RIGHT)
                .setScale(0.64f * uiScale);
        verticalSensitivityLabel = textRenderer
                .createLabel(
                        "Vertical Sensitivity",
                        screenWidth * 0.5f,
                        verticalSliderRectPx.y1 - 20f * uiScale,
                        FColor.CLR(1f, 1f, 1f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.BOTTOM_CENTER)
                .setScale(0.82f * uiScale);
        verticalValueLabel = textRenderer
                .createLabel(
                        "",
                        screenWidth * 0.5f,
                        verticalSliderRectPx.y2 + 20f * uiScale,
                        FColor.CLR(1f, 1f, 1f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.TOP_CENTER)
                .setScale(0.76f * uiScale);
        verticalMinLabel = textRenderer
                .createLabel(
                        formatSensitivity(TouchSensitivitySettings.MIN_SENSITIVITY),
                        verticalSliderRectPx.x1,
                        verticalSliderRectPx.y2 + 20f * uiScale,
                        FColor.CLR(0.85f, 0.85f, 0.85f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.TOP_LEFT)
                .setScale(0.64f * uiScale);
        verticalMaxLabel = textRenderer
                .createLabel(
                        formatSensitivity(TouchSensitivitySettings.MAX_SENSITIVITY),
                        verticalSliderRectPx.x2,
                        verticalSliderRectPx.y2 + 20f * uiScale,
                        FColor.CLR(0.85f, 0.85f, 0.85f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.TOP_RIGHT)
                .setScale(0.64f * uiScale);
        hintLabel = textRenderer
                .createLabel(
                        "Drag either bar to adjust",
                        screenWidth * 0.5f,
                        verticalSliderRectPx.y2 + 68f * uiScale,
                        FColor.CLR(0.80f, 0.80f, 0.80f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.TOP_CENTER)
                .setScale(0.64f * uiScale);
        developerSectionLabel = textRenderer
                .createLabel(
                        "Developer",
                        screenWidth * 0.5f,
                        separatorY + 26f * uiScale,
                        FColor.CLR(0.88f, 0.88f, 0.88f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.TOP_CENTER)
                .setScale(0.78f * uiScale);
        slowFrameCaptureLabel = textRenderer
                .createLabel(
                        "Capture Slow Frame Stats",
                        screenWidth * 0.5f,
                        slowFrameCaptureLabelY,
                        FColor.CLR(1f, 1f, 1f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.TOP_CENTER)
                .setScale(0.74f * uiScale);
        slowFrameStatsCountLabel = textRenderer
                .createLabel(
                        "",
                        screenWidth * 0.5f,
                        slowFrameCaptureCheckboxRectPx.y2 + 26f * uiScale,
                        FColor.CLR(0.80f, 0.80f, 0.80f, 1f)
                )
                .setAnchor(TextRenderer.Anchor.TOP_CENTER)
                .setScale(0.68f * uiScale);
        syncSensitivityValueLabels();
        syncPortalToggleButton();
        syncSlowFrameCaptureCheckbox();
        syncSlowFrameStatsCountLabel();
    }

    @Override
    public void updateThenDraw(float dt) {
        refreshScrollBounds();
        processTouchEvents();
        syncSensitivityValueLabels();
        syncPortalToggleButton();
        syncSlowFrameCaptureCheckbox();
        syncSlowFrameStatsCountLabel();

        beginScrollableContentDraw();
        if (horizontalSensitivitySlider != null) {
            horizontalSensitivitySlider.draw(
                    TouchSensitivitySettings.getHorizontalSensitivity());
        }
        if (verticalSensitivitySlider != null) {
            verticalSensitivitySlider.draw(
                    TouchSensitivitySettings.getVerticalSensitivity());
        }
        if (portalToggleButton != null) {
            portalToggleButton.draw();
        }
        if (developerSeparator != null) {
            developerSeparator.draw();
        }
        if (slowFrameCaptureCheckboxButton != null) {
            slowFrameCaptureCheckboxButton.draw();
        }
        if (printSlowFrameStatsButton != null) {
            printSlowFrameStatsButton.draw();
        }
        if (textRenderer != null) {
            textRenderer.draw();
        }
        endScrollableContentDraw();

        if (backButton != null) {
            backButton.draw();
        }
        if (headerTextRenderer != null) {
            headerTextRenderer.draw();
        }
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (horizontalSensitivitySlider != null) {
            horizontalSensitivitySlider.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (verticalSensitivitySlider != null) {
            verticalSensitivitySlider.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (backButton != null) {
            backButton.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (portalToggleButton != null) {
            portalToggleButton.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (developerSeparator != null) {
            developerSeparator.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (slowFrameCaptureCheckboxButton != null) {
            slowFrameCaptureCheckboxButton.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (printSlowFrameStatsButton != null) {
            printSlowFrameStatsButton.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (textRenderer != null) {
            textRenderer.reloadGPUResourcesRecursivelyOnContextLoss();
        }
        if (headerTextRenderer != null) {
            headerTextRenderer.reloadGPUResourcesRecursivelyOnContextLoss();
        }
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (horizontalSensitivitySlider != null) {
            horizontalSensitivitySlider.cleanupGPUResourcesRecursively();
        }
        if (verticalSensitivitySlider != null) {
            verticalSensitivitySlider.cleanupGPUResourcesRecursively();
        }
        if (backButton != null) {
            backButton.cleanupGPUResourcesRecursively();
        }
        if (portalToggleButton != null) {
            portalToggleButton.cleanupGPUResourcesRecursively();
        }
        if (developerSeparator != null) {
            developerSeparator.cleanupGPUResourcesRecursively();
        }
        if (slowFrameCaptureCheckboxButton != null) {
            slowFrameCaptureCheckboxButton.cleanupGPUResourcesRecursively();
        }
        if (printSlowFrameStatsButton != null) {
            printSlowFrameStatsButton.cleanupGPUResourcesRecursively();
        }
        if (textRenderer != null) {
            textRenderer.cleanupGPUResourcesRecursively();
        }
        if (headerTextRenderer != null) {
            headerTextRenderer.cleanupGPUResourcesRecursively();
        }
    }

    @Override
    protected void releaseOwnedResourcesOnDiscard() {
        cancelButtonGesture();
        textRenderer = null;
        headerTextRenderer = null;
        titleLabel = null;
        gameplaySectionLabel = null;
        developerSectionLabel = null;
        horizontalSensitivityLabel = null;
        horizontalValueLabel = null;
        horizontalMinLabel = null;
        horizontalMaxLabel = null;
        verticalSensitivityLabel = null;
        verticalValueLabel = null;
        verticalMinLabel = null;
        verticalMaxLabel = null;
        hintLabel = null;
        slowFrameCaptureLabel = null;
        slowFrameStatsCountLabel = null;
        horizontalSensitivitySlider = null;
        verticalSensitivitySlider = null;
        backButton = null;
        portalToggleButton = null;
        developerSeparator = null;
        slowFrameCaptureCheckboxButton = null;
        printSlowFrameStatsButton = null;
        backButtonRectPx = null;
        portalToggleRectPx = null;
        slowFrameCaptureCheckboxRectPx = null;
        slowFrameCaptureTouchRectPx = null;
        printSlowFrameStatsRectPx = null;
        horizontalSliderRectPx = null;
        horizontalSliderTouchRectPx = null;
        verticalSliderRectPx = null;
        verticalSliderTouchRectPx = null;
        draggingSlider = DraggingSlider.NONE;
        uiScale = 1f;
        screenWidthPx = 0;
        screenHeightPx = 0;
        contentClipTopPx = 0f;
        contentBottomPx = 0f;
        scrollOffsetPx = 0f;
        maxScrollOffsetPx = 0f;
        resetContentGesture();
    }

    @Override
    protected void onPause() {
        resetContentGesture();
        cancelButtonGesture();
    }

    @Override
    protected void onResume() {
    }

    private void activateButton(PendingButton button) {
        switch (button) {
            case BACK:
                stageManager.pop();
                break;
            case PORTAL_TOGGLE:
                PortalTestSettings.toggleTestPortalEnabled();
                syncPortalToggleButton();
                break;
            case SLOW_FRAME_CAPTURE:
                SlowFrameStatsSettings.toggleCaptureEnabled();
                syncSlowFrameCaptureCheckbox();
                syncSlowFrameStatsCountLabel();
                break;
            case PRINT_SLOW_FRAMES:
                SlowFrameStats.dumpToStdout();
                syncSlowFrameStatsCountLabel();
                break;
            case NONE:
            default:
                break;
        }
    }

    private void cancelButtonGesture() {
        buttonGesture.cancel();
        pendingButton = PendingButton.NONE;
    }

    private void beginScrollableContentDraw() {
        int width = Math.max(
                1,
                screenWidthPx > 0 ? screenWidthPx : ScreenInfo.getScreenW()
        );
        int height = Math.max(
                1,
                screenHeightPx > 0 ? screenHeightPx : ScreenInfo.getScreenH()
        );
        int clipTop = Math.max(
                0,
                Math.min(height, Math.round(contentClipTopPx))
        );
        int safeBottom = safeBottomInsetPx(height);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(
                0,
                safeBottom,
                width,
                Math.max(0, height - clipTop - safeBottom)
        );
        GLES20.glViewport(0, Math.round(scrollOffsetPx), width, height);
    }

    private void endScrollableContentDraw() {
        int width = Math.max(
                1,
                screenWidthPx > 0 ? screenWidthPx : ScreenInfo.getScreenW()
        );
        int height = Math.max(
                1,
                screenHeightPx > 0 ? screenHeightPx : ScreenInfo.getScreenH()
        );
        GLES20.glViewport(0, 0, width, height);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    private boolean isInsideScrollableViewport(float yPx) {
        return yPx >= contentClipTopPx
                && yPx <= screenHeightPx - safeBottomInsetPx(screenHeightPx);
    }

    private void refreshScrollBounds() {
        maxScrollOffsetPx = SettingsScrollMath.maxScrollOffset(
                contentBottomPx,
                screenHeightPx,
                safeBottomInsetPx(screenHeightPx),
                CONTENT_BOTTOM_PADDING_PX * uiScale
        );
        scrollOffsetPx = SettingsScrollMath.clampScrollOffset(
                scrollOffsetPx,
                maxScrollOffsetPx
        );
    }

    private static int safeBottomInsetPx(int screenHeightPx) {
        return Math.max(
                0,
                Math.min(screenHeightPx, ScreenInfo.getSafeInsetBottom())
        );
    }

    private void resetContentGesture() {
        draggingSlider = DraggingSlider.NONE;
        contentGesture = false;
        scrolling = false;
        touchDownX = 0f;
        touchDownY = 0f;
        scrollOffsetAtTouchDown = scrollOffsetPx;
    }

    private Rect expandedSliderTouchRect(Rect sliderRectPx) {
        return new Rect(
                sliderRectPx.x1,
                sliderRectPx.y1 - 24f * uiScale,
                sliderRectPx.x2,
                sliderRectPx.y2 + 24f * uiScale
        );
    }

    private void updateDraggedSensitivityFromTouchX(float xPx) {
        if (draggingSlider == DraggingSlider.HORIZONTAL) {
            updateHorizontalSensitivityFromTouchX(xPx);
        } else if (draggingSlider == DraggingSlider.VERTICAL) {
            updateVerticalSensitivityFromTouchX(xPx);
        }
    }

    private void updateHorizontalSensitivityFromTouchX(float xPx) {
        TouchSensitivitySettings.setHorizontalSensitivity(
                sensitivityFromTouchX(horizontalSliderRectPx, xPx)
        );
        syncSensitivityValueLabels();
    }

    private void updateVerticalSensitivityFromTouchX(float xPx) {
        TouchSensitivitySettings.setVerticalSensitivity(
                sensitivityFromTouchX(verticalSliderRectPx, xPx)
        );
        syncSensitivityValueLabels();
    }

    private static float sensitivityFromTouchX(Rect sliderRectPx, float xPx) {
        if (sliderRectPx == null || sliderRectPx.w <= 1e-6f) {
            return TouchSensitivitySettings.DEFAULT_SENSITIVITY;
        }
        float normalized = (xPx - sliderRectPx.x1) / sliderRectPx.w;
        return TouchSensitivitySettings.fromNormalized(normalized);
    }

    private void syncSensitivityValueLabels() {
        if (horizontalValueLabel != null) {
            horizontalValueLabel.setText(formatSensitivity(
                    TouchSensitivitySettings.getHorizontalSensitivity()));
        }
        if (verticalValueLabel != null) {
            verticalValueLabel.setText(formatSensitivity(
                    TouchSensitivitySettings.getVerticalSensitivity()));
        }
    }

    private void syncPortalToggleButton() {
        if (portalToggleButton != null) {
            portalToggleButton.setText(
                    PortalTestSettings.isTestPortalEnabled()
                            ? "Test Portal: On"
                            : "Test Portal: Off"
            );
        }
    }

    private void syncSlowFrameCaptureCheckbox() {
        if (slowFrameCaptureCheckboxButton != null) {
            slowFrameCaptureCheckboxButton.setText(
                    SlowFrameStatsSettings.isCaptureEnabled() ? "X" : ""
            );
        }
    }

    private void syncSlowFrameStatsCountLabel() {
        if (slowFrameStatsCountLabel != null) {
            slowFrameStatsCountLabel.setText(
                    "Stored Slow Frames: " + SlowFrameStats.getStoredRecordCount()
            );
        }
    }

    private static String formatSensitivity(float sensitivity) {
        String formatted = String.format(java.util.Locale.US, "%.2f", sensitivity);
        int end = formatted.length();
        while (end > 0 && formatted.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && formatted.charAt(end - 1) == '.') {
            end--;
        }
        return formatted.substring(0, end);
    }
}
