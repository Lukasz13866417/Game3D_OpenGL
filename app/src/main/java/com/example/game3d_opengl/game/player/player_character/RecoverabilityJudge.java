package com.example.game3d_opengl.game.player.player_character;

import static java.lang.Float.max;

final class RecoverabilityJudge {
    private RecoverabilityJudge() {}

    static boolean shouldLoseRunFromUnrecoverableFall(
            PlayerConfig config,
            RecoveryCapabilities recoveryCapabilities,
            boolean hasRecoverableTrackAnchor,
            boolean hasFooting,
            float nearestGroundDistance,
            float nearestGroundY,
            float verticalMoveY,
            float playerY,
            float lastRecoverableTrackY
    ) {
        if (config == null
                || !hasRecoverableTrackAnchor
                || hasFooting
                || verticalMoveY > 0f) {
            return false;
        }
        if (recoveryCapabilities != null && recoveryCapabilities.hasAnyAirRecoveryOption()) {
            return false;
        }
        if (!Float.isInfinite(nearestGroundDistance)) {
            float recoverableGroundDistance = max(
                    config.playerHeight * Player.UNRECOVERABLE_FALL_RECOVERY_GROUND_FACTOR,
                    config.playerHeight
            );
            if (nearestGroundDistance <= recoverableGroundDistance) {
                return false;
            }
        }
        float unrecoverableDrop = max(
                config.playerHeight * Player.UNRECOVERABLE_FALL_DROP_FACTOR,
                Player.UNRECOVERABLE_FALL_MIN_DROP
        );
        if (lastRecoverableTrackY - playerY < unrecoverableDrop) {
            return false;
        }
        float referenceTileY = !Float.isInfinite(nearestGroundY) ? nearestGroundY : lastRecoverableTrackY;
        float belowTileMargin = max(
                config.playerHeight * Player.UNRECOVERABLE_FALL_BELOW_TILE_MARGIN_FACTOR,
                Player.UNRECOVERABLE_FALL_BELOW_TILE_MIN_MARGIN
        );
        return playerY < referenceTileY - belowTileMargin;
    }
}
