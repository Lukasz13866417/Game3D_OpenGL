package com.example.game3d_opengl.game.player.player_state.infos.jump;

public interface PlayerAllJumpVisitor {
    default void visit(PlayerAllJumpVisitor info){
        throw new RuntimeException("Unknown subclass of PlayerJumpInfo");
    }
    void visit(PlayerJumpInfo.PlayerHasFooting info);
    void visit(PlayerJumpInfo.PlayerWantsJump info);
    void visit(PlayerJumpInfo.PlayerHitsGroundSoon info);
    void visit(PlayerJumpInfo.PlayerHitsSpikeSoon info);
    void visit(PlayerJumpInfo.PlayerHasJumpCharges info);

}
