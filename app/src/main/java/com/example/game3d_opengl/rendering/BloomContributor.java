package com.example.game3d_opengl.rendering;

/**
 * Adds any emission energy missing from the ordinary scene bright pass before spatial bloom.
 * Implementations may temporarily bind their own targets, but must leave {@code destination}
 * bound with depth testing and blending disabled when they return.
 */
@FunctionalInterface
public interface BloomContributor {
    void contribute(
            RenderTarget destination,
            RenderTarget sceneSource,
            int viewportWidth,
            int viewportHeight);
}
