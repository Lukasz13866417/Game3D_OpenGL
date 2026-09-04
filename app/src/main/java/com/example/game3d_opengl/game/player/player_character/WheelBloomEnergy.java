package com.example.game3d_opengl.game.player.player_character;

/** Pure helpers shared by the temporal-wheel bloom setup and its JVM tests. */
final class WheelBloomEnergy {
    private WheelBloomEnergy() {
    }

    static float brightPassFactor(
            float red,
            float green,
            float blue,
            float threshold) {
        float peak = Math.max(red, Math.max(green, blue));
        return Math.max(
                (peak - threshold) / Math.max(1.0e-4f, 1f - threshold),
                0f);
    }

    /** Residual which makes ordinary + residual equal max(ordinary, temporalTarget). */
    static float residual(
            float decodedPremultipliedExposure,
            float emissionBrightFactor,
            float ordinaryBrightPass) {
        return Math.max(
                decodedPremultipliedExposure * emissionBrightFactor
                        - ordinaryBrightPass,
                0f);
    }
}
