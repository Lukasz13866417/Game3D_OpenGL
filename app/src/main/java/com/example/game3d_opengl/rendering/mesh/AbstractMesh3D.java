package com.example.game3d_opengl.rendering.mesh;

import static com.example.game3d_opengl.rendering.util3d.RenderingUtils.ID_NOT_SET;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.shader.ShaderPair;
import com.example.game3d_opengl.rendering.util3d.vector.Vector3D;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public abstract class AbstractMesh3D<A extends BaseMeshDrawArgs, S extends ShaderPair<?, ?>> implements GPUResourceOwner {

    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_SHORT = 2;
    private static final int BYTES_PER_INT = 4;

    private final FloatBuffer vertexData;
    private int vboId;
    private final boolean ownsVbo;

    private final Buffer fillIndexData;
    private final boolean use32BitIndices;
    private int iboFillId;
    private final int fillIndexCount;
    private final boolean ownsIbo;

    protected S shader;

    protected AbstractMesh3D(BaseBuilder<?, ?, S> builder) {
        this.vertexData = builder.vertexData;
        this.vboId = builder.vboId;
        this.ownsVbo = builder.ownsVbo;
        this.fillIndexData = builder.indexBuffer;
        this.use32BitIndices = builder.use32BitIndices;
        this.iboFillId = builder.iboId;
        this.fillIndexCount = builder.indexCount;
        this.ownsIbo = builder.ownsIbo;
        this.shader = builder.shader;
    }

    protected abstract void setVariableArgsValues(A meshDrawArgs, S targetShader);

    public void draw(A args) {
        shader.setAsCurrentProgram();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        shader.enableAndPointVertexAttribs();

        setVariableArgsValues(args, shader);
        shader.transferUniformArgsToGPU();

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboFillId);
        int indexType = use32BitIndices ? GLES20.GL_UNSIGNED_INT : GLES20.GL_UNSIGNED_SHORT;
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, fillIndexCount, indexType, 0);

        shader.disableVertexAttribs();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private void reload() {
        int[] bufs = new int[1];
        if (ownsVbo) {
            GLES20.glGenBuffers(1, bufs, 0);
            vboId = bufs[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexData.capacity() * BYTES_PER_FLOAT, vertexData, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }

        if (ownsIbo) {
            GLES20.glGenBuffers(1, bufs, 0);
            iboFillId = bufs[0];
            int bytesPerElement = use32BitIndices ? BYTES_PER_INT : BYTES_PER_SHORT;
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboFillId);
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, fillIndexData.capacity() * bytesPerElement, fillIndexData, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        }
    }

    private void cleanup() {
        if (ownsVbo) GLES20.glDeleteBuffers(1, new int[]{vboId}, 0);
        if (ownsIbo) GLES20.glDeleteBuffers(1, new int[]{iboFillId}, 0);
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        reload();
        shader.reloadGPUResourcesRecursivelyOnContextLoss();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        cleanup();
        shader.cleanupGPUResourcesRecursively();
    }

    protected static abstract class BaseBuilder<T extends AbstractMesh3D<?, S>,
            B extends BaseBuilder<T, B, S>,
            S extends ShaderPair<?, ?>> {
        protected Vector3D[] verts;
        protected int[][] faces;

        protected int vboId = ID_NOT_SET, iboId = ID_NOT_SET;
        protected boolean ownsVbo = true, ownsIbo = true;
        protected FloatBuffer vertexData;
        protected Buffer indexBuffer;
        protected boolean use32BitIndices;
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

        protected void prepareGPUResources() {
            float[] vertexDataAsFloats = setVertexData();
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

            int maxIndex = 0;
            for (int[] face : faces) {
                if (face == null) continue;
                for (int idx : face) {
                    if (idx > maxIndex) maxIndex = idx;
                }
            }

            use32BitIndices = maxIndex > 0xFFFF;
            indexCount = totalFillTris * 3;

            if (use32BitIndices) {
                int[] fillIdx = new int[indexCount];
                int w = 0;
                for (int[] face : faces) {
                    if (face == null || face.length < 3) continue;
                    int i0 = face[0];
                    for (int i = 1; i < face.length - 1; ++i) {
                        fillIdx[w++] = i0;
                        fillIdx[w++] = face[i];
                        fillIdx[w++] = face[i + 1];
                    }
                }
                IntBuffer ib = ByteBuffer
                        .allocateDirect(fillIdx.length * BYTES_PER_INT)
                        .order(ByteOrder.nativeOrder())
                        .asIntBuffer();
                ib.put(fillIdx).position(0);
                indexBuffer = ib;
            } else {
                short[] fillIdx = new short[indexCount];
                int w = 0;
                for (int[] face : faces) {
                    if (face == null || face.length < 3) continue;
                    int i0 = face[0];
                    for (int i = 1; i < face.length - 1; ++i) {
                        fillIdx[w++] = (short) i0;
                        fillIdx[w++] = (short) face[i];
                        fillIdx[w++] = (short) face[i + 1];
                    }
                }
                ShortBuffer sb = ByteBuffer
                        .allocateDirect(fillIdx.length * BYTES_PER_SHORT)
                        .order(ByteOrder.nativeOrder())
                        .asShortBuffer();
                sb.put(fillIdx).position(0);
                indexBuffer = sb;
            }

            int bytesPerElement = use32BitIndices ? BYTES_PER_INT : BYTES_PER_SHORT;
            if (iboId == ID_NOT_SET) {
                GLES20.glGenBuffers(1, buf, 0);
                iboId = buf[0];
            }
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId);
            GLES20.glBufferData(
                    GLES20.GL_ELEMENT_ARRAY_BUFFER,
                    indexBuffer.capacity() * bytesPerElement,
                    indexBuffer,
                    GLES20.GL_STATIC_DRAW
            );
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        }

    }
}
