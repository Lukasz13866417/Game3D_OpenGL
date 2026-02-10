package com.example.game3d_opengl.game;

import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public interface WorldActor extends GPUResourceOwner {
    void updateBeforeDraw(float dt);
    void updateAfterDraw(float dt);
    void cleanupGPUResourcesRecursivelyOnContextLoss();
    void reloadGPUResourcesRecursivelyOnContextLoss();
    void draw(float[] mvpMatrix);
    void rebasePosition(Vector3D delta);
}
