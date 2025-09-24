package com.example.game3d_opengl.game;

import com.example.game3d_opengl.rendering.GPUResourceOwner;

public interface WorldActor extends GPUResourceOwner {
    void updateBeforeDraw(float dt);
    void updateAfterDraw(float dt);
    void cleanupGPUResourcesRecursively();
    void reloadGPUResourcesRecursively();
    void draw(float[] mvpMatrix);
}
