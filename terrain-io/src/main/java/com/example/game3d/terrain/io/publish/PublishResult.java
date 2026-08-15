package com.example.game3d.terrain.io.publish;

import java.nio.file.Path;

public final class PublishResult {
    private final Path output;
    private final int entryCount;
    private final String catalogDigest;

    public PublishResult(Path output, int entryCount, String catalogDigest) {
        this.output = output;
        this.entryCount = entryCount;
        this.catalogDigest = catalogDigest;
    }

    public Path output() { return output; }
    public int entryCount() { return entryCount; }
    public String catalogDigest() { return catalogDigest; }
}
