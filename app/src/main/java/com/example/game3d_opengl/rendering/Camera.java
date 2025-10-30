package com.example.game3d_opengl.rendering;

import android.opengl.Matrix;

import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class Camera {

    private final float[] viewMatrix       = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] vpMatrix         = new float[16];

    private float eyeX, eyeY, eyeZ;    // where the camera is
    private float lookX, lookY, lookZ; // where the camera is looking
    private float upX,   upY,   upZ;   // which way is up

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

    /** Optional expanded API: set with an explicit right vector.
     *  We'll re-orthonormalize to keep the basis tidy and right-handed. */
    public void set(Vector3D eye, Vector3D look, Vector3D up, Vector3D right) {
        // Forward from eye->look
        Vector3D f = sub(look, eye);
        if (len2(f) == 0) f = new Vector3D(0, 0, -1);
        f = normalize(f);

        Vector3D r = normalize(right);
        // Recompute up to be orthonormal and right-handed: u = r x f
        Vector3D u = cross(r, f);
        if (len2(u) == 0) {
            // Fallback: derive r from f and provided up, then u = r x f
            r = normalize(cross(f, up));
            u = cross(r, f);
        } else {
            u = normalize(u);
            // Nudge r to exact orthonormality as well: r = normalize(f x u)
            r = normalize(cross(f, u));
        }

        Vector3D lookFixed = add(eye, f);
        set(eye, lookFixed, u);
    }

    public void setProjectionAsScreen() {
        setProjection(ScreenInfo.getScreenW(), ScreenInfo.getScreenH());
    }

    public void setProjection(int width, int height) {
        float ratio = (float) width / height;
        // Simple frustum from -ratio..ratio, -1..1, near=3, far=160
        Matrix.frustumM(projectionMatrix, 0,
                -ratio, ratio,
                -1, 1,
                3, 160);
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

    /** Yaw 180° about current Up: keep Up, flip Forward & Right. */
    public void rotate180AroundUp() {
        // Forward vector f = look - eye -> new look = eye - f
        float nx = 2f * eyeX - lookX;
        float ny = 2f * eyeY - lookY;
        float nz = 2f * eyeZ - lookZ;
        // Up unchanged
        set(eyeX, eyeY, eyeZ, nx, ny, nz, upX, upY, upZ);
    }

    /** Pitch 180° about current Right: keep Right, flip Forward & Up.
     *  For LookAt representation, this is: new look = eye - f, new up = -up. */
    public void rotate180AroundRight() {
        float nx = 2f * eyeX - lookX;
        float ny = 2f * eyeY - lookY;
        float nz = 2f * eyeZ - lookZ;
        set(eyeX, eyeY, eyeZ, nx, ny, nz, -upX, -upY, -upZ);
    }

    /** Roll 180° about current Forward: keep Forward, flip Right & Up.
     *  For LookAt representation, this is: look unchanged, up = -up. */
    public void rotate180AroundForward() {
        set(eyeX, eyeY, eyeZ, lookX, lookY, lookZ, -upX, -upY, -upZ);
    }

    // -------------------------------
    // Convenience getters
    // -------------------------------

    /** Forward = normalize(look - eye). */
    public Vector3D getForward() {
        return normalizeSafe(sub(new Vector3D(lookX, lookY, lookZ),
                        new Vector3D(eyeX, eyeY, eyeZ)),
                new Vector3D(0, 0, -1));
    }

    /** Up as stored (will be normalized). */
    public Vector3D getUp() {
        return normalizeSafe(new Vector3D(upX, upY, upZ), new Vector3D(0, 1, 0));
    }

    /** Right = normalize( forward × up ) (right-handed). */
    public Vector3D getRight() {
        return normalizeSafe(cross(getForward(), getUp()), new Vector3D(1, 0, 0));
    }

    // -------------------------------
    // Tiny vec math (no GC storms)
    // -------------------------------

    private static Vector3D sub(Vector3D a, Vector3D b) {
        return new Vector3D(a.x - b.x, a.y - b.y, a.z - b.z);
    }

    private static Vector3D add(Vector3D a, Vector3D b) {
        return new Vector3D(a.x + b.x, a.y + b.y, a.z + b.z);
    }

    private static float len2(Vector3D v) { return v.x*v.x + v.y*v.y + v.z*v.z; }

    private static Vector3D normalize(Vector3D v) {
        float l2 = len2(v);
        if (l2 == 0f) return new Vector3D(0,0,0);
        float inv = 1.0f / (float)Math.sqrt(l2);
        return new Vector3D(v.x * inv, v.y * inv, v.z * inv);
    }

    private static Vector3D normalizeSafe(Vector3D v, Vector3D fallbackUnit) {
        Vector3D n = normalize(v);
        if (len2(n) == 0f) return fallbackUnit; // assume fallback is unit
        return n;
    }

    private static Vector3D cross(Vector3D a, Vector3D b) {
        return new Vector3D(
                a.y * b.z - a.z * b.y,
                a.z * b.x - a.x * b.z,
                a.x * b.y - a.y * b.x
        );
    }
}
