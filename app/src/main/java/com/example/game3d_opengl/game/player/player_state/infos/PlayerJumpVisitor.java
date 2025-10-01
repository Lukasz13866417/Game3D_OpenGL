package com.example.game3d_opengl.game.player.player_state.infos;

public interface PlayerJumpVisitor {

    default void visit(PlayerJumpInfo jumpInfo){
        throw new IllegalStateException("Unknown subclass - not handled");
    }

    void visit(PlayerJumpInfo.PlayerHasFooting info);
    void visit(PlayerJumpInfo.PlayerWantsJump info);
    void visit(PlayerJumpInfo.PlayerHitsGroundSoon info);
    void visit(PlayerJumpInfo.PlayerHitsSpikeSoon info);
    void visit(PlayerJumpInfo.PlayerHasJumpCharges info);

}
