package com.example.game3d_opengl.game.terrain.terrain_api.main;

public enum TileProfile {
    NORMAL(0, 1f, 1f),
    BOOST_RAMP(1, 1.55f, 1.32f),
    BOOST_RAMP_LAUNCH(2, 1.62f, 1.32f);

    private final int commandId;
    private final float horizontalSpeedMultiplier;
    private final float brightnessMultiplier;

    TileProfile(int commandId, float horizontalSpeedMultiplier, float brightnessMultiplier) {
        this.commandId = commandId;
        this.horizontalSpeedMultiplier = horizontalSpeedMultiplier;
        this.brightnessMultiplier = brightnessMultiplier;
    }

    public int getCommandId() {
        return commandId;
    }

    public float getHorizontalSpeedMultiplier() {
        return horizontalSpeedMultiplier;
    }

    public float getBrightnessMultiplier() {
        return brightnessMultiplier;
    }

    public float applyHorizontalSpeed(float baseSpeed) {
        return baseSpeed * horizontalSpeedMultiplier;
    }

    public static TileProfile fromCommandId(int commandId) {
        for (TileProfile profile : values()) {
            if (profile.commandId == commandId) {
                return profile;
            }
        }
        return NORMAL;
    }
}
