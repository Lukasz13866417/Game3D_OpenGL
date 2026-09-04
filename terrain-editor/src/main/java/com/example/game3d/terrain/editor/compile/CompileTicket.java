package com.example.game3d.terrain.editor.compile;

import java.util.Objects;
import java.util.UUID;

/** Globally unambiguous identity for one workspace compilation attempt. */
public record CompileTicket(UUID workspaceId, long sequence) {
    public CompileTicket {
        Objects.requireNonNull(workspaceId, "workspaceId");
        if (sequence < 0L) throw new IllegalArgumentException("sequence < 0");
    }
}
