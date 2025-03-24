package com.example.game3d_opengl.game.stages;


import android.content.Context;

public interface Stage {
     void onTouchDown(float x, float y);
     void onTouchUp(float x, float y);
     void onTouchMove(float x1, float y1, float x2, float y2) ;
     void initScene(Context context, int screenWidth, int screenHeight) ;
     void updateThenDraw(float dt) ;
}