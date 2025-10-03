package com.example.game3d_opengl.game.player.player_state.infos;

public abstract class PlayerAffectingInfo<BaseVisitor> {
    public abstract void accept(BaseVisitor visitor);
}
