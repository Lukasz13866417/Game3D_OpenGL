package com.example.game3d_opengl.game.player.player_character;

public final class RecoveryCapabilities {
    public static final RecoveryCapabilities NONE = new RecoveryCapabilities(0);

    private final int extraJumpCount;

    public RecoveryCapabilities(int extraJumpCount) {
        this.extraJumpCount = Math.max(0, extraJumpCount);
    }

    public int getExtraJumpCount() {
        return extraJumpCount;
    }

    public boolean hasAnyAirRecoveryOption() {
        return extraJumpCount > 0;
    }
}
