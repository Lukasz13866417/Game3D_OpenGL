package com.example.game3d.terrain.editor.persistence;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/** Exact raw-byte identity of one observed disk document. */
public record DiskVersion(
        Path path,
        String rawSha256,
        long byteLength,
        FileTime modifiedTime,
        String filesystemIdentity) {
    public DiskVersion {
        if (path == null || rawSha256 == null || modifiedTime == null
                || filesystemIdentity == null || filesystemIdentity.isEmpty()) {
            throw new IllegalArgumentException("Disk version fields are required");
        }
        path = path.toAbsolutePath().normalize();
        if (!rawSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("rawSha256 is malformed");
        }
        if (byteLength < 0L) throw new IllegalArgumentException("byteLength < 0");
    }

    /** Exact observed version; timestamps and path spelling are not authoritative. */
    public boolean sameContent(DiskVersion other) {
        return other != null && byteLength == other.byteLength
                && rawSha256.equals(other.rawSha256)
                && filesystemIdentity.equals(other.filesystemIdentity);
    }
}
