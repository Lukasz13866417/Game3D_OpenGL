package com.example.game3d_opengl.rendering.object3d;

import static com.example.game3d_opengl.rendering.util3d.FColor.CLR;

import android.opengl.GLES20;
import android.opengl.Matrix;

import com.example.game3d_opengl.rendering.util3d.FColor;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * An object that exists in absolute world space and does not have its own model matrix.
 * Drawn directly using the provided view-projection matrix, without any translation,
 * rotation, or scaling transformations.
 * Supports solid fill and wireframe outline without relying on Polygon3D.
 */
public class AbsoluteObject3D {

    // 1) Constants and static fields
    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_SHORT = 2;

    // 2) Instance fields
    private final float[] mvpMatrix = new float[16];

    private final FColor fillColor;
    private final FColor edgeColor;

    private final FloatBuffer vertexData; // for reload
    private int vboId;

    private final ShortBuffer fillIndexData; // for reload
    private int iboFillId;
    private final int fillIndexCount;

    private final ShortBuffer lineIndexData; // for reload
    private int iboLineId;
    private final int lineIndexCount;

    private final BasicShaderPair shader = BasicShaderPair.sharedShader;

    // 3) Constructors
    protected AbsoluteObject3D(BaseBuilder<?,?> builder) {
        this.fillColor = builder.fillColor;
        this.edgeColor = builder.edgeColor;
        this.vertexData = builder.vertexData;
        this.vboId = builder.vboId;
        this.fillIndexData = builder.fillIndexData;
        this.iboFillId = builder.iboFillId;
        this.fillIndexCount = builder.fillIndexCount;
        this.lineIndexData = builder.lineIndexData;
        this.iboLineId = builder.iboLineId;
        this.lineIndexCount = builder.lineIndexCount;
    }

