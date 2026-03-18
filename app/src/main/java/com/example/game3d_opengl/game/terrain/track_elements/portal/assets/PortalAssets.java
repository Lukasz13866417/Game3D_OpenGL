package com.example.game3d_opengl.game.terrain.track_elements.portal.assets;

public final class PortalAssets {
    private PortalAssets() {}

    public static PortalAsset createPortalAsset() {
        return new SpikedTorusPortalAsset();
        // return new TriambicIcosahedronPortalAsset();
    }
}

