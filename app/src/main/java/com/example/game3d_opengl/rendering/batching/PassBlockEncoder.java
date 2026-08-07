package com.example.game3d_opengl.rendering.batching;

import java.nio.FloatBuffer;

public interface PassBlockEncoder<P> {
    int floatCount();

    void encode(P pass, FloatBuffer target);
}
