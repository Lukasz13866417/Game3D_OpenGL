package com.example.game3d_opengl.rendering.mesh;

import static com.example.game3d_opengl.rendering.util3d.RenderingUtils.ID_NOT_SET;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.GPUResourceOwner;
import com.example.game3d_opengl.rendering.layout.VertexLayout;
import com.example.game3d_opengl.rendering.shader.MeshShaderPair;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.List;

public class AbstractRenderer<
        A extends BaseMeshDrawArgs,
        S extends MeshShaderPair<?, ?, L>,
        L extends VertexLayout>
        implements GPUResourceOwner {

    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_SHORT = 2;
    private static final int BYTES_PER_INT = 4;

    private final S shader;
    private final L layout;
    private final FloatBuffer vertexData;
    private final Buffer indexData;
    private final boolean use32BitIndices;
    private final int indexCount;

    private int vboId = ID_NOT_SET;
    private int iboId = ID_NOT_SET;

    public AbstractRenderer(S shader, L layout, float[] vertexDataAsFloats, int[][] faces) {
        if (shader == null) {
            throw new IllegalArgumentException("shader == null");
        }
        if (layout == null) {
            throw new IllegalArgumentException("layout == null");
        }
        if (vertexDataAsFloats == null) {
            throw new IllegalArgumentException("vertexDataAsFloats == null");
        }
        if (faces == null) {
            throw new IllegalArgumentException("faces == null");
        }
        if (layout.strideBytes() <= 0 || layout.strideBytes() % BYTES_PER_FLOAT != 0) {
            throw new IllegalArgumentException("layout stride must be a positive float multiple");
        }
        if (vertexDataAsFloats.length % (layout.strideBytes() / BYTES_PER_FLOAT) != 0) {
            throw new IllegalArgumentException("vertexData length does not match layout stride");
        }

        this.shader = shader;
        this.layout = layout;
        this.vertexData = ByteBuffer
                .allocateDirect(vertexDataAsFloats.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        this.vertexData.put(vertexDataAsFloats).position(0);

        int vertexCount = vertexDataAsFloats.length / (layout.strideBytes() / BYTES_PER_FLOAT);
        int maxIndex = maxIndex(faces);
        this.use32BitIndices = maxIndex >= 65536 || vertexCount >= 65536;

        if (use32BitIndices) {
            IntBuffer buffer = buildIntIndexBuffer(faces);
            this.indexData = buffer;
            this.indexCount = buffer.capacity();
        } else {
            ShortBuffer buffer = buildShortIndexBuffer(faces);
            this.indexData = buffer;
            this.indexCount = buffer.capacity();
        }

        // The builder supplied an already-live shader. Creating a mesh is not a GL context
        // loss, so recompiling that (usually shared) program here is both incorrect and very
        // expensive. Only create this renderer's own buffers.
        reloadOwnedBuffers();
    }

    public final void render(AbstractMesh3D<A, S, L> mesh, A args) {
        bindForDraw();
        try {
            renderSlice(mesh, args);
        } finally {
            unbindAfterDraw();
        }
    }

    public final void renderBatch(List<? extends AbstractMesh3D<A, S, L>> meshes,
                                  List<? extends A> argsList) {
        if (meshes == null || argsList == null) {
            throw new IllegalArgumentException("meshes/argsList must be non-null");
        }
        if (meshes.size() != argsList.size()) {
            throw new IllegalArgumentException("meshes and argsList size mismatch");
        }

        bindForDraw();
        try {
            for (int i = 0; i < meshes.size(); i++) {
                renderSlice(meshes.get(i), argsList.get(i));
            }
        } finally {
            unbindAfterDraw();
        }
    }

    public final int getIndexCount() {
        return indexCount;
    }

    public final int getIndexType() {
        return use32BitIndices ? GLES20.GL_UNSIGNED_INT : GLES20.GL_UNSIGNED_SHORT;
    }

    final boolean canUpdateVertexData(int floatCount) {
        return floatCount == vertexData.capacity();
    }

    /**
     * Replaces vertex positions without destroying buffers or recompiling the shared shader.
     * The topology and vertex layout must stay unchanged.
     */
    final void updateVertexData(float[] vertexDataAsFloats) {
        if (vertexDataAsFloats == null
                || vertexDataAsFloats.length != vertexData.capacity()) {
            throw new IllegalArgumentException(
                    "Updated vertex data must preserve the existing topology");
        }
        vertexData.position(0);
        vertexData.put(vertexDataAsFloats);
        vertexData.position(0);
        if (vboId == ID_NOT_SET) {
            return;
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferSubData(
                GLES20.GL_ARRAY_BUFFER,
                0,
                vertexData.capacity() * BYTES_PER_FLOAT,
                vertexData);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (vboId != ID_NOT_SET) {
            GLES20.glDeleteBuffers(1, new int[]{vboId}, 0);
            vboId = ID_NOT_SET;
        }
        if (iboId != ID_NOT_SET) {
            GLES20.glDeleteBuffers(1, new int[]{iboId}, 0);
            iboId = ID_NOT_SET;
        }
        shader.cleanupGPUResourcesRecursively();
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        shader.reloadGPUResourcesRecursivelyOnContextLoss();
        reloadOwnedBuffers();
    }

    private void reloadOwnedBuffers() {
        if (vboId != ID_NOT_SET) {
            GLES20.glDeleteBuffers(1, new int[]{vboId}, 0);
            vboId = ID_NOT_SET;
        }
        if (iboId != ID_NOT_SET) {
            GLES20.glDeleteBuffers(1, new int[]{iboId}, 0);
            iboId = ID_NOT_SET;
        }
        vboId = createBufferObject(GLES20.GL_ARRAY_BUFFER, vertexData,
                vertexData.capacity() * BYTES_PER_FLOAT);
        iboId = createBufferObject(
                GLES20.GL_ELEMENT_ARRAY_BUFFER,
                indexData,
                use32BitIndices
                        ? ((IntBuffer) indexData).capacity() * BYTES_PER_INT
                        : ((ShortBuffer) indexData).capacity() * BYTES_PER_SHORT
        );
    }

    private void bindForDraw() {
        shader.setAsCurrentProgram();
        shader.bindLayout(layout);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId);
        shader.enableAndPointVertexAttribs();
    }

    private void unbindAfterDraw() {
        shader.disableVertexAttribs();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private void renderSlice(AbstractMesh3D<A, S, L> mesh, A args) {
        if (mesh.renderer() != this) {
            throw new IllegalArgumentException("Mesh does not belong to this renderer");
        }
        mesh.issueDraw(args);
    }

    private int createBufferObject(int target, Buffer data, int byteSize) {
        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        int id = ids[0];
        GLES20.glBindBuffer(target, id);
        data.position(0);
        GLES20.glBufferData(target, byteSize, data, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(target, 0);
        return id;
    }

    private static int maxIndex(int[][] faces) {
        int max = -1;
        for (int[] face : faces) {
            if (face == null) {
                continue;
            }
            for (int idx : face) {
                if (idx > max) {
                    max = idx;
                }
            }
        }
        return max;
    }

    private static ShortBuffer buildShortIndexBuffer(int[][] faces) {
        short[] shortIndices = buildShortIndices(faces);
        ShortBuffer buffer = ByteBuffer
                .allocateDirect(shortIndices.length * BYTES_PER_SHORT)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        buffer.put(shortIndices).position(0);
        return buffer;
    }

    private static IntBuffer buildIntIndexBuffer(int[][] faces) {
        int total = 0;
        for (int[] face : faces) {
            if (face == null || face.length < 3) {
                continue;
            }
            total += (face.length - 2) * 3;
        }
        IntBuffer buffer = ByteBuffer
                .allocateDirect(total * BYTES_PER_INT)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
        for (int[] face : faces) {
            if (face == null || face.length < 3) {
                continue;
            }
            if (face.length == 3) {
                buffer.put(face[0]).put(face[1]).put(face[2]);
            } else {
                for (int i = 1; i < face.length - 1; i++) {
                    buffer.put(face[0]).put(face[i]).put(face[i + 1]);
                }
            }
        }
        buffer.position(0);
        return buffer;
    }

    private static short[] buildShortIndices(int[][] faces) {
        int total = 0;
        for (int[] face : faces) {
            if (face == null || face.length < 3) {
                continue;
            }
            total += (face.length - 2) * 3;
        }
        short[] out = new short[total];
        int off = 0;
        for (int[] face : faces) {
            if (face.length < 3) {
                continue;
            }
            short i0 = (short) face[0];
            for (int i = 1; i < face.length - 1; i++) {
                out[off++] = i0;
                out[off++] = (short) face[i];
                out[off++] = (short) face[i + 1];
            }
        }
        return out;
    }
}
