package com.example.game3d_opengl.game.player.player_state.infos.jump;

import com.example.game3d_opengl.game.player.player_state.infos.PlayerAffectingInfo;
import com.example.game3d_opengl.game.terrain.terrain_api.main.Tile;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public abstract class PlayerJumpInfo extends PlayerAffectingInfo<PlayerAllJumpVisitor> {
    public abstract void accept(PlayerAllJumpVisitor visitor);

    public static class PlayerHasFooting extends PlayerJumpInfo {
        public final Tile tile;                 // tile we stand on
        public final Vector3D[][] triangles;   // tile triangles for physics

        public PlayerHasFooting(Tile tile, Vector3D[][] triangles) {
            this.tile = tile;
            this.triangles = triangles;
        }

        public void accept(PlayerAllJumpVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class PlayerHasJumpCharges extends PlayerJumpInfo {
        public void accept(PlayerAllJumpVisitor visitor) {
            visitor.visit(this);
        }

    }

    public static class PlayerHitsGroundSoon extends PlayerJumpInfo {
        public void accept(PlayerAllJumpVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class PlayerHitsSpikeSoon extends PlayerJumpInfo {
        public void accept(PlayerAllJumpVisitor visitor) {
            visitor.visit(this);
        }
    }

    public static class PlayerWantsJump extends PlayerJumpInfo {
        public void accept(PlayerAllJumpVisitor visitor) {
            visitor.visit(this);
        }
    }

}
