/**
 * Legacy Android terrain compatibility implementation.
 *
 * <p>Production gameplay must not depend on this package or any of its subpackages. Canonical
 * terrain definitions live in {@code :game-core}, authoring and streaming live in
 * {@code :terrain-authoring}, and Android rendering lives in
 * {@code game.terrain.presentation}. These classes remain temporarily for the old diagnostic
 * stages and algorithm-regression tests while those tests are moved to shared modules.</p>
 */
package com.example.game3d_opengl.game.terrain.terrain_api;
