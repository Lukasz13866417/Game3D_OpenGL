package com.example.game3d.terrain.editor.persistence;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class UserStateDirectory {
    private UserStateDirectory() {}

    public static Path terrainEditor() {
        String xdg = System.getenv("XDG_STATE_HOME");
        if (xdg != null && !xdg.trim().isEmpty()) return Paths.get(xdg, "game3d", "terrain-editor");
        String appData = System.getenv("LOCALAPPDATA");
        if (appData != null && !appData.trim().isEmpty()) return Paths.get(appData, "Game3D", "terrain-editor");
        return Paths.get(System.getProperty("user.home"), ".local", "state", "game3d", "terrain-editor");
    }
}
