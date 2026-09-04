package com.example.game3d.terrain.editor.persistence;

/** Target state required immediately before an atomic save replaces it. */
public record ExpectedDiskVersion(Kind kind, DiskVersion exactVersion) {
    public enum Kind { ABSENT, EXACT }

    public ExpectedDiskVersion {
        if (kind == null) throw new IllegalArgumentException("kind == null");
        if ((kind == Kind.EXACT) != (exactVersion != null)) {
            throw new IllegalArgumentException("Only EXACT has an exactVersion");
        }
    }

    public static ExpectedDiskVersion absent() {
        return new ExpectedDiskVersion(Kind.ABSENT, null);
    }

    public static ExpectedDiskVersion exact(DiskVersion version) {
        return new ExpectedDiskVersion(Kind.EXACT, version);
    }
}
