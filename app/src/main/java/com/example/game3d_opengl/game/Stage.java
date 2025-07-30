package com.example.game3d_opengl.game;


import android.content.Context;

public abstract class Stage {
     public abstract void onTouchDown(float x, float y);
     public abstract void onTouchUp(float x, float y);
     public abstract void onTouchMove(float x1, float y1, float x2, float y2) ;
     public abstract void initScene(Context context, int screenWidth, int screenHeight) ;
     public abstract void updateThenDraw(float dt) ;

     public abstract void onClose();
     public abstract void onSwitch();

     public abstract void onReturn();

     private boolean is_paused = false;

    /**
     * Called when the application is paused. The default implementation marks the stage as paused.
     * Subclasses can override this to save state or pause expensive operations like sound.
     */
    public void pause() {
        this.is_paused = true;
    }

    /**
     * Called when the application is resumed. The default implementation marks the stage as not paused.
     * Subclasses can override this to restore state.
     */
    public void resume() {
        this.is_paused = false;
    }

    /**
     * @return true if the stage is currently paused.
     */
    public boolean isPaused() {
        return is_paused;
    }

     public boolean isInitialized(){
          return is_initialized;
     }

     public void setInitialized(){
          is_initialized = true;
     }

     private boolean is_initialized = false;

     public abstract void resetGPUResources() ;
}