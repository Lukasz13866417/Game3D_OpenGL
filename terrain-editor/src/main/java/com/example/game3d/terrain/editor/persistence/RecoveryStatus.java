package com.example.game3d.terrain.editor.persistence;

import java.nio.file.Path;

/** Latest recovery outcome; failure is non-null only for FAILED. */
public record RecoveryStatus(RecoveryHealth health, Path path, Throwable failure) {
    public RecoveryStatus {
        if (health == null) throw new IllegalArgumentException("health == null");
        if ((health == RecoveryHealth.FAILED) != (failure != null)) {
            throw new IllegalArgumentException("Only FAILED has a failure");
        }
    }
}
