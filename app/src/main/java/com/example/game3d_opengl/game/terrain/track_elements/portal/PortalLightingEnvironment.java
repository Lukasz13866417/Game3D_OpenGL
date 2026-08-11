package com.example.game3d_opengl.game.terrain.track_elements.portal;

import com.example.game3d_opengl.game.LightSource;
import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Shared lighting/camera/theme state for addon visuals.
 * Updated once per frame by the gameplay stage.
 */
public final class PortalLightingEnvironment {
    private static Vector3D cameraPos = new Vector3D(0f, 0f, 3f);
    private static Vector3D lightPos = new Vector3D(0f, 10f, 0f);
    private static FColor lightColor = FColor.CLR(1f, 1f, 1f, 1f);
    private static FColor colorTheme = FColor.CLR(0.8f, 0f, 0f, 1f);

    private PortalLightingEnvironment() {}

    public static void update(LightSource source, Vector3D cameraPosition, FColor theme) {
        if (cameraPosition != null) {
            cameraPos = cameraPosition;
        }
        if (source != null) {
            if (source.position != null) {
                lightPos = source.position;
            }
            if (source.color != null) {
                lightColor = source.color;
            }
        }
        if (theme != null) {
            colorTheme = theme;
        }
    }

    public static void update(LightSource source, Vector3D cameraPosition) {
        update(source, cameraPosition, null);
    }

    public static Vector3D getCameraPos() {
        return cameraPos;
    }

    public static Vector3D getLightPos() {
        return lightPos;
    }

    public static FColor getLightColor() {
        return lightColor;
    }

    public static FColor getColorTheme() {
        return colorTheme;
    }
}
