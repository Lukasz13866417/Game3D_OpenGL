package com.example.game3d_opengl.game.terrain.track_elements.potion;

import com.example.game3d_opengl.rendering.util3d.FColor;

/** Renderer input for one collectible, independent from the legacy terrain-addon hierarchy. */
public interface PotionBatchInstance {
    void writePotionModelMatrix(float[] outModel);
    FColor potionFillColor();
}
