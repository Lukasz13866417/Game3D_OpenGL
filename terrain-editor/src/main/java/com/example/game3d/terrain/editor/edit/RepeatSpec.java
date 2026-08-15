package com.example.game3d.terrain.editor.edit;

public record RepeatSpec(
        int count,
        double startTurn, double turnIncrement,
        double startSlope, double slopeIncrement,
        double startLift, double liftIncrement,
        double startAlpha, double alphaIncrement,
        double startBrightness, double brightnessIncrement) {
    public RepeatSpec {
        if (count < 1) throw new IllegalArgumentException("count must be positive");
    }
}
