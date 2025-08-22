package com.example.game3d_opengl.rendering.object3d;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * An object that exists in absolute world space and does not have its own model matrix.
 * Drawn directly using the provided view-projection matrix, without any translation,
 * rotation, or scaling transformations.
 * Can be used for objects like Potions, which all look the same
 * and only differ with regard to position or some angles.
 */
public class AbsoluteObject3D {

    private final Polygon3D<?,?,?>[] facePolys;
    private int sharedVBO;
    private final FloatBuffer sharedVertexData;
    private final float[] mvpMatrix = new float[16];

    protected AbsoluteObject3D(BaseBuilder<?,?> builder) {
        this.sharedVBO = builder.vboId;
        this.sharedVertexData = builder.vertexData;
        this.facePolys = builder.polygons;
    }

    /**
     * Draws all faces of the object directly using the view-projection matrix.
     * @param vpMatrix The combined view-projection matrix from the camera.
     */
    public void draw(float[] vpMatrix) {
        for (Polygon3D<?,?,?> poly : facePolys) {
            poly.draw(vpMatrix);
        }
    }

    /**
     * Draws all faces of the object, applying a temporary model matrix transformation.
     * This does not modify the object's state.
     * @param modelMatrix The temporary model matrix to apply.
     * @param vpMatrix The combined view-projection matrix from the camera.
     */
    public void draw(float[] modelMatrix, float[] vpMatrix) {
        // Combine the model and view-projection matrices
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0);

        for (Polygon3D<?,?,?> poly : facePolys) {
            poly.draw(mvpMatrix);
        }
    }

    /**
     * Re-uploads the shared VBO and instructs all associated polygons to reload their IBOs.
     * This is necessary to restore buffers after a GL context loss.
     */
    public void reload() {
        int[] buffers = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        sharedVBO = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, sharedVBO);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                            sharedVertexData.capacity() * 4,
                            sharedVertexData,
                            GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        for (Polygon3D<?,?,?> poly : facePolys) {
            poly.setVBO(sharedVBO);
            poly.reload();
        }
    }

    /**
     * Cleans up the OpenGL resources (VBO and IBOs) used by this object.
     */
    public void cleanup(){
        GLES20.glDeleteBuffers(1, new int[]{sharedVBO}, 0);
        for(Polygon3D<?,?,?> poly : facePolys){
            poly.cleanup();
        }
    }

    /**
     * Base class for Builders of 3D objects
     */
    protected static abstract class BaseBuilder<T extends AbsoluteObject3D, B extends BaseBuilder<T,B>> {
        protected Vector3D[] verts;
        protected int[][] faces;
        protected FColor fillColor = CLR(0, 0, 0, 1), edgeColor = CLR(1, 1, 1, 1);
        
        protected int vboId;
        protected FloatBuffer vertexData;
        protected Polygon3D<?,?,?>[] polygons;

        protected abstract B self();

        protected abstract T create();

        protected abstract void checkValid();

        public B verts(Vector3D[] verts) {
            this.verts = verts;
            return self();
        }

        public B faces(int[][] faces) {
            this.faces = faces;
            return self();
        }

        public B fillColor(FColor fillColor) {
            this.fillColor = fillColor;
            return self();
        }

        public B edgeColor(FColor edgeColor) {
            this.edgeColor = edgeColor;
            return self();
        }

        public final T buildObject() {
            checkValid();
            prepareGPUResources();
            return create();
        }
        
        protected void prepareGPUResources() {
            // Compute center vertices and add them to the vertex array
            Vector3D[] centersToAdd = new Vector3D[faces.length];
            int[] faceCenterIndices = new int[faces.length];

            for (int i = 0; i < faces.length; i++) {
                int[] face = faces[i];
                float cx = 0, cy = 0, cz = 0;
                for (int vertexIndex : face) {
                    cx += verts[vertexIndex].x;
                    cy += verts[vertexIndex].y;
                    cz += verts[vertexIndex].z;
                }
                centersToAdd[i] = new Vector3D(cx / face.length, cy / face.length, cz / face.length);
                faceCenterIndices[i] = verts.length + i;
            }

            Vector3D[] expandedVerts = new Vector3D[verts.length + centersToAdd.length];
            System.arraycopy(verts, 0, expandedVerts, 0, verts.length);
            System.arraycopy(centersToAdd, 0, expandedVerts, verts.length, centersToAdd.length);

            // Create a single VBO for all vertices
            float[] allVertices = new float[expandedVerts.length * 3];
            for (int i = 0; i < expandedVerts.length; i++) {
                allVertices[3 * i] = expandedVerts[i].x;
                allVertices[3 * i + 1] = expandedVerts[i].y;
                allVertices[3 * i + 2] = expandedVerts[i].z;
            }

            vertexData = ByteBuffer
                    .allocateDirect(allVertices.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            vertexData.put(allVertices).position(0);

            int[] buffers = new int[1];
            GLES20.glGenBuffers(1, buffers, 0);
            vboId = buffers[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, allVertices.length * 4, vertexData, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

            // Create Polygon3D instances
            polygons = new Polygon3D[faces.length];
            for (int i = 0; i < faces.length; i++) {
                int[] face = faces[i];
                int centerIndex = faceCenterIndices[i];

                int[] expandedFaceIndices = new int[face.length + 1];
                System.arraycopy(face, 0, expandedFaceIndices, 0, face.length);
                expandedFaceIndices[face.length] = centerIndex;

                polygons[i] = new BasicPolygon3D.Builder()
                        .fillColor(fillColor)
                        .edgeColor(edgeColor)
                        .fromExistingBuffer(vboId, expandedFaceIndices, face.length)
                        .build();
            }
        }

    }

    public static class Builder extends BaseBuilder<AbsoluteObject3D,Builder> {
        @Override
        public void checkValid() {
            assert faces != null;
            assert verts != null;
        }

        @Override
        public Builder self(){
            return this;
        }

        @Override
        public AbsoluteObject3D create() {
            return new AbsoluteObject3D(this);
        }
    }



}