package com.example.game3d_opengl.game;

import com.example.game3d_opengl.rendering.GPUResourceUser;

public interface WorldActor extends GPUResourceUser {
    void updateBeforeDraw(float dt);
    void updateAfterDraw(float dt);
    void cleanupOwnedGPUResources();
    void reloadOwnedGPUResources();
    void draw(float[] mvpMatrix);
}
