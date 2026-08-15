package com.example.game3d.terrain.io.model;

import java.util.Objects;

public final class LevelEntry {
    public enum Kind { STRUCTURE_REFERENCE, LEVEL_REFERENCE, INLINE_STRUCTURE }

    private final String sourceId;
    private final Kind kind;
    private final String referenceId;
    private final StructureDocument inlineStructure;

    private LevelEntry(String sourceId, Kind kind, String referenceId, StructureDocument inlineStructure) {
        this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.referenceId = referenceId;
        this.inlineStructure = inlineStructure;
    }

    public static LevelEntry reference(String sourceId, String structureRef) {
        return new LevelEntry(sourceId, Kind.STRUCTURE_REFERENCE,
                Objects.requireNonNull(structureRef, "structureRef"), null);
    }

    /** Supported for composition and cycle diagnostics; editors normally emit structure refs. */
    public static LevelEntry levelReference(String sourceId, String levelRef) {
        return new LevelEntry(sourceId, Kind.LEVEL_REFERENCE,
                Objects.requireNonNull(levelRef, "levelRef"), null);
    }

    public static LevelEntry inline(String sourceId, StructureDocument structure) {
        return new LevelEntry(sourceId, Kind.INLINE_STRUCTURE, null,
                Objects.requireNonNull(structure, "structure"));
    }

    public String sourceId() { return sourceId; }
    public Kind kind() { return kind; }
    public boolean isReference() { return referenceId != null; }
    public String structureRef() {
        return kind == Kind.STRUCTURE_REFERENCE ? referenceId : null;
    }
    public String levelRef() { return kind == Kind.LEVEL_REFERENCE ? referenceId : null; }
    public String referenceId() { return referenceId; }
    public StructureDocument inlineStructure() { return inlineStructure; }
}
