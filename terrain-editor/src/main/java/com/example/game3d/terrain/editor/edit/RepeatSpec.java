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
        requireFinite("startTurn", startTurn);
        requireFinite("turnIncrement", turnIncrement);
        requireFinite("startSlope", startSlope);
        requireFinite("slopeIncrement", slopeIncrement);
        requireFinite("startLift", startLift);
        requireFinite("liftIncrement", liftIncrement);
        requireFinite("startAlpha", startAlpha);
        requireFinite("alphaIncrement", alphaIncrement);
        requireFinite("startBrightness", startBrightness);
        requireFinite("brightnessIncrement", brightnessIncrement);
    }

    private static void requireFinite(String field, double value) {
        if (!Double.isFinite(value)) {
            // NumberFormatException lets existing numeric-dialog call sites report this as input
            // failure without committing a partial compound edit.
            throw new NumberFormatException(field + " must be finite");
        }
    }
}
