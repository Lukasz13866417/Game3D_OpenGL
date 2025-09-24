package com.example.game3d_opengl.rendering.object3d;

import android.opengl.Matrix;

import com.example.game3d_opengl.rendering.GPUResourceOwner;

/**
 * Transform wrapper that composes an AbstractMesh3D.
 */
// TODO figure out what to do with this class.
public abstract class UnbatchedObject3D implements GPUResourceOwner {
    public float objX, objY, objZ, objYaw, objPitch, objRoll;
    private final float[] modelMatrix = new float[16];

    private void applyTransformations(float[] mMatrix) {
        Matrix.setIdentityM(mMatrix, 0);
        Matrix.translateM(mMatrix, 0, objX, objY, objZ);
        Matrix.rotateM(mMatrix, 0, objYaw, 0, 1, 0);
        Matrix.rotateM(mMatrix, 0, objPitch, 1, 0, 0);
        Matrix.rotateM(mMatrix, 0, objRoll, 0, 0, 1);
    }

    public void draw(float[] vpMatrix) {
        applyTransformations(this.modelMatrix);
        drawUnderlying(modelMatrix, vpMatrix);
    }

    protected abstract void drawUnderlying(float[] mMat, float[] vpMat);
    public abstract void reloadGPUResourcesRecursivelyOnContextLoss();
    public abstract void cleanupGPUResourcesRecursivelyOnContextLoss();
}