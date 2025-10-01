package com.example.game3d_opengl.game.player.player_state.infos;

public abstract class PlayerAffectingInfo {
    public void accept(PlayerInfoVisitor visitor){
        visitor.visit(this);
    }
}
