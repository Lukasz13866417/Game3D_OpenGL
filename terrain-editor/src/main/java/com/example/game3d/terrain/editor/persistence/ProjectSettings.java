package com.example.game3d.terrain.editor.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.prefs.Preferences;

/** User-local editor preferences; project content remains checked in under the selected root. */
public final class ProjectSettings {
    private static final String ROOT_KEY = "projectRoot";
    private final Preferences preferences = Preferences.userNodeForPackage(ProjectSettings.class);

    public Optional<Path> projectRoot() {
        String stored = preferences.get(ROOT_KEY, "");
        if (!stored.isEmpty()) {
            Path path = Paths.get(stored).toAbsolutePath().normalize();
            if (isProjectRoot(path)) return Optional.of(path);
        }
        return discover(Paths.get(System.getProperty("user.dir", ".")));
    }

    public void setProjectRoot(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        if (!isProjectRoot(normalized))
            throw new IllegalArgumentException("Not a Game3D Gradle project root: " + normalized);
        preferences.put(ROOT_KEY, normalized.toString());
    }

    public static Optional<Path> discover(Path start) {
        Path candidate = start.toAbsolutePath().normalize();
        while (candidate != null) {
            if (isProjectRoot(candidate)) return Optional.of(candidate);
            candidate = candidate.getParent();
        }
        return Optional.empty();
    }

    public static boolean isProjectRoot(Path path) {
        return path != null && Files.isRegularFile(path.resolve("gradlew"))
                && Files.isRegularFile(path.resolve("settings.gradle.kts"));
    }
}
