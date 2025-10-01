package com.example.game3d_opengl.game.player.player_state.infos.jump;

import com.example.game3d_opengl.game.player.player_state.infos.PlayerAffectingInfo;

public abstract class PlayerJumpInfo extends PlayerAffectingInfo<PlayerJumpVisitor> {
    public abstract void accept(PlayerJumpVisitor visitor);

    public static class PlayerHasFooting extends PlayerJumpInfo {
        public void accept(PlayerJumpVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class PlayerHasJumpCharges extends PlayerJumpInfo {
        public void accept(PlayerJumpVisitor visitor) {
            visitor.visit(this);
        }

    }

    public static class PlayerHitsGroundSoon extends PlayerJumpInfo {
        public void accept(PlayerJumpVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class PlayerHitsSpikeSoon extends PlayerJumpInfo {
        public void accept(PlayerJumpVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class PlayerWantsJump extends PlayerJumpInfo {
        public void accept(PlayerJumpVisitor visitor) {
            visitor.visit(this);
        }
    }
}
