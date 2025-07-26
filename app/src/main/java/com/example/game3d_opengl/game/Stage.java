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


     public boolean isInitialized(){
          return is_initialized;
     }

     public void setInitialized(){
          is_initialized = true;
     }

     private boolean is_initialized = false;

}