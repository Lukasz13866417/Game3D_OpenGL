package com.example.game3d_opengl.rendering.object3d;



import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import android.opengl.Matrix;

import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class Object3D {

    public float objX, objY, objZ, objYaw, objPitch, objRoll;
    private final float[] mvpMatrix, modelMatrix;
    private final Polygon3D[] facePolys;

    protected Object3D(Builder builder) {
        this.objX = builder.objX;
        this.objY = builder.objY;
        this.objZ = builder.objZ;
        this.objYaw = builder.objYaw;
        this.objPitch = builder.objPitch;
        this.objRoll = builder.objRoll;

        int faceCnt = builder.faces.length;
        this.facePolys = new Polygon3D[faceCnt];
        for (int i = 0; i < faceCnt; ++i) {
            int[] face = builder.faces[i];
            float[] coords = new float[face.length * 3];
            for (int j = 0; j < face.length; ++j) {
                coords[3 * j] = builder.verts[face[j]].x;
                coords[3 * j + 1] = builder.verts[face[j]].y;
                coords[3 * j + 2] = builder.verts[face[j]].z;
            }
            facePolys[i] = new Polygon3D(coords, builder.fillColor, builder.edgeColor);
        }

        this.mvpMatrix = new float[16];
        this.modelMatrix = new float[16];
    }

    private void applyTransformations(float[] mMatrix) {
        Matrix.setIdentityM(mMatrix, 0);
        Matrix.translateM(mMatrix, 0, objX, objY, objZ);
        Matrix.rotateM(mMatrix, 0, objYaw, 0, 1, 0);
        Matrix.rotateM(mMatrix, 0, objPitch, 1, 0, 0);
        Matrix.rotateM(mMatrix, 0, objRoll, 0, 0, 1);
    }

    public void draw(float[] vpMatrix) {
        applyTransformations(this.modelMatrix);
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0);
        for (Polygon3D poly : facePolys) {
            poly.draw(mvpMatrix);
        }
    }

    public void cleanup(){
        for(Polygon3D poly : facePolys){
            poly.cleanup();
        }
    }

    public static class Builder {
        protected float objX = 0, objY = 0, objZ = 0;
        protected float objYaw = 0, objPitch = 0, objRoll = 0;
        protected Vector3D[] verts;
        protected int[][] faces;
        protected FColor fillColor = CLR(0, 0, 0, 1), edgeColor = CLR(1, 1, 1, 1);

        public Builder() {
        }

        public Builder verts(Vector3D[] verts) {
            this.verts = verts;
            return this;
        }

        public Builder faces(int[][] faces) {
            this.faces = faces;
            return this;
        }

        public Builder fillColor(FColor fillColor) {
            this.fillColor = fillColor;
            return this;
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

        public Builder edgeColor(FColor edgeColor) {
            this.edgeColor = edgeColor;
            return this;
        }

        public Object3D buildObject() {
            checkValid();
            return new Object3D(this);
        }

        protected void checkValid() {
            assert (verts != null);
            assert (faces != null);
        }
    }

}