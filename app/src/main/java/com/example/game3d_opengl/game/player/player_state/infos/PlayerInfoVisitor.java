package com.example.game3d_opengl.game.player.player_state.infos;

import com.example.game3d_opengl.game.player.player_state.infos.jump.PlayerJumpInfo;

public interface PlayerInfoVisitor {

    default void visit(PlayerAffectingInfo<?> info){
        throw new IllegalStateException("Unknown subclass - not handled");
    }
    void visit(PlayerJumpInfo info);
}
