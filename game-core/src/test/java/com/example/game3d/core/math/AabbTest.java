package com.example.game3d.core.math;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AabbTest {
    @Test
    public void containsAcceptsTouchingAndNestedBounds() {
        Aabb outer = new Aabb(
                new Vec3(-2.0, -3.0, -4.0),
                new Vec3(5.0, 6.0, 7.0));

        assertTrue(outer.contains(outer));
        assertTrue(outer.contains(new Aabb(
                new Vec3(-2.0, -1.0, 0.0),
                new Vec3(3.0, 6.0, 7.0))));
    }

    @Test
    public void containsRejectsAnyAxisOutside() {
        Aabb outer = new Aabb(Vec3.ZERO, new Vec3(1.0, 1.0, 1.0));

        assertFalse(outer.contains(new Aabb(
                new Vec3(-0.001, 0.0, 0.0),
                new Vec3(1.0, 1.0, 1.0))));
        assertFalse(outer.contains(new Aabb(
                Vec3.ZERO,
                new Vec3(1.0, 1.001, 1.0))));
        assertFalse(outer.contains(new Aabb(
                Vec3.ZERO,
                new Vec3(1.0, 1.0, 1.001))));
    }
}
