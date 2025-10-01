package com.example.game3d_opengl.game.player.player_state.infos.jump;

import com.example.game3d_opengl.game.player.player_state.infos.PlayerInfoVisitor;

public interface PlayerJumpVisitor extends PlayerInfoVisitor {

    default void visit(PlayerJumpInfo jumpInfo){
        throw new IllegalStateException("Unknown subclass - not handled");
    }

    void visit(PlayerJumpInfo.PlayerHasFooting info);
    void visit(PlayerJumpInfo.PlayerWantsJump info);
    void visit(PlayerJumpInfo.PlayerHitsGroundSoon info);
    void visit(PlayerJumpInfo.PlayerHitsSpikeSoon info);
    void visit(PlayerJumpInfo.PlayerHasJumpCharges info);

}
