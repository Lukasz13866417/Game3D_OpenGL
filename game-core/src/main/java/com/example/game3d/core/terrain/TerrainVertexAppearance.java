package com.example.game3d.core.terrain;

/** Immutable renderer-neutral attributes for one authored terrain corner. */
public final class TerrainVertexAppearance {
    public static final TerrainVertexAppearance DEFAULT =
            new TerrainVertexAppearance(1.0f, 1.0f);

    public final float alpha;
    public final float brightness;

    public TerrainVertexAppearance(float alpha, float brightness) {
        if (!Float.isFinite(alpha) || !Float.isFinite(brightness)
                || alpha < 0.0f || brightness < 0.0f) {
            throw new IllegalArgumentException(
                    "Terrain appearance values must be finite and non-negative");
        }
        this.alpha = alpha;
        this.brightness = brightness;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TerrainVertexAppearance)) {
            return false;
        }
        TerrainVertexAppearance value = (TerrainVertexAppearance) other;
        return Float.floatToIntBits(alpha) == Float.floatToIntBits(value.alpha)
                && Float.floatToIntBits(brightness)
                == Float.floatToIntBits(value.brightness);
    }

    @Override
    public int hashCode() {
        return 31 * Float.floatToIntBits(alpha) + Float.floatToIntBits(brightness);
    }
}
