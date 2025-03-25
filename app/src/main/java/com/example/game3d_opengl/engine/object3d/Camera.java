package com.example.game3d_opengl.engine.object3d;

import android.opengl.Matrix;

import com.example.game3d_opengl.engine.util3d.vector.Vector3D;

public class Camera {

    // Store global screen dimensions here; set them once at app startup or in GLSurfaceView onSurfaceChanged
    public static int SCREEN_WIDTH = 0;
    public static int SCREEN_HEIGHT = 0;

    /**
     * Call this once you know the true screen size (e.g. in onSurfaceChanged).
     */
    public static void setGlobalScreenSize(int width, int height) {
        SCREEN_WIDTH = width;
        SCREEN_HEIGHT = height;
    }

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

    public void set(float eyeX, float eyeY, float eyeZ,
                    float lookX, float lookY, float lookZ,
                    float upX, float upY, float upZ) {

        this.eyeX  = eyeX;   this.eyeY  = eyeY;   this.eyeZ  = eyeZ;
        this.lookX = lookX;  this.lookY = lookY;  this.lookZ = lookZ;
        this.upX   = upX;    this.upY   = upY;    this.upZ   = upZ;

        Matrix.setLookAtM(viewMatrix, 0,
                this.eyeX,  this.eyeY,  this.eyeZ,
                this.lookX, this.lookY, this.lookZ,
                this.upX,   this.upY,   this.upZ);
    }

    /**
     * Same as setProjection(width,height), but automatically uses the global static
     * SCREEN_WIDTH and SCREEN_HEIGHT.
     */
    public void setProjectionAsScreen() {
        setProjection(SCREEN_WIDTH, SCREEN_HEIGHT);
    }

    public void setProjection(int width, int height) {
        float ratio = (float) width / height;
        // Simple frustum from -ratio..ratio, -1..1, with near=3 and far=7
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
        set(v.x,v.y,v.z,lookX,lookY,lookZ,upX,upY,upZ);
    }

    public void updateLookPos(Vector3D v){
        set(eyeX,eyeY,eyeZ,v.x,v.y,v.z,upX,upY,upZ);
    }



}
