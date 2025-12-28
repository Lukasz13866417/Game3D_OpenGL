package com.example.game3d_opengl.rendering;

import static com.example.game3d_opengl.rendering.util3d.vector.Vector3D.sub;

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

    public Vector3D getUp() {
        return new Vector3D(upX, upY, upZ).normalized();
    }

    public Vector3D getRight() {
        return getForward().crossProduct(getUp()).normalized();
    }

}
