package com.example.game3d_opengl.game.terrain_api;

import com.example.game3d_opengl.game.WorldActor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public interface TerrainElement extends WorldActor {
    boolean isGoneBy(long playerTileID);
}
