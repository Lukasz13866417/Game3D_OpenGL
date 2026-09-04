package com.example.game3d.terrain.io;

/**
 * Indicates that an in-memory terrain document cannot be represented by the strict JSON format.
 *
 * <p>This is distinct from semantic validation: authoring tools may save semantically invalid
 * drafts, but a successful encode must always produce JSON that this codec can read back.</p>
 */
public final class TerrainEncodingException extends IllegalArgumentException {
    public TerrainEncodingException(String message) {
        super(message);
    }

    public TerrainEncodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
