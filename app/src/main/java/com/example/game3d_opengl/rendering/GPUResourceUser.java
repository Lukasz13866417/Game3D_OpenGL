package com.example.game3d_opengl.rendering;

public interface GPUResourceUser {
      void reloadOwnedGPUResources();
      void cleanupOwnedGPUResources();
}
