package com.example.game3d.terrain.editor.persistence;

import com.example.game3d.terrain.editor.state.EditorState;

import java.util.Optional;

/** Result of a conditional save; conflicts never replace the target. */
public sealed interface SaveResult permits SaveResult.Saved, SaveResult.Conflict {
    record Saved(EditorState state, DiskVersion diskVersion) implements SaveResult {
        public Saved {
            if (state == null || diskVersion == null) {
                throw new IllegalArgumentException("Saved result fields are required");
            }
        }
    }

    record Conflict(
            ExpectedDiskVersion expected,
            Optional<DiskVersion> actual) implements SaveResult {
        public Conflict {
            if (expected == null || actual == null) {
                throw new IllegalArgumentException("Conflict result fields are required");
            }
        }
    }
}
