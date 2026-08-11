package com.example.game3d_opengl.game.player.player_character;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlayerFallRecoveryTest {
    @Test
    public void unrecoverable_fall_requires_loss_of_footing() {
        PlayerConfig config = new PlayerConfig();

        assertFalse(Player.shouldDieFromUnrecoverableFall(
                config,
                true,
                true,
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY,
                -0.01f,
                -1.0f,
                0f
        ));
    }

    @Test
    public void unrecoverable_fall_waits_while_ground_is_still_close() {
        PlayerConfig config = new PlayerConfig();

        assertFalse(Player.shouldDieFromUnrecoverableFall(
                config,
                true,
                false,
                config.playerHeight,
                -0.2f,
                -0.01f,
                -0.9f,
                0f
        ));
    }

    @Test
    public void unrecoverable_fall_waits_while_player_is_still_moving_upward() {
        PlayerConfig config = new PlayerConfig();

        assertFalse(Player.shouldDieFromUnrecoverableFall(
                config,
                true,
                false,
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY,
                0.01f,
                -0.9f,
                0f
        ));
    }

    @Test
    public void unrecoverable_fall_triggers_after_large_drop_with_no_ground_below() {
        PlayerConfig config = new PlayerConfig();
        float lethalDrop = Player.unrecoverableDropThreshold(config) + 0.05f;
        float belowTileMargin = Player.belowTileMarginThreshold(config) + 0.05f;
        float playerY = -Math.max(lethalDrop, belowTileMargin);

        assertTrue(Player.shouldDieFromUnrecoverableFall(
                config,
                true,
                false,
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY,
                -0.02f,
                playerY,
                0f
        ));
    }

    @Test
    public void unrecoverable_fall_triggers_when_ground_is_far_below_recovery_range() {
        PlayerConfig config = new PlayerConfig();
        float lethalDrop = Player.unrecoverableDropThreshold(config) + 0.05f;
        float farGround = Player.recoverableGroundDistance(config) + 0.05f;
        float nearestTileY = -0.1f;
        float playerY = Math.min(
                -Math.max(lethalDrop, Player.belowTileMarginThreshold(config) + 0.05f),
                nearestTileY - Player.belowTileMarginThreshold(config) - 0.05f
        );

        assertTrue(Player.shouldDieFromUnrecoverableFall(
                config,
                true,
                false,
                farGround,
                nearestTileY,
                -0.02f,
                playerY,
                0f
        ));
    }

    @Test
    public void unrecoverable_fall_is_disabled_until_track_anchor_has_been_established() {
        PlayerConfig config = new PlayerConfig();
        float lethalDrop = Player.unrecoverableDropThreshold(config) + 0.05f;

        assertFalse(Player.shouldDieFromUnrecoverableFall(
                config,
                false,
                false,
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY,
                -0.02f,
                -lethalDrop,
                0f
        ));
    }

    @Test
    public void unrecoverable_fall_waits_until_player_is_below_nearest_tile_surface() {
        PlayerConfig config = new PlayerConfig();
        float lethalDrop = Player.unrecoverableDropThreshold(config) + 0.20f;
        float nearestTileY = -0.55f;
        float playerY = nearestTileY - Player.belowTileMarginThreshold(config) + 0.05f;

        assertFalse(Player.shouldDieFromUnrecoverableFall(
                config,
                true,
                false,
                Float.POSITIVE_INFINITY,
                nearestTileY,
                -0.02f,
                playerY,
                0f
        ));
    }

    @Test
    public void unrecoverable_fall_waits_when_extra_jump_recovery_is_available() {
        PlayerConfig config = new PlayerConfig();
        float lethalDrop = Player.unrecoverableDropThreshold(config) + 0.30f;

        assertFalse(RecoverabilityJudge.shouldLoseRunFromUnrecoverableFall(
                config,
                new RecoveryCapabilities(1),
                true,
                false,
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY,
                -0.02f,
                -lethalDrop,
                0f
        ));
    }

    @Test
    public void unrecoverable_fall_triggers_after_player_passes_below_nearest_tile_surface_margin() {
        PlayerConfig config = new PlayerConfig();
        float lethalDrop = Player.unrecoverableDropThreshold(config) + 0.30f;
        float nearestTileY = -0.55f;
        float playerY = nearestTileY - Player.belowTileMarginThreshold(config) - 0.05f;

        assertTrue(Player.shouldDieFromUnrecoverableFall(
                config,
                true,
                false,
                Float.POSITIVE_INFINITY,
                nearestTileY,
                -0.02f,
                Math.min(-lethalDrop, playerY),
                0f
        ));
    }
}
