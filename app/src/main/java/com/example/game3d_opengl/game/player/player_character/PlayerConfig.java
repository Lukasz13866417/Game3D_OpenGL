package com.example.game3d_opengl.game.player.player_character;

import static com.example.game3d_opengl.game.util.GameMath.PI;

public class PlayerConfig {
    static final float PLAYER_WIDTH = 0.132f;
    static final float PLAYER_HEIGHT = PLAYER_WIDTH * 3.54f;

    // Physics constants
    static final float FALL_ACCELERATION = 3e-5f;
    static final float FALL_COLLISION_SAFETY_MULTIPLIER = 1.05f;

    static final float PLAYER_SPEED = 0.04f;

    // Rotation constants
     static final float STICKY_ROTATION_LASTING_TIME = 42f;
     static final float STICKY_ROTATION_ANGLE_DECAY_RATE = 0.0575f;
     static final float STICKY_ROTATION_COEFFICIENT = 0.0085f;
     static final float ROTATION_SWIPE_SENSITIVITY = 0.00052f;

    // Jump physics
    static final float JUMP_INITIAL_SPEED = 0.015f;

    // Bounce constants
    static final float BOUNCE_FALL_SPEED_THRESHOLD = 0.0025f;
    static final float BOUNCE_SPEED_FACTOR = 0.6f;

    // Movement constants
    static final float INITIAL_DIRECTION_X = 0f;
    static final float INITIAL_DIRECTION_Y = 0f;
    static final float INITIAL_DIRECTION_Z = -1f;
     static final float INITIAL_POSITION_X = 0f;
     static final float INITIAL_POSITION_Y = -0.5f;
     static final float INITIAL_POSITION_Z = -0.5f;


    // Asset loading constants
     static final String PLAYER_MODEL_FILENAME = "tire.obj";
     static final float MODEL_ROTATION_X = PI / 2;
     static final float MODEL_ROTATION_Y = PI / 2;

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
    public final float playerSpeed;
    public final float stickyRotationLastingTime;
    public final float stickyRotationAngleDecayRate;
    public final float stickyRotationCoefficient;
    public final float rotationSwipeSensitivity;
    public final float initialDirectionX;
    public final float initialDirectionY;
    public final float initialDirectionZ;
    public final float jumpInitialSpeed;
    public final float bounceFallSpeedThreshold;
    public final float bounceSpeedFactor;

    public PlayerConfig() {
        this.playerWidth = PLAYER_WIDTH;
        this.playerHeight = PLAYER_HEIGHT;
        this.fallAcceleration = FALL_ACCELERATION;
        this.fallCollisionSafetyMultiplier = FALL_COLLISION_SAFETY_MULTIPLIER;
        this.playerSpeed = PLAYER_SPEED;
        this.stickyRotationLastingTime = STICKY_ROTATION_LASTING_TIME;
        this.stickyRotationAngleDecayRate = STICKY_ROTATION_ANGLE_DECAY_RATE;
        this.stickyRotationCoefficient = STICKY_ROTATION_COEFFICIENT;
        this.rotationSwipeSensitivity = ROTATION_SWIPE_SENSITIVITY;
        this.initialDirectionX = INITIAL_DIRECTION_X;
        this.initialDirectionY = INITIAL_DIRECTION_Y;
        this.initialDirectionZ = INITIAL_DIRECTION_Z;
        this.jumpInitialSpeed = JUMP_INITIAL_SPEED;
        this.bounceFallSpeedThreshold = BOUNCE_FALL_SPEED_THRESHOLD;
        this.bounceSpeedFactor = BOUNCE_SPEED_FACTOR;
    }
}
