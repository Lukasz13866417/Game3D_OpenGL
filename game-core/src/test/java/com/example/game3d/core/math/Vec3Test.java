package com.example.game3d.core.math;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

public final class Vec3Test {
    @Test
    public void equalComponentsHaveValueEqualityAndMatchingHashCodes() {
        Vec3 first = new Vec3(1.25, -2.5, 3.75);
        Vec3 second = new Vec3(1.25, -2.5, 3.75);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new Vec3(1.25, -2.5, 3.7500001));
    }

    @Test
    public void positiveAndNegativeZeroAreEquivalent() {
        Vec3 positive = new Vec3(0.0, 0.0, 0.0);
        Vec3 negative = new Vec3(-0.0, 0.0, -0.0);

        assertEquals(positive, negative);
        assertEquals(positive.hashCode(), negative.hashCode());
        assertFalse(positive.equals(null));
    }
}
