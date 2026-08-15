package com.example.game3d.terrain.editor.persistence;

import java.nio.file.Path;

/** Tracks a consciously retained external edit until a save decision resolves it. */
public final class ExternalConflictGuard {
    private boolean pending;

    public boolean pending() {
        return pending;
    }

    public void keepEditorVersion() {
        pending = true;
    }

    public void resolved() {
        pending = false;
    }

    public boolean requiresExplicitSaveDecision(
            Path sourcePath, Path targetPath, boolean diskChangedNow) {
        return sameFileName(sourcePath, targetPath) && (pending || diskChangedNow);
    }

    public static boolean sameFileName(Path left, Path right) {
        return left != null && right != null
                && left.toAbsolutePath().normalize()
                .equals(right.toAbsolutePath().normalize());
    }
}