    // 4) Public methods (API)
    /** Draws the object using the given view-projection matrix. */
    public void _draw(float[] vpMatrix) {
        // Bind shared program and VBO once
        shader.setAsCurrentProgram();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        shader.enableAndPointVertexAttribs();

        // Fill pass
        BasicShaderArgs.VS vs = new BasicShaderArgs.VS();
        vs.mvp = vpMatrix;
        BasicShaderArgs.FS fs = new BasicShaderArgs.FS();
        fs.color = fillColor;
        shader.setArgValues(vs, fs);
        shader.transferArgsToGPU();

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboFillId);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, fillIndexCount, GLES20.GL_UNSIGNED_SHORT, 0);

        // Wireframe pass
        fs.color = edgeColor; // reuse same objects
        shader.setArgValues(vs, fs);
        shader.transferArgsToGPU();

        GLES20.glLineWidth(2.5f);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboLineId);
        GLES20.glDrawElements(GLES20.GL_LINES, lineIndexCount, GLES20.GL_UNSIGNED_SHORT, 0);

        // cleanup state
        shader.disableVertexAttribs();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }


    public void draw(float[] vpMatrix) {
        _draw(mvpMatrix);
    }

    public void draw(float[] modelMatrix, float[] vpMatrix) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0);
        this._draw(mvpMatrix);
    }

    /** Re-uploads buffers after a GL context loss. */
    public void reload() {
        // VBO
        int[] bufs = new int[1];
        GLES20.glGenBuffers(1, bufs, 0);
        vboId = bufs[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexData.capacity() * BYTES_PER_FLOAT, vertexData, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // Fill IBO
        GLES20.glGenBuffers(1, bufs, 0);
        iboFillId = bufs[0];
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboFillId);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, fillIndexData.capacity() * BYTES_PER_SHORT, fillIndexData, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        // Line IBO
        GLES20.glGenBuffers(1, bufs, 0);
        iboLineId = bufs[0];
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboLineId);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, lineIndexData.capacity() * BYTES_PER_SHORT, lineIndexData, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /** Deletes GL buffers. */
    public void cleanup(){
        GLES20.glDeleteBuffers(1, new int[]{vboId}, 0);
        GLES20.glDeleteBuffers(1, new int[]{iboFillId}, 0);
        GLES20.glDeleteBuffers(1, new int[]{iboLineId}, 0);
    }

    // 5) Protected methods

    /** Base class for Builders of 3D objects */
    protected static abstract class BaseBuilder<T extends AbsoluteObject3D, B extends BaseBuilder<T,B>> {
        protected Vector3D[] verts;
        protected int[][] faces;
        protected FColor fillColor = CLR(0, 0, 0, 1), edgeColor = CLR(1, 1, 1, 1);

        protected int vboId;
        protected FloatBuffer vertexData;

        protected ShortBuffer fillIndexData;
        protected int iboFillId;
        protected int fillIndexCount;

        protected ShortBuffer lineIndexData;
        protected int iboLineId;
        protected int lineIndexCount;

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
            // Compute and append per-face centers
            Vector3D[] centersToAdd = new Vector3D[faces.length];
            int[] faceCenterIndices = new int[faces.length];
            for (int i = 0; i < faces.length; i++) {
                int[] face = faces[i];
                float cx = 0, cy = 0, cz = 0;
                for (int vi : face) {
                    cx += verts[vi].x;
                    cy += verts[vi].y;
                    cz += verts[vi].z;
                }
                centersToAdd[i] = new Vector3D(cx / face.length, cy / face.length, cz / face.length);
                faceCenterIndices[i] = verts.length + i;
            }

            Vector3D[] expandedVerts = new Vector3D[verts.length + centersToAdd.length];
            System.arraycopy(verts, 0, expandedVerts, 0, verts.length);
            System.arraycopy(centersToAdd, 0, expandedVerts, verts.length, centersToAdd.length);

            // Upload VBO for all vertices
            float[] allVertices = new float[expandedVerts.length * 3];
            for (int i = 0; i < expandedVerts.length; i++) {
                allVertices[3 * i] = expandedVerts[i].x;
                allVertices[3 * i + 1] = expandedVerts[i].y;
                allVertices[3 * i + 2] = expandedVerts[i].z;
            }
            vertexData = ByteBuffer
                    .allocateDirect(allVertices.length * BYTES_PER_FLOAT)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            vertexData.put(allVertices).position(0);

            int[] buf = new int[1];
            GLES20.glGenBuffers(1, buf, 0);
            vboId = buf[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, allVertices.length * BYTES_PER_FLOAT, vertexData, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

            // Build combined index arrays
            // Fill triangles: for each face, fan as (center, i, i+1)
            int totalFillTris = 0;
            int totalLineSegments = 0;
            for (int[] face : faces) {
                totalFillTris += face.length; // one triangle per edge around center
                totalLineSegments += face.length; // one line per edge
            }

            short[] fillIdx = new short[totalFillTris * 3];
            short[] lineIdx = new short[totalLineSegments * 2];

            int fillWrite = 0;
            int lineWrite = 0;
            for (int fi = 0; fi < faces.length; fi++) {
                int[] face = faces[fi];
                int center = faceCenterIndices[fi];
                int n = face.length;
                for (int i = 0; i < n; i++) {
                    int a = center;
                    int b = face[i];
                    int c = face[(i + 1) % n];
                    fillIdx[fillWrite++] = (short) a;
                    fillIdx[fillWrite++] = (short) b;
                    fillIdx[fillWrite++] = (short) c;

                    lineIdx[lineWrite++] = (short) b;
                    lineIdx[lineWrite++] = (short) c;
                }
            }

            fillIndexData = ByteBuffer
                    .allocateDirect(fillIdx.length * BYTES_PER_SHORT)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer();
            fillIndexData.put(fillIdx).position(0);
            fillIndexCount = fillIdx.length;

            lineIndexData = ByteBuffer
                    .allocateDirect(lineIdx.length * BYTES_PER_SHORT)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer();
            lineIndexData.put(lineIdx).position(0);
            lineIndexCount = lineIdx.length;

            // Upload IBOs
            GLES20.glGenBuffers(1, buf, 0);
            iboFillId = buf[0];
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboFillId);
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, fillIdx.length * BYTES_PER_SHORT, fillIndexData, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

            GLES20.glGenBuffers(1, buf, 0);
            iboLineId = buf[0];
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboLineId);
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, lineIdx.length * BYTES_PER_SHORT, lineIndexData, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        }
    }

    // 6) Package-private and private methods

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