package com.example.game3d_opengl.game.player.player_state.infos;

import com.example.game3d_opengl.game.player.player_state.infos.jump.PlayerAllJumpVisitor;

public interface PlayerAllInfoVisitor extends PlayerAllJumpVisitor {
    default void visit(PlayerAffectingInfo<? super PlayerAllInfoVisitor> ignoredInfo){
        throw new IllegalStateException("Unknown subclass - not handled");
    }

}
