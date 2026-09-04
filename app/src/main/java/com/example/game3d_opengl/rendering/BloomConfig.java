package com.example.game3d_opengl.rendering;

/** Shared radiometric constants for scene bloom and direct emissive contributors. */
public final class BloomConfig {
    /** Scene values below this peak channel do not enter the ordinary bright pass. */
    public static final float BRIGHT_THRESHOLD = 0.64f;

    /** Final additive scale applied after the separable bloom blur. */
    public static final float COMPOSITE_INTENSITY = 0.95f;

    private BloomConfig() {
    }
}
