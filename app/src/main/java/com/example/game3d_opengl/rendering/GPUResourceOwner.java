package com.example.game3d_opengl.rendering;

public interface GPUResourceOwner {
      void reloadGPUResourcesRecursively();
      void cleanupGPUResourcesRecursively();
}
