package com.example.game3d.terrain.editor.compile;

import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.terrain.io.validation.ValidationProblem;

import java.util.List;
import java.util.Map;

public record CompileResult(
        CompileTicket ticket,
        long documentRevision,
        TerrainSnapshot snapshot,
        Map<String, Long> sourceSegmentIds,
        Map<String, Long> sourceAddonIds,
        List<ValidationProblem> problems,
        String compilerFailure) {
    public CompileResult {
        if (ticket == null) throw new IllegalArgumentException("ticket == null");
        sourceSegmentIds = Map.copyOf(sourceSegmentIds);
        sourceAddonIds = Map.copyOf(sourceAddonIds);
        problems = List.copyOf(problems);
    }

    /** Compatibility name for callers that display the source-document revision. */
    public long revision() { return documentRevision; }

    public CompileResult(
            CompileTicket ticket,
            long documentRevision,
            TerrainSnapshot snapshot,
            Map<String, Long> sourceSegmentIds,
            Map<String, Long> sourceAddonIds,
            List<ValidationProblem> problems) {
        this(ticket, documentRevision, snapshot, sourceSegmentIds, sourceAddonIds,
                problems, null);
    }

    /** Convenience constructor retained for deterministic compiler unit tests. */
    public CompileResult(
            long revision,
            TerrainSnapshot snapshot,
            Map<String, Long> sourceSegmentIds,
            Map<String, Long> sourceAddonIds,
            List<ValidationProblem> problems) {
        this(new CompileTicket(new java.util.UUID(0L, 0L), revision), revision,
                snapshot, sourceSegmentIds, sourceAddonIds, problems, null);
    }

    public boolean successful() { return snapshot != null && problems.stream()
            .noneMatch(problem -> problem.severity() == ValidationProblem.Severity.ERROR)
            && compilerFailure == null; }

    public boolean compilerFailed() { return compilerFailure != null; }
}
