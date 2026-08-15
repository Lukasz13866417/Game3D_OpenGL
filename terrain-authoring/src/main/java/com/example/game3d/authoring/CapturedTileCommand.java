package com.example.game3d.authoring;

import com.example.game3d.core.terrain.SurfaceProperties;

/**
 * Exact resolved CPU tile command used by tooling that expands handwritten Java content.
 * Angles remain in radians so an exporter can preserve the interpreter's floating-point input.
 */
public final class CapturedTileCommand {
    public final boolean solid;
    public final double turnDeltaRadians;
    public final double absolutePitchRadians;
    public final double liftBefore;
    public final SurfaceProperties surface;
    public final float alphaLeft;
    public final float alphaRight;
    public final float brightness;

    CapturedTileCommand(
            boolean solid,
            double turnDeltaRadians,
            double absolutePitchRadians,
            double liftBefore,
            SurfaceProperties surface,
            float alphaLeft,
            float alphaRight,
            float brightness) {
        this.solid = solid;
        this.turnDeltaRadians = turnDeltaRadians;
        this.absolutePitchRadians = absolutePitchRadians;
        this.liftBefore = liftBefore;
        this.surface = surface;
        this.alphaLeft = alphaLeft;
        this.alphaRight = alphaRight;
        this.brightness = brightness;
    }
}
