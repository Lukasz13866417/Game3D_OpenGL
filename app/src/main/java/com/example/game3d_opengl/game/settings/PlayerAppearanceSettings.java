package com.example.game3d_opengl.game.settings;

/**
 * Defines the wheel appearance currently used by every gameplay session.
 */
public final class PlayerAppearanceSettings {
    /**
     * Names the active wheel and the OBJ asset that supplies its geometry.
     */
    public enum WheelStyle {
        GREEN("mint-wheel.obj");

        private final String assetFilename;

        WheelStyle(String assetFilename) {
            this.assetFilename = assetFilename;
        }

        public String assetFilename() {
            return assetFilename;
        }
    }

    private PlayerAppearanceSettings() {}

    public static WheelStyle getWheelStyle() {
        return WheelStyle.GREEN;
    }
}
