package com.example.game3d.terrain.io.publish;

/** Indicates that a published runtime artifact is malformed or has been corrupted. */
public final class RuntimeCatalogException extends Exception {
    public RuntimeCatalogException(String message) {
        super(message);
    }

    public RuntimeCatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}
