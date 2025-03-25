package com.example.game3d_opengl.engine.object3d;

import android.opengl.Matrix;

import com.example.game3d_opengl.engine.util3d.vector.Vector3D;


/**
 * Icon is a thin wrapper around an Object3D for rendering
 * a small 3D shape at a given (centerX, centerY) in NDC coords.
 *
 * It uses a static aspect ratio for the orthographic projection.
 * Call Icon.setAspectRatio(...) from your renderer once you know
 * the screen width/height.
 */
public class Icon {

    /** A shared aspect ratio used by all Icon objects. */
    private static float sAspectRatio = 1f;

    /**
     * Updates the static aspect ratio for all Icon instances.
     * Typically, you'd call this in onSurfaceChanged(...) or whenever
     * the viewport size changes.
     *
     * @param screenWidth  the width of the viewport in pixels
     * @param screenHeight the height of the viewport in pixels
     */
    public static void setAspectRatio(int screenWidth, int screenHeight) {
        if (screenHeight == 0) {
            // Avoid divide-by-zero; default aspect to 1
            sAspectRatio = 1f;
        } else {
            sAspectRatio = (float) screenWidth / (float) screenHeight;
        }
    }

    // The underlying 3D object we want to draw
    private final Object3D object3D;

    // Icon center in NDC: [-1..1] range
    private float centerX;
    private float centerY;

    // Minimal "camera" and "projection"
    private final float[] viewMatrix       = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] vpMatrix         = new float[16];

    /**
     * Constructs an Icon. We require geometry is centered around (0,0,0).
     */
    public Icon(Object3D.Builder builder, float centerX, float centerY) {
        assertGeometryCentered(builder.verts);
        this.object3D = builder.buildObject();
        this.centerX  = centerX;
        this.centerY  = centerY;
    }

    /**
     * Check that the builder's verts are centered around (0,0,0).
     * Throws AssertionError if not near (0,0,0).
     */
    private void assertGeometryCentered(Vector3D[] verts) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (Vector3D v : verts) {
            if (v.x < minX) minX = v.x;
            if (v.x > maxX) maxX = v.x;
            if (v.y < minY) minY = v.y;
            if (v.y > maxY) maxY = v.y;
            if (v.z < minZ) minZ = v.z;
            if (v.z > maxZ) maxZ = v.z;
        }
        float cx = 0.5f * (minX + maxX);
        float cy = 0.5f * (minY + maxY);
        float cz = 0.5f * (minZ + maxZ);

        float eps = 0.0001f;
        if (Math.abs(cx) > eps || Math.abs(cy) > eps || Math.abs(cz) > eps) {
            throw new AssertionError(
                "Icon geometry not centered around (0,0,0). " +
                "BBox center is (" + cx + ", " + cy + ", " + cz + ")"
            );
        }
    }

    /**
     * Update the icon's NDC center at runtime if needed.
     */
    public void setCenter(float x, float y) {
        this.centerX = x;
        this.centerY = y;
    }

    /**
     * Renders this icon. Uses the static aspect ratio for the orthographic projection.
     * Camera is placed at (0,0,2), looking at (0,0,0).
     */
    public void draw() {
        // 1) A minimal "camera" looking at the origin
        Matrix.setLookAtM(
            viewMatrix, 0,
            0f, 0f, 2f,   // camera at z=2
            0f, 0f, 0f,   // looking at origin
            0f, 1f, 0f    // up is +Y
        );

        // 2) Orthographic projection that accounts for the static aspect ratio:
        //
        //    If sAspectRatio > 1, the screen is wide, so we expand X range.
        //    If sAspectRatio < 1, the screen is tall, so we expand Y range.
        //
        //    That keeps geometry from looking stretched or squashed.
        float left   = -sAspectRatio;
        float right  =  sAspectRatio;
        float bottom = -1f;
        float top    =  1f;
        float near   =  1f;
        float far    = 10f;
        Matrix.orthoM(projectionMatrix, 0, left, right, bottom, top, near, far);

        // 3) Combine them: view * projection
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

        // 4) Offset the underlying object in NDC so it appears at (centerX, centerY).
        object3D.objX = centerX;
        object3D.objY = centerY;
        object3D.objZ = 0f;  // camera is at z=2

        // 5) Finally, draw the underlying object using the combined matrix
        object3D.draw(vpMatrix);
    }
}
