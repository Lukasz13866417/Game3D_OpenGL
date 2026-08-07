package com.example.game3d_opengl.rendering.batching;

import java.nio.FloatBuffer;

public interface ObjectBlockEncoder<O> {
    int floatCountPerObject();

    void encode(O object, FloatBuffer target);
}
