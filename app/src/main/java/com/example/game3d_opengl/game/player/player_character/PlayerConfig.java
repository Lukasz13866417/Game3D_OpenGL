package com.example.game3d_opengl.game.player.player_character;

import com.example.game3d.core.simulation.PhysicsConfig;

/**
 * Defines the player's shared visual dimensions and remaining legacy movement settings.
 * Visual dimensions stay tied to the authoritative cylinder used by game-core.
 */
public class PlayerConfig {
    static final float PLAYER_WIDTH =
            (float) (PhysicsConfig.DEFAULT_CYLINDER_HALF_LENGTH * 2.0);
    static final float PLAYER_HEIGHT =
            (float) (PhysicsConfig.DEFAULT_CYLINDER_RADIUS * 2.0);

    // Physics constants
    static final float FALL_ACCELERATION = 3e-5f;
    static final float FALL_COLLISION_SAFETY_MULTIPLIER = 1.05f;

    static final float INITIAL_PLAYER_SPEED = 0.032f;
    static final float PLAYER_SPEED_PER_PHASE = 0.0025f;

    // Movement constants
    static final float INITIAL_DIRECTION_X = 0f;
    static final float INITIAL_DIRECTION_Y = 0f;
    static final float INITIAL_DIRECTION_Z = -1f;
    static final float INITIAL_POSITION_X = 0f;
    static final float INITIAL_POSITION_Y = -0.5f;
    static final float INITIAL_POSITION_Z = -0.5f;

    // Error messages
     static final String ERROR_ASSETS_NOT_LOADED
            = "Player assets not loaded. Call LOAD_PLAYER_ASSETS first.";
     static final String ERROR_ASSET_LOADING
            = "Failed to load player assets: ";
     static final String TAG = "Player";

    public final float playerWidth;
    public final float playerHeight;
    public final float fallAcceleration;
    public final float fallCollisionSafetyMultiplier;
    public float playerSpeed;
    public final float initialDirectionX;
    public final float initialDirectionY;
    public final float initialDirectionZ;

    public PlayerConfig() {
        this.playerWidth = PLAYER_WIDTH;
        this.playerHeight = PLAYER_HEIGHT;
        this.fallAcceleration = FALL_ACCELERATION;
        this.fallCollisionSafetyMultiplier = FALL_COLLISION_SAFETY_MULTIPLIER;
        this.playerSpeed = INITIAL_PLAYER_SPEED;
        this.initialDirectionX = INITIAL_DIRECTION_X;
        this.initialDirectionY = INITIAL_DIRECTION_Y;
        this.initialDirectionZ = INITIAL_DIRECTION_Z;
    }

    public static float speedForCompletedPhases(int completedPhaseCount) {
        return INITIAL_PLAYER_SPEED + Math.max(0, completedPhaseCount) * PLAYER_SPEED_PER_PHASE;
    }
}
