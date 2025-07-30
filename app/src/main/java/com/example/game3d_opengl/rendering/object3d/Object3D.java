package com.example.game3d_opengl.rendering.object3d;



import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import android.opengl.Matrix;
import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class Object3D extends AbsoluteObject3D {

    public float objX, objY, objZ, objYaw, objPitch, objRoll;
    private final float[] modelMatrix;

    protected Object3D(Builder builder) {
        super(builder);
        this.objX = builder.objX;
        this.objY = builder.objY;
        this.objZ = builder.objZ;
        this.objYaw = builder.objYaw;
        this.objPitch = builder.objPitch;
        this.objRoll = builder.objRoll;
        this.modelMatrix = new float[16];
    }

    private void applyTransformations(float[] mMatrix) {
        Matrix.setIdentityM(mMatrix, 0);
        Matrix.translateM(mMatrix, 0, objX, objY, objZ);
        Matrix.rotateM(mMatrix, 0, objYaw, 0, 1, 0);
        Matrix.rotateM(mMatrix, 0, objPitch, 1, 0, 0);
        Matrix.rotateM(mMatrix, 0, objRoll, 0, 0, 1);
    }

    @Override
    public void draw(float[] vpMatrix) {
        applyTransformations(this.modelMatrix);
        super.draw(modelMatrix, vpMatrix);
    }

    public static class Builder extends AbsoluteObject3D.BaseBuilder<Object3D, Builder> {
        protected float objX = 0, objY = 0, objZ = 0;
        protected float objYaw = 0, objPitch = 0, objRoll = 0;

        public Builder() {
            super();
        }

        public Builder position(float x, float y, float z) {
            this.objX = x;
            this.objY = y;
            this.objZ = z;
            return this;
        }

        public Builder angles(float yaw, float pitch, float roll) {
            this.objYaw = yaw;
            this.objPitch = pitch;
            this.objRoll = roll;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public void checkValid() {
            assert faces != null;
            assert verts != null;
        }

        @Override
        public Object3D create() {
            return new Object3D(this);
        }
    }
}