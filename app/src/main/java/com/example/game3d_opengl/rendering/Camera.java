package com.example.game3d_opengl.rendering;

import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.sub;

import android.opengl.Matrix;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class Camera {
    private static final float DEFAULT_FRUSTUM_HALF_HEIGHT = 1f;
    private static final float DEFAULT_NEAR_CLIP = 3f;
    private static final float DEFAULT_FAR_CLIP = 160f;
    // Matches the old default frustum shape when near=3 and top=1: fovY ~= 36.87 deg
    private static final float DEFAULT_FOV_Y_DEGREES = 36.869896f;

    private enum ProjectionMode {
        SYMMETRIC_FRUSTUM,
        CUSTOM_FRUSTUM,
        FOV
    }

    private final float[] viewMatrix       = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] vpMatrix         = new float[16];

    private float eyeX, eyeY, eyeZ;    // where the camera is
    private float lookX, lookY, lookZ; // where the camera is looking
    private float upX,   upY,   upZ;   // which way is up

    private int projectionWidth = 1;
    private int projectionHeight = 1;
    private ProjectionMode projectionMode = ProjectionMode.SYMMETRIC_FRUSTUM;

    private float nearClip = DEFAULT_NEAR_CLIP;
    private float farClip = DEFAULT_FAR_CLIP;

    // SYMMETRIC_FRUSTUM params (left/right derived from aspect ratio)
    private float frustumHalfHeight = DEFAULT_FRUSTUM_HALF_HEIGHT;

    // CUSTOM_FRUSTUM params
    private float frustumLeft = -1f;
    private float frustumRight = 1f;
    private float frustumBottom = -1f;
    private float frustumTop = 1f;

    // FOV params
    private float fovYDegrees = DEFAULT_FOV_Y_DEGREES;

    public Camera() {
        set(0f, 0f, 3f,
                0f, 0f, 0f,
                0f, 1f, 0f);
    }

    public Camera(Vector3D eye, Vector3D look, Vector3D up) {
        set(eye, look, up);
    }

    /** Back-compat setter (no right vector). */
    public void set(Vector3D eye, Vector3D look, Vector3D up){
        set(eye.x, eye.y, eye.z,
                look.x, look.y, look.z,
                up.x,   up.y,   up.z);
    }

    /** Back-compat setter (no right vector). */
    public void set(float eyeX, float eyeY, float eyeZ,
                    float lookX, float lookY, float lookZ,
                    float upX,   float upY,   float upZ) {

        this.eyeX  = eyeX;   this.eyeY  = eyeY;   this.eyeZ  = eyeZ;
        this.lookX = lookX;  this.lookY = lookY;  this.lookZ = lookZ;
        this.upX   = upX;    this.upY   = upY;    this.upZ   = upZ;

        Matrix.setLookAtM(viewMatrix, 0,
                this.eyeX,  this.eyeY,  this.eyeZ,
                this.lookX, this.lookY, this.lookZ,
                this.upX,   this.upY,   this.upZ);
    }

    public void setProjectionAsScreen() {
        setProjection(ScreenInfo.getScreenW(), ScreenInfo.getScreenH());
    }

    public void setProjection(int width, int height) {
        projectionWidth = Math.max(1, width);
        projectionHeight = Math.max(1, height);
        rebuildProjectionMatrix();
    }

    /**
     * Set a symmetric frustum where horizontal planes are derived from aspect ratio.
     */
    public void setSymmetricFrustum(float halfHeight, float near, float far) {
        if (halfHeight <= 0f) {
            throw new IllegalArgumentException("halfHeight must be > 0");
        }
        validateNearFar(near, far);
        projectionMode = ProjectionMode.SYMMETRIC_FRUSTUM;
        frustumHalfHeight = halfHeight;
        nearClip = near;
        farClip = far;
        rebuildProjectionMatrix();
    }

    /**
     * Set all six frustum parameters directly.
     */
    public void setFrustum(float left, float right, float bottom, float top, float near, float far) {
        if (!(left < right)) {
            throw new IllegalArgumentException("left must be < right");
        }
        if (!(bottom < top)) {
            throw new IllegalArgumentException("bottom must be < top");
        }
        validateNearFar(near, far);
        projectionMode = ProjectionMode.CUSTOM_FRUSTUM;
        frustumLeft = left;
        frustumRight = right;
        frustumBottom = bottom;
        frustumTop = top;
        nearClip = near;
        farClip = far;
        rebuildProjectionMatrix();
    }

    /**
     * Set vertical FOV (degrees) for perspective projection with current clip planes.
     */
    public void setFov(float fovYDegrees) {
        validateFov(fovYDegrees);
        projectionMode = ProjectionMode.FOV;
        this.fovYDegrees = fovYDegrees;
        rebuildProjectionMatrix();
    }

    /**
     * Set vertical FOV (degrees) and clip planes for perspective projection.
     */
    public void setFov(float fovYDegrees, float near, float far) {
        validateFov(fovYDegrees);
        validateNearFar(near, far);
        projectionMode = ProjectionMode.FOV;
        this.fovYDegrees = fovYDegrees;
        nearClip = near;
        farClip = far;
        rebuildProjectionMatrix();
    }

    /**
     * Update clip planes while preserving current projection mode.
     */
    public void setClipPlanes(float near, float far) {
        validateNearFar(near, far);
        nearClip = near;
        farClip = far;
        rebuildProjectionMatrix();
    }

    private static void validateNearFar(float near, float far) {
        if (near <= 0f) {
            throw new IllegalArgumentException("near must be > 0");
        }
        if (!(far > near)) {
            throw new IllegalArgumentException("far must be > near");
        }
    }

    private static void validateFov(float fovYDegrees) {
        if (!(fovYDegrees > 0f && fovYDegrees < 179f)) {
            throw new IllegalArgumentException("fovYDegrees must be in (0, 179)");
        }
    }

    private void rebuildProjectionMatrix() {
        float ratio = (float) projectionWidth / projectionHeight;
        switch (projectionMode) {
            case CUSTOM_FRUSTUM:
                Matrix.frustumM(projectionMatrix, 0,
                        frustumLeft, frustumRight,
                        frustumBottom, frustumTop,
                        nearClip, farClip);
                break;
            case FOV:
                Matrix.perspectiveM(projectionMatrix, 0,
                        fovYDegrees, ratio, nearClip, farClip);
                break;
            case SYMMETRIC_FRUSTUM:
            default:
                Matrix.frustumM(projectionMatrix, 0,
                        -ratio * frustumHalfHeight, ratio * frustumHalfHeight,
                        -frustumHalfHeight, frustumHalfHeight,
                        nearClip, farClip);
                break;
        }
    }

    public float[] getViewProjectionMatrix() {
        Matrix.multiplyMM(vpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
        return vpMatrix;
    }

    public void updateEyePos(Vector3D v){
        set(v.x, v.y, v.z, lookX, lookY, lookZ, upX, upY, upZ);
    }

    public void updateLookPos(Vector3D v){
        set(eyeX, eyeY, eyeZ, v.x, v.y, v.z, upX, upY, upZ);
    }

    public void updateUp(Vector3D u){
        set(eyeX, eyeY, eyeZ, lookX, lookY, lookZ, u.x, u.y, u.z);
    }

    // -------------------------------
    // 180° rotations about camera axes
    // -------------------------------

    /** Yaw 180 deg about current Up: keep Up, flip Forward & Right. */
    public void rotate180AroundUp() {
        // Forward vector f = look - eye -> new look = eye - f
        float nx = 2f * eyeX - lookX;
        float ny = 2f * eyeY - lookY;
        float nz = 2f * eyeZ - lookZ;
        // Up unchanged
        set(eyeX, eyeY, eyeZ, nx, ny, nz, upX, upY, upZ);
    }

    /** Pitch 180 deg about current Right: keep Right, flip Forward & Up.
     *  For LookAt representation, this is: new look = eye - f, new up = -up. */
    public void rotate180AroundRight() {
        float nx = 2f * eyeX - lookX;
        float ny = 2f * eyeY - lookY;
        float nz = 2f * eyeZ - lookZ;
        set(eyeX, eyeY, eyeZ, nx, ny, nz, -upX, -upY, -upZ);
    }

    /** Roll 180 deg about current Forward: keep Forward, flip Right & Up.
     *  For LookAt representation, this is: look unchanged, up = -up. */
    public void rotate180AroundForward() {
        set(eyeX, eyeY, eyeZ, lookX, lookY, lookZ, -upX, -upY, -upZ);
    }

    public Vector3D getForward() {
        return sub(new Vector3D(lookX, lookY, lookZ),
                        new Vector3D(eyeX, eyeY, eyeZ)).normalized();
    }

    /** Scalar accessors avoid allocating a temporary vector in per-frame render plumbing. */
    public float getEyeX() {
        return eyeX;
    }

    public float getEyeY() {
        return eyeY;
    }

    public float getEyeZ() {
        return eyeZ;
    }

    public Vector3D getUp() {
        return new Vector3D(upX, upY, upZ).normalized();
    }

    public Vector3D getRight() {
        return getForward().crossProduct(getUp()).normalized();
    }

}
