package com.example.game3d_opengl.rendering.mesh;

import static com.example.game3d_opengl.rendering.util3d.RenderingUtils.ID_NOT_SET;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.shader.ShaderPair;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

// TODO remove unused stuff.
public abstract class AbstractMesh3D<A extends MeshDrawArgs, S extends ShaderPair<?, ?>> implements GPUResourceOwner {

    // Constants and static fields
    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_SHORT = 2;

    // Instance fields
    private final float[] mvpMatrix = new float[16];

    private final FloatBuffer vertexData; // for reload
    private int vboId;
    private final boolean ownsVbo;

    private final ShortBuffer fillIndexData; // for reload
    private int iboFillId;
    private final int fillIndexCount;
    private final boolean ownsIbo;


    protected S shader;

    protected AbstractMesh3D(BaseBuilder<?, ?, S> builder) {
        this.vertexData = builder.vertexData;
        this.vboId = builder.vboId;
        this.ownsVbo = builder.ownsVbo;
        this.fillIndexData = builder.indexData;
        this.iboFillId = builder.iboId;
        this.fillIndexCount = builder.indexCount;
        this.ownsIbo = builder.ownsIbo;
        this.shader = builder.shader;
    }

    protected abstract void setVariableArgsValues(A meshDrawArgs, S targetShader);


    // Public methods (API)

    /**
     * Draws the object using the given view-projection matrix.
     */
    public void draw(A args) {
        // Bind shared program and VBO once
        shader.setAsCurrentProgram();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        shader.enableAndPointVertexAttribs();

        setVariableArgsValues(args, shader);
        shader.transferArgsToGPU();

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboFillId);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, fillIndexCount, GLES20.GL_UNSIGNED_SHORT, 0);

        // cleanup state
        shader.disableVertexAttribs();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /**
     * Re-uploads buffers after a GL context loss.
     */
    private void reload() {
        int[] bufs = new int[1];
        // VBO
        if (ownsVbo) {
            GLES20.glGenBuffers(1, bufs, 0);
            vboId = bufs[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexData.capacity() * BYTES_PER_FLOAT, vertexData, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }

        // Fill IBO
        if (ownsIbo) {
            GLES20.glGenBuffers(1, bufs, 0);
            iboFillId = bufs[0];
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboFillId);
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, fillIndexData.capacity() * BYTES_PER_SHORT, fillIndexData, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        }
    }

    /**
     * Deletes GL buffers.
     */
    private void cleanup() {
        if (ownsVbo) GLES20.glDeleteBuffers(1, new int[]{vboId}, 0);
        if (ownsIbo) GLES20.glDeleteBuffers(1, new int[]{iboFillId}, 0);
    }

    @Override
    public void reloadGPUResourcesRecursively() {
        reload();
        shader.reloadGPUResourcesRecursively();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        cleanup();
        shader.cleanupGPUResourcesRecursively();
    }

    // 5) Protected methods

    /**
     * Base class for Builders of 3D objects
     */
    protected static abstract class BaseBuilder<T extends AbstractMesh3D<?, S>,
            B extends BaseBuilder<T, B, S>,
            S extends ShaderPair<?, ?>> {
        protected Vector3D[] verts;
        protected int[][] faces; // each face is a simple, planar polygon given by ordered vertex indices

        protected int vboId = ID_NOT_SET, iboId = ID_NOT_SET;
        protected boolean ownsVbo = true, ownsIbo = true;
        protected FloatBuffer vertexData;
        protected ShortBuffer indexData;
        protected int indexCount;

        protected S shader;

        protected abstract B self();

        protected abstract T create();

        protected void checkValid() {
            assert faces != null;
            assert verts != null;
            assert shader != null;
        }

        public B verts(Vector3D[] verts) {
            this.verts = verts;
            return self();
        }

        public B faces(int[][] faces) {
            this.faces = faces;
            return self();
        }

        public final T buildObject() {
            checkValid();
            prepareGPUResources();
            return create();
        }

        public B shader(S what) {
            this.shader = what;
            return self();
        }

        public B vboId(int vbo) {
            this.vboId = vbo;
            this.ownsVbo = false;
            return self();
        }

        public B iboId(int ibo) {
            this.iboId = ibo;
            this.ownsIbo = false;
            return self();
        }

        protected abstract float[] setVertexData();


        /**
         * Prepares VBO/IBOs using current vertex data + fan triangulation of `faces`.
         * Assumptions: each face is simple & ordered; quads become two tris; n-gons become (n-2) tris.
         */
        protected void prepareGPUResources() {
            float[] vertexDataAsFloats = setVertexData(); // <- subclasses can mutate `faces` here
            vertexData = ByteBuffer
                    .allocateDirect(vertexDataAsFloats.length * BYTES_PER_FLOAT)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            vertexData.put(vertexDataAsFloats).position(0);

            int[] buf = new int[1];
            if (vboId == ID_NOT_SET) {
                GLES20.glGenBuffers(1, buf, 0);
                vboId = buf[0];
            }
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
            GLES20.glBufferData(
                    GLES20.GL_ARRAY_BUFFER,
                    vertexData.capacity() * BYTES_PER_FLOAT,
                    vertexData,
                    GLES20.GL_STATIC_DRAW
            );
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

            int totalFillTris = 0;
            for (int[] face : faces) {
                if (face != null && face.length >= 3) totalFillTris += (face.length - 2);
            }
            short[] fillIdx = new short[totalFillTris * 3];

            int w = 0;
            for (int[] face : faces) {
                if (face == null || face.length < 3) continue;

                for (int idx : face) {
                    if (idx < 0 || idx > 0xFFFF) {
                        throw new IllegalStateException("Index exceeds 16-bit range: " + idx);
                    }
                }

                int i0 = face[0];
                for (int i = 1; i < face.length - 1; ++i) {
                    fillIdx[w++] = (short) i0;
                    fillIdx[w++] = (short) face[i];
                    fillIdx[w++] = (short) face[i + 1];
                }
            }

            indexData = ByteBuffer
                    .allocateDirect(fillIdx.length * BYTES_PER_SHORT)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer();
            indexData.put(fillIdx).position(0);
            indexCount = fillIdx.length;

            if (iboId == ID_NOT_SET) {
                GLES20.glGenBuffers(1, buf, 0);
                iboId = buf[0];
            }
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId);
            GLES20.glBufferData(
                    GLES20.GL_ELEMENT_ARRAY_BUFFER,
                    indexData.capacity() * BYTES_PER_SHORT,
                    indexData,
                    GLES20.GL_STATIC_DRAW
            );
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        }


    }
}
