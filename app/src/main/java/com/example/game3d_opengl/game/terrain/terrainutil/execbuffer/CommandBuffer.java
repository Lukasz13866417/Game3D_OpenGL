package com.example.game3d_opengl.game.terrain.terrainutil.execbuffer;

public interface CommandBuffer {
    void addCommand(float... args);
    void executeFirstCommand(CommandExecutor executor);

    boolean hasAnyCommands();
}
