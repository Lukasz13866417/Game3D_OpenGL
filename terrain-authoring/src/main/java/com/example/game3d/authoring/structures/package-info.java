/**
 * Reusable, renderer-free handwritten terrain structures.
 *
 * <p>Legacy constructors remain convenient for standalone use. Compositions should use the
 * source-prefix overloads (or builder {@code sourcePrefix}) so repeated structure types retain
 * distinct stable tile and addon source IDs. Advanced-grid randomized reservations consume only
 * the materialization seed; basic-grid layout variants are explicit authored choices.</p>
 */
package com.example.game3d.authoring.structures;
