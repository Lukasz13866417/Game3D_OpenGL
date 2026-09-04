package com.example.game3d.terrain.editor.compile;

import com.example.game3d.terrain.io.model.TerrainSourceDocument;
import com.example.game3d.terrain.io.resolve.TerrainDocumentRepository;
import com.example.game3d.terrain.io.validation.ValidationProblem;

import java.util.List;
import java.util.Objects;

/** Immutable input captured on the JavaFX thread before background compilation starts. */
public record CompileRequest(
        CompileTicket ticket,
        long documentRevision,
        TerrainSourceDocument document,
        TerrainDocumentRepository references,
        List<ValidationProblem> sessionProblems) {
    public CompileRequest {
        Objects.requireNonNull(ticket, "ticket");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(references, "references");
        sessionProblems = List.copyOf(Objects.requireNonNull(sessionProblems, "sessionProblems"));
    }

    public CompileRequest(
            CompileTicket ticket,
            long documentRevision,
            TerrainSourceDocument document,
            TerrainDocumentRepository references) {
        this(ticket, documentRevision, document, references, List.of());
    }
}
