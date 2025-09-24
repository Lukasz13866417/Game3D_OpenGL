package com.example.game3d_opengl.game.stage_api;


import android.content.Context;
import android.content.res.AssetManager;

import com.example.game3d_opengl.MyGLRenderer.StageManager;
import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.infill.InfillShaderPair;
import com.example.game3d_opengl.rendering.wireframe.WireframeShaderPair;


public abstract class Stage implements GPUResourceOwner {

    public Stage(StageManager stageManager){
        this.stageManager = stageManager;
    }
    public abstract void onTouchDown(float x, float y);

    public abstract void onTouchUp(float x, float y);

    public abstract void onTouchMove(float x1, float y1, float x2, float y2);

    protected abstract void initScene(Context context, int screenWidth, int screenHeight);

    public final void init(Context context, int screenWidth, int screenHeight){
        AssetManager assetManager = context.getAssets();
        InfillShaderPair.LOAD_SHADER_CODE(assetManager);
        WireframeShaderPair.LOAD_SHADER_CODE(assetManager);
        initScene(context, screenWidth, screenHeight);
    }

    public abstract void updateThenDraw(float dt);

    public abstract void onClose();

    public abstract void onSwitch();

    public abstract void onReturn();

    private boolean is_paused = false;

    /**
     * Called when the application is paused. The default implementation marks the stage as paused.
     * Subclasses can override this to save state or pause expensive operations like sound.
     */
    public void pause() {
        onPause();
        this.is_paused = true;
    }

    /**
     * Called when the application is resumed.
     */
    public void resume() {
        onResume();
        this.is_paused = false;
    }


    /**
     * @return true if the stage is currently paused.
     */
    public boolean isPaused() {
        return is_paused;
    }

    public boolean isInitialized() {
        return is_initialized;
    }

    public void setInitialized() {
        is_initialized = true;
    }

    public abstract void reloadGPUResourcesRecursively();

    public abstract void cleanupGPUResourcesRecursively();

    protected abstract void onPause();

    protected abstract void onResume();

    protected StageManager stageManager;

    private boolean is_initialized = false;


}