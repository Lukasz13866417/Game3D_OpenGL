package com.example.game3d_opengl.game.terrain.track_elements.potion;

/** Renderer input for one collectible, independent from the legacy terrain-addon hierarchy. */
public interface PotionBatchInstance {
    void writePotionModelMatrix(float[] outModel);
}
