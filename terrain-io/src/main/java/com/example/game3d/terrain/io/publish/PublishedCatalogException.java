package com.example.game3d.terrain.io.publish;

/** A valid JSON envelope whose runtime gameplay content cannot be accepted. */
public final class PublishedCatalogException extends Exception {
    public PublishedCatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}
