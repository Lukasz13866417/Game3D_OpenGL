package com.example.game3d.terrain.editor.compile;

import com.example.game3d.core.terrain.TerrainSnapshot;
import com.example.game3d.terrain.io.validation.ValidationProblem;

import java.util.List;
import java.util.Map;

public record CompileResult(
        long revision,
        TerrainSnapshot snapshot,
        Map<String, Long> sourceSegmentIds,
        Map<String, Long> sourceAddonIds,
        List<ValidationProblem> problems) {
    public CompileResult {
        sourceSegmentIds = Map.copyOf(sourceSegmentIds);
        sourceAddonIds = Map.copyOf(sourceAddonIds);
        problems = List.copyOf(problems);
    }

    public boolean successful() { return snapshot != null && problems.stream()
            .noneMatch(problem -> problem.severity() == ValidationProblem.Severity.ERROR); }
}
