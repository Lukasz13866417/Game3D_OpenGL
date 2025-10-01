package com.example.game3d_opengl.game.player.player_state.infos;

public abstract class PlayerAffectingInfo<V extends PlayerInfoVisitor> {
    public abstract void accept(V visitor);
}
