package com.example.game3d.terrain.editor.ui;

import com.example.game3d.core.math.Vec3;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortalPreviewBasisTest {
    @Test void canonicalAuthoredAxesMapIntoJavaFxCoordinates() {
        PortalPreviewBasis basis = PortalPreviewBasis.from(
                new Vec3(0, 0, -1), new Vec3(0, 1, 0));

        assertVec(new Vec3(1, 0, 0), basis.right);
        assertVec(new Vec3(0, -1, 0), basis.up);
        assertVec(new Vec3(0, 0, -1), basis.forward);
    }

    @Test void angledNonOrthogonalInputBecomesAnOrthonormalRightHandedBasis() {
        Vec3 authoredForward = new Vec3(2, .7, -3);
        Vec3 authoredUp = new Vec3(.2, 4, .5);
        PortalPreviewBasis basis = PortalPreviewBasis.from(
                authoredForward, authoredUp);

        Vec3 expectedForward = new Vec3(
                authoredForward.x, -authoredForward.y, authoredForward.z)
                .normalized();
        assertVec(expectedForward, basis.forward);
        assertEquals(1.0, basis.right.length(), 1.0e-12);
        assertEquals(1.0, basis.up.length(), 1.0e-12);
        assertEquals(1.0, basis.forward.length(), 1.0e-12);
        assertEquals(0.0, basis.right.dot(basis.up), 1.0e-12);
        assertEquals(0.0, basis.right.dot(basis.forward), 1.0e-12);
        assertEquals(0.0, basis.up.dot(basis.forward), 1.0e-12);
        assertVec(basis.forward, basis.right.cross(basis.up));
    }

    @Test void degenerateAuthoredAxesStillProduceAUsableRigidBasis() {
        PortalPreviewBasis basis = PortalPreviewBasis.from(Vec3.ZERO, Vec3.ZERO);
        assertEquals(1.0, basis.right.length(), 1.0e-12);
        assertEquals(1.0, basis.up.length(), 1.0e-12);
        assertEquals(1.0, basis.forward.length(), 1.0e-12);
        assertVec(basis.forward, basis.right.cross(basis.up));
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 1.0e-12);
        assertEquals(expected.y, actual.y, 1.0e-12);
        assertEquals(expected.z, actual.z, 1.0e-12);
    }
}
