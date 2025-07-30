package com.example.game3d_opengl.rendering.object3d;



import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import android.opengl.Matrix;
import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

public class Object3D {

    public float objX, objY, objZ, objYaw, objPitch, objRoll;
    private final float[] mvpMatrix, modelMatrix;
    private final Polygon3D[] facePolys;
    private int sharedVBO;
    private final FloatBuffer sharedVertexData;

    protected Object3D(Builder builder, int preAllocatedVBO, FloatBuffer vertexData, int[] faceCenterIndices) {
        this.objX = builder.objX;
        this.objY = builder.objY;
        this.objZ = builder.objZ;
        this.objYaw = builder.objYaw;
        this.objPitch = builder.objPitch;
        this.objRoll = builder.objRoll;

        this.sharedVBO = preAllocatedVBO;
        this.sharedVertexData = vertexData;

        int faceCnt = builder.faces.length;
        this.facePolys = new Polygon3D[faceCnt];
        for (int i = 0; i < faceCnt; ++i) {
            int[] face = builder.faces[i];
            int centerIndex = faceCenterIndices[i];
            
            // Create expanded face indices: original face + center at the end
            int[] expandedFaceIndices = new int[face.length + 1];
            System.arraycopy(face, 0, expandedFaceIndices, 0, face.length);
            expandedFaceIndices[face.length] = centerIndex; // center is last
            
            facePolys[i] = Polygon3D.createWithExistingBuffer(
                sharedVBO,
                expandedFaceIndices,
                face.length, // center is at index face.length in expandedFaceIndices
                builder.fillColor,
                builder.edgeColor
            );
        }

        this.mvpMatrix = new float[16];
        this.modelMatrix = new float[16];
    }

    /**
     * Re-upload the shared VBO after a GL context loss.
     */
    public void reload() {
        // Recreate the shared VBO
        int[] buffers = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        sharedVBO = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sharedVBO);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                            sharedVertexData.capacity() * 4,
                            sharedVertexData,
                            GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        
        // Update all polygons with the new VBO ID and reload their IBOs
        for (Polygon3D poly : facePolys) {
            poly.setVBO(sharedVBO);
            poly.reload();
        }
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
        // Clean up the shared VBO
        GLES20.glDeleteBuffers(1, new int[]{sharedVBO}, 0);
        // Clean up all polygons (they will only clean up their IBOs since they don't own the VBO)
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
            
            // Compute center vertices for each face and add them to the vertex array
            Vector3D[] centersToAdd = new Vector3D[faces.length];
            int[] faceCenterIndices = new int[faces.length];
            
            for (int i = 0; i < faces.length; i++) {
                int[] face = faces[i];
                // Compute the centroid of this face
                float cx = 0, cy = 0, cz = 0;
                for (int vertexIndex : face) {
                    cx += verts[vertexIndex].x;
                    cy += verts[vertexIndex].y;
                    cz += verts[vertexIndex].z;
                }
                cx /= face.length;
                cy /= face.length;
                cz /= face.length;
                
                centersToAdd[i] = new Vector3D(cx, cy, cz);
                faceCenterIndices[i] = verts.length + i; // center will be at this index
            }
            
            // Create expanded vertex array: original vertices + computed centers
            Vector3D[] expandedVerts = new Vector3D[verts.length + centersToAdd.length];
            System.arraycopy(verts, 0, expandedVerts, 0, verts.length);
            System.arraycopy(centersToAdd, 0, expandedVerts, verts.length, centersToAdd.length);
            
            // Create a single VBO containing all vertices
            float[] allVertices = new float[expandedVerts.length * 3];
            for (int i = 0; i < expandedVerts.length; i++) {
                allVertices[3 * i] = expandedVerts[i].x;
                allVertices[3 * i + 1] = expandedVerts[i].y;
                allVertices[3 * i + 2] = expandedVerts[i].z;
            }
            
            FloatBuffer vertexData = ByteBuffer
                .allocateDirect(allVertices.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
            vertexData.put(allVertices).position(0);
            
            int[] buffers = new int[1];
            GLES20.glGenBuffers(1, buffers, 0);
            int vboId = buffers[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                                allVertices.length * 4,
                                vertexData,
                                GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            
            return new Object3D(this, vboId, vertexData, faceCenterIndices);
        }

        protected void checkValid() {
            assert (verts != null);
            assert (faces != null);
        }
    }

}