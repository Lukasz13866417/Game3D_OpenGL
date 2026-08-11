package com.example.game3d.core.terrain;

public enum SurfaceMaterial {
    NORMAL(1.0),
    BOOST(1.65);

    public final double motorSpeedMultiplier;

    SurfaceMaterial(double motorSpeedMultiplier) {
        this.motorSpeedMultiplier = motorSpeedMultiplier;
    }

    public SurfaceProperties properties() {
        return this == BOOST
                ? SurfaceProperties.LEGACY_BOOST : SurfaceProperties.NORMAL;
    }
}
