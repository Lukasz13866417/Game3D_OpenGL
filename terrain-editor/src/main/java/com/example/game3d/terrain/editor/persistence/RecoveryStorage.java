package com.example.game3d.terrain.editor.persistence;

import java.io.IOException;
import java.nio.file.Path;

/** Injectable atomic storage boundary for recovery tests and platform integration. */
public interface RecoveryStorage {
    void writeUtf8(Path path, String content) throws IOException;
    void deleteIfExists(Path path) throws IOException;
}
