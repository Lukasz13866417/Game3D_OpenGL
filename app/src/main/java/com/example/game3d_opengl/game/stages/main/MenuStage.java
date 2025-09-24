package com.example.game3d_opengl.game.stages.main;

import android.content.Context;

import com.example.game3d_opengl.MyGLRenderer;
import com.example.game3d_opengl.game.stage_api.Stage;

/**
 * Extremely lightweight start screen. Shows nothing visually (black screen)
 * and waits for the user to tap anywhere. The first tap switches to
 * {@link GameplayStage}. This is enough to let developers attach the
 * Android Studio Profiler before heavy rendering starts.
 */
public class MenuStage extends Stage {

    private boolean firstFrame = true;

    public MenuStage(MyGLRenderer.StageManager stageManager) {
        super(stageManager);
    }

    @Override
    public void onTouchDown(float x, float y) {
        // Start the game immediately on first touch
        // TODO make an actual menu
        stageManager.toGameplay();
    }

    @Override
    public void onTouchUp(float x, float y) {
        // No-op
    }

    @Override
    public void onTouchMove(float x1, float y1, float x2, float y2) {
        // No-op
    }

    @Override
    protected void initScene(Context context, int screenWidth, int screenHeight) {
        // Nothing to initialise for the blank menu.
        System.out.println("MENU INIT");
    }

    @Override
    public void updateThenDraw(float dt) {
        if (firstFrame) {
            firstFrame = false;
            // Optionally log so dev knows menu loaded
            android.util.Log.d("MenuStage", "Ready – tap to play");
        }
    }

    @Override
    public void onClose() {
    }

    @Override
    public void onSwitch() {
        System.out.println("SWITCHING FROM MENU");
    }

    @Override
    public void onReturn() {
        System.out.println("RETURNING TO MENU");
    }

    @Override
    public void onPause() {

    }

    @Override
    public void onResume() {

    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {

    }

    @Override
    public void cleanupGPUResourcesRecursivelyOnContextLoss() {}
}
