package com.example.game3d.terrain.editor.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalConflictGuardTest {
    @TempDir Path directory;

    @Test void keepDecisionSurvivesMtimeAcknowledgementUntilExplicitSaveResolution() {
        Path original = directory.resolve("terrain.json");
        ExternalConflictGuard guard = new ExternalConflictGuard();

        assertTrue(guard.requiresExplicitSaveDecision(original, original, true));
        guard.keepEditorVersion();
        assertTrue(guard.pending());
        assertTrue(guard.requiresExplicitSaveDecision(original, original, false));
        assertFalse(guard.requiresExplicitSaveDecision(
                original, directory.resolve("copy.json"), false));

        guard.resolved();
        assertFalse(guard.pending());
        assertFalse(guard.requiresExplicitSaveDecision(original, original, false));
    }

    @Test void normalizedEquivalentPathsStillCountAsTheOriginal() {
        Path original = directory.resolve("folder").resolve("terrain.json");
        Path equivalent = directory.resolve("folder").resolve("..").resolve("folder")
                .resolve("terrain.json");
        assertTrue(ExternalConflictGuard.sameFileName(original, equivalent));
    }
}
