package com.example.game3d_opengl.game.util;

import static org.junit.Assert.assertEquals;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import org.junit.Test;

public class GameMathTest {
    private static final float EPS = 1e-5f;

    @Test
    public void rotateAroundAxisTo_matches_allocating_rotation() {
        Vector3D axisPoint = new Vector3D(2.5f, -1.25f, 0.75f);
        Vector3D axisDirection = new Vector3D(3.0f, -2.0f, 5.0f);
        Vector3D point = new Vector3D(-4.5f, 6.25f, 1.5f);
        float angle = 0.73f;

        Vector3D rotated = GameMath.rotateAroundAxis(axisPoint, axisDirection, point, angle);
        GameMath.MutableVec3 out = new GameMath.MutableVec3();
        GameMath.rotateAroundAxisTo(out, axisPoint, axisDirection, point, angle);

        assertEquals(rotated.x, out.x, EPS);
        assertEquals(rotated.y, out.y, EPS);
        assertEquals(rotated.z, out.z, EPS);
    }

    @Test
    public void rotateAroundAxisTo_raw_components_matches_vector_overload() {
        Vector3D axisPoint = new Vector3D(-0.5f, 1.75f, 2.25f);
        Vector3D axisDirection = new Vector3D(-1.5f, 4.0f, 2.0f);
        Vector3D point = new Vector3D(0.125f, -3.25f, 5.5f);
        float angle = -1.1f;

        GameMath.MutableVec3 fromVectors = new GameMath.MutableVec3();
        GameMath.MutableVec3 fromComponents = new GameMath.MutableVec3();
        GameMath.rotateAroundAxisTo(fromVectors, axisPoint, axisDirection, point, angle);
        GameMath.rotateAroundAxisTo(
                fromComponents,
                axisPoint.x, axisPoint.y, axisPoint.z,
                axisDirection.x, axisDirection.y, axisDirection.z,
                point.x, point.y, point.z,
                angle
        );

        assertEquals(fromVectors.x, fromComponents.x, EPS);
        assertEquals(fromVectors.y, fromComponents.y, EPS);
        assertEquals(fromVectors.z, fromComponents.z, EPS);
    }

    @Test
    public void rayTriangleDistance_raw_components_matches_vector_overload() {
        Vector3D rayOrigin = new Vector3D(0.25f, 1.5f, 0.75f);
        Vector3D rayDirection = new Vector3D(0f, -1f, 0f);
        Vector3D vertex0 = new Vector3D(-1f, 0f, -1f);
        Vector3D vertex1 = new Vector3D(1f, 0f, -1f);
        Vector3D vertex2 = new Vector3D(0f, 0f, 1f);

        float fromVectors = GameMath.rayTriangleDistance(
                rayOrigin, rayDirection, vertex0, vertex1, vertex2
        );
        float fromComponents = GameMath.rayTriangleDistance(
                rayOrigin.x, rayOrigin.y, rayOrigin.z,
                rayDirection.x, rayDirection.y, rayDirection.z,
                vertex0.x, vertex0.y, vertex0.z,
                vertex1.x, vertex1.y, vertex1.z,
                vertex2.x, vertex2.y, vertex2.z
        );

        assertEquals(fromVectors, fromComponents, EPS);
    }
}
