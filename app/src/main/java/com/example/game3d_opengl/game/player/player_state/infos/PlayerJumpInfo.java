package com.example.game3d_opengl.game.player.player_state.infos;

public abstract class PlayerJumpInfo extends PlayerAffectingInfo {
    public void accept(PlayerJumpVisitor visitor) {
        visitor.visit(this);
    }

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
