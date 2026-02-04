package com.example.game3d_opengl.game.player.player_character;

import static com.example.game3d_opengl.game.util.GameMath.PI;

public class PlayerConfig {
    public static final float PLAYER_WIDTH = 0.132f;
    public static final float PLAYER_HEIGHT = PLAYER_WIDTH * 3.54f;

    // Physics constants
    public static final float FALL_ACCELERATION = 3e-5f;
    public static final float FALL_COLLISION_SAFETY_MULTIPLIER = 1.05f;

    public static final float PLAYER_SPEED = 0.04f;

    // Rotation constants
     static final float STICKY_ROTATION_LASTING_TIME = 42f;
     static final float STICKY_ROTATION_ANGLE_DECAY_RATE = 0.0575f;
     static final float STICKY_ROTATION_COEFFICIENT = 0.0085f;
     static final float ROTATION_SWIPE_SENSITIVITY = 0.00052f;

    // Movement constants
    public static final float INITIAL_DIRECTION_X = 0f;
    public static final float INITIAL_DIRECTION_Y = 0f;
    public static final float INITIAL_DIRECTION_Z = -1f;
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
}
