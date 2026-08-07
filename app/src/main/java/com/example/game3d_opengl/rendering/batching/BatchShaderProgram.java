package com.example.game3d_opengl.rendering.batching;

import com.example.game3d_opengl.rendering.GPUResourceOwner;

public interface BatchShaderProgram extends GPUResourceOwner {
    void useProgram();

    void bindGeometryAttributes(StaticGeometrySource geometry);

    void disableGeometryAttributes();
}
