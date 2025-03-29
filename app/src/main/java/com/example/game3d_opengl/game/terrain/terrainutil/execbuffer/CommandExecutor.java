package com.example.game3d_opengl.game.terrain.terrainutil.execbuffer;

public interface CommandExecutor {
    void execute(float[] buffer, int offset, int length);

    boolean canHandle(float v);
}
