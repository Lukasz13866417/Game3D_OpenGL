package com.example.game3d_opengl.game.player.player_state.infos;

public interface PlayerInfoVisitor {

    default void visit(PlayerAffectingInfo info){
        throw new IllegalStateException("Unknown subclass - not handled");
    }
    void visit(PlayerJumpInfo info);
}
