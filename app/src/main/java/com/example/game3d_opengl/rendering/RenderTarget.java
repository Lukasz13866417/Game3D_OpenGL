package com.example.game3d_opengl.rendering;

public interface RenderTarget extends GPUResourceOwner {
    void bind();
    void unbind();
    int getWidth();
    int getHeight();
    int getTextureId();

    /** Sampleable depth texture, or zero for color-only/legacy targets. */
    default int getDepthTextureId() {
        return 0;
    }
}
