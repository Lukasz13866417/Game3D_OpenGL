package com.example.game3d_opengl.game;

public interface WorldActor {
    void updateBeforeDraw(float dt);
    void updateAfterDraw(float dt);
    void cleanupGPUResources();
    void resetGPUResources();
    void draw(float[] mvpMatrix);
}
