package com.example.game3d.terrain.editor.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

/** User-local editor preferences; project content remains checked in under the selected root. */
public final class ProjectSettings {
    private static final String ROOT_KEY = "projectRoot";
    private static final String RECENT_COUNT_KEY = "recent.count";
    private static final String RECENT_PREFIX = "recent.";
    private static final int MAX_RECENT = 12;
    private static final String PANE_PREFIX = "pane.";
    private final Preferences preferences;

    public ProjectSettings() {
        this(Preferences.userNodeForPackage(ProjectSettings.class));
    }

    public ProjectSettings(Preferences preferences) {
        this.preferences = java.util.Objects.requireNonNull(preferences, "preferences");
    }

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

    public List<Path> recentDocuments() {
        int count = Math.max(0, Math.min(MAX_RECENT,
                preferences.getInt(RECENT_COUNT_KEY, 0)));
        ArrayList<Path> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String value = preferences.get(RECENT_PREFIX + i, "");
            if (value.isEmpty()) continue;
            try { result.add(Paths.get(value).toAbsolutePath().normalize()); }
            catch (RuntimeException ignored) { /* Ignore stale platform-specific paths. */ }
        }
        return Collections.unmodifiableList(result);
    }

    public void setRecentDocuments(List<Path> paths) {
        ArrayList<Path> unique = new ArrayList<>();
        if (paths != null) {
            for (Path path : paths) {
                if (path == null) continue;
                Path normalized = path.toAbsolutePath().normalize();
                if (!unique.contains(normalized)) unique.add(normalized);
                if (unique.size() == MAX_RECENT) break;
            }
        }
        int previous = preferences.getInt(RECENT_COUNT_KEY, 0);
        for (int i = 0; i < unique.size(); i++) {
            preferences.put(RECENT_PREFIX + i, unique.get(i).toString());
        }
        for (int i = unique.size(); i < previous; i++) {
            preferences.remove(RECENT_PREFIX + i);
        }
        preferences.putInt(RECENT_COUNT_KEY, unique.size());
    }

    public double panePosition(String key, double fallback) {
        double safeFallback = normalizedPanePosition(fallback, .5);
        return normalizedPanePosition(
                preferences.getDouble(PANE_PREFIX + key, safeFallback), safeFallback);
    }

    public void setPanePosition(String key, double value) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Pane preference key is empty");
        }
        preferences.putDouble(PANE_PREFIX + key,
                normalizedPanePosition(value, .5));
    }

    private static double normalizedPanePosition(double value, double fallback) {
        return Double.isFinite(value) && value >= .05 && value <= .95
                ? value : fallback;
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
