package com.example.game3d.core.simulation;

import com.example.game3d.core.math.Vec3;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CylinderColliderTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    public void supportIsExactForCapAndRimDirections() {
        Vec3 center = new Vec3(1.0, 2.0, 3.0);
        Vec3 axis = new Vec3(1.0, 0.0, 0.0);

        Vec3 cap = CylinderCollider.support(center, axis, 0.5, 2.0,
                new Vec3(1.0, 0.0, 0.0));
        assertEquals(1.5, cap.x, EPSILON);
        assertEquals(2.0, cap.y, EPSILON);

        Vec3 bottom = CylinderCollider.support(center, axis, 0.5, 2.0,
                new Vec3(0.0, -1.0, 0.0));
        assertEquals(1.0, bottom.x, EPSILON);
        assertEquals(0.0, bottom.y, EPSILON);
    }

    @Test
    public void boundsIncludeBothCapsAndCircularRadius() {
        Vec3 center = new Vec3(0.0, 1.0, 0.0);
        assertEquals(-0.5, CylinderCollider.bounds(center,
                new Vec3(1.0, 0.0, 0.0), 0.5, 2.0).min.x, EPSILON);
        assertEquals(-1.0, CylinderCollider.bounds(center,
                new Vec3(1.0, 0.0, 0.0), 0.5, 2.0).min.y, EPSILON);
    }

    @Test
    public void diagonalSupportMatchesAnalyticCylinderFormula() {
        Vec3 direction = new Vec3(1.0, 2.0, 0.0);
        Vec3 support = CylinderCollider.support(Vec3.ZERO,
                new Vec3(1.0, 0.0, 0.0), 0.5, 2.0, direction);

        assertEquals(0.5, support.x, EPSILON);
        assertEquals(2.0, support.y, EPSILON);
        assertEquals(0.0, support.z, EPSILON);
        assertEquals(4.5, support.dot(direction), EPSILON);
    }

    @Test
    public void rimSamplesLieOnBothCapPlanesAndAtExactRadius() {
        Vec3 center = new Vec3(2.0, 3.0, 4.0);
        Vec3 axis = new Vec3(1.0, 0.0, 0.0);
        java.util.List<Vec3> samples = CylinderCollider.rimSamples(
                center, axis, 0.4, 1.2, 12);

        assertEquals(24, samples.size());
        for (Vec3 sample : samples) {
            Vec3 offset = sample.subtract(center);
            assertEquals(0.4, Math.abs(offset.dot(axis)), EPSILON);
            Vec3 radial = offset.subtract(axis.multiply(offset.dot(axis)));
            assertEquals(1.2, radial.length(), EPSILON);
        }
    }

    @Test
    public void boundsAreCorrectForVerticalAxisToo() {
        com.example.game3d.core.math.Aabb bounds = CylinderCollider.bounds(
                Vec3.ZERO, Vec3.UP, 2.0, 0.5);

        assertEquals(-0.5, bounds.min.x, EPSILON);
        assertEquals(-2.0, bounds.min.y, EPSILON);
        assertEquals(0.5, bounds.max.z, EPSILON);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rimSamplesRejectFewerThanThreeSegments() {
        CylinderCollider.rimSamples(Vec3.ZERO,
                new Vec3(1.0, 0.0, 0.0), 0.5, 1.0, 2);
    }
}
