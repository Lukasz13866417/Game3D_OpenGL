package com.example.game3d_opengl.game.stage.stages.test.util;

import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

/**
 * Renders four 3D points and the four edges connecting them like a rectangle.
 * The input points are expected in clockwise order.
 * Internally uses a {@link LineSet3D} for rendering.
 */
public class FourPoints3D {

    private static final int[][] RECT_EDGES = new int[][]{
            {0, 1}, {1, 2}, {2, 3}, {3, 0}
    };

    private final LineSet3D lineSet;

    /**
     * Creates a FourPoints3D from four points provided separately, in clockwise order.
     */
    public FourPoints3D(Vector3D p0, Vector3D p1, Vector3D p2, Vector3D p3) {
        this(new Vector3D[]{p0, p1, p2, p3});
    }

    /**
     * Creates a FourPoints3D from an array of four points, in clockwise order.
     */
    public FourPoints3D(Vector3D[] pointsClockwise) {
        if (pointsClockwise == null || pointsClockwise.length != 4) {
            throw new IllegalArgumentException("FourPoints3D requires exactly 4 points");
        }
        FColor lineColor = FColor.CLR(1f, 1f, 1f);
        FColor pointColor = FColor.CLR(1f, 1f, 1f);
        this.lineSet = new LineSet3D(
                new Vector3D[]{
                        pointsClockwise[0].addY(0.01f),
                        pointsClockwise[1].addY(0.01f),
                        pointsClockwise[2].addY(0.01f),
                        pointsClockwise[3].addY(0.01f),
                }, RECT_EDGES, lineColor, pointColor);
    }

    public void draw(float[] vpMatrix) {
        lineSet.draw(vpMatrix);
    }
}


