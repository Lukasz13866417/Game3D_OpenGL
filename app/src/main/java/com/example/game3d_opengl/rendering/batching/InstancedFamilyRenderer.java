package com.example.game3d_opengl.rendering.batching;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLES31;

import com.example.game3d_opengl.rendering.GPUResourceOwner;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.List;

public final class InstancedFamilyRenderer<P, O> implements GPUResourceOwner {
    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_SHORT = 2;
    private static final int BYTES_PER_INT = 4;
    private static final int PASS_BINDING_POINT = 0;
    private static final int OBJECT_BINDING_POINT = 1;

    private final StaticGeometrySource geometry;
    private final BatchShaderProgram shader;
    private final PassBlockEncoder<P> passEncoder;
    private final ObjectBlockEncoder<O> objectEncoder;
    private final FloatBuffer vertexData;
    private final Buffer indexData;
    private final boolean use32BitIndices;
    private final int indexCount;
    private final int indexType;
    private final FloatBuffer passBuffer;

    private FloatBuffer objectBuffer;
    private int objectCapacity = 0;

    private int vboId = 0;
    private int iboId = 0;
    private int passUboId = 0;
    private int objectSsboId = 0;

    public InstancedFamilyRenderer(StaticGeometrySource geometry,
                                   BatchShaderProgram shader,
                                   PassBlockEncoder<P> passEncoder,
                                   ObjectBlockEncoder<O> objectEncoder) {
        if (geometry == null || shader == null || passEncoder == null || objectEncoder == null) {
            throw new IllegalArgumentException("Instanced family renderer args must be non-null");
        }
        this.geometry = geometry;
        this.shader = shader;
        this.passEncoder = passEncoder;
        this.objectEncoder = objectEncoder;
        this.vertexData = ByteBuffer
                .allocateDirect(geometry.vertexData().length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        this.vertexData.put(geometry.vertexData()).position(0);

        int maxIndex = maxIndex(geometry.faces());
        int vertexCount = geometry.vertexData().length / (geometry.strideBytes() / BYTES_PER_FLOAT);
        this.use32BitIndices = maxIndex >= 65536 || vertexCount >= 65536;
        if (use32BitIndices) {
            IntBuffer indices = buildIntIndexBuffer(geometry.faces());
            this.indexData = indices;
            this.indexCount = indices.capacity();
            this.indexType = GLES30.GL_UNSIGNED_INT;
        } else {
            ShortBuffer indices = buildShortIndexBuffer(geometry.faces());
            this.indexData = indices;
            this.indexCount = indices.capacity();
            this.indexType = GLES20.GL_UNSIGNED_SHORT;
        }

        this.passBuffer = ByteBuffer
                .allocateDirect(passEncoder.floatCount() * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();

        reloadGPUResourcesRecursivelyOnContextLoss();
    }

    public void render(P pass, List<? extends O> objects) {
        if (pass == null || objects == null || objects.isEmpty()) {
            return;
        }

        passBuffer.clear();
        passEncoder.encode(pass, passBuffer);
        passBuffer.flip();

        ensureObjectBuffer(objects.size());
        objectBuffer.clear();
        for (O object : objects) {
            objectEncoder.encode(object, objectBuffer);
        }
        objectBuffer.flip();

        shader.useProgram();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId);
        shader.bindGeometryAttributes(geometry);

        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, passUboId);
        GLES30.glBufferData(
                GLES30.GL_UNIFORM_BUFFER,
                passBuffer.remaining() * BYTES_PER_FLOAT,
                passBuffer,
                GLES30.GL_DYNAMIC_DRAW
        );
        GLES30.glBindBufferBase(GLES30.GL_UNIFORM_BUFFER, PASS_BINDING_POINT, passUboId);

        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, objectSsboId);
        GLES31.glBufferData(
                GLES31.GL_SHADER_STORAGE_BUFFER,
                objectBuffer.remaining() * BYTES_PER_FLOAT,
                objectBuffer,
                GLES31.GL_DYNAMIC_DRAW
        );
        GLES31.glBindBufferBase(GLES31.GL_SHADER_STORAGE_BUFFER, OBJECT_BINDING_POINT, objectSsboId);

        GLES30.glDrawElementsInstanced(GLES20.GL_TRIANGLES, indexCount, indexType, 0, objects.size());

        shader.disableGeometryAttributes();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLES30.glBindBuffer(GLES30.GL_UNIFORM_BUFFER, 0);
        GLES31.glBindBuffer(GLES31.GL_SHADER_STORAGE_BUFFER, 0);
    }

    public void renderSingle(P pass, O object) {
        render(pass, java.util.Collections.singletonList(object));
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        shader.reloadGPUResourcesRecursivelyOnContextLoss();
        deleteBuffers();
        vboId = createBufferObject(GLES20.GL_ARRAY_BUFFER, vertexData, vertexData.capacity() * BYTES_PER_FLOAT);
        iboId = createBufferObject(
                GLES20.GL_ELEMENT_ARRAY_BUFFER,
                indexData,
                use32BitIndices
                        ? ((IntBuffer) indexData).capacity() * BYTES_PER_INT
                        : ((ShortBuffer) indexData).capacity() * BYTES_PER_SHORT
        );
        passUboId = createEmptyBufferObject(GLES30.GL_UNIFORM_BUFFER);
        objectSsboId = createEmptyBufferObject(GLES31.GL_SHADER_STORAGE_BUFFER);
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        deleteBuffers();
        shader.cleanupGPUResourcesRecursively();
    }

    private void ensureObjectBuffer(int objectCount) {
        int floatsNeeded = Math.max(1, objectCount * objectEncoder.floatCountPerObject());
        if (floatsNeeded <= objectCapacity && objectBuffer != null) {
            return;
        }
        objectCapacity = Math.max(floatsNeeded, Math.max(16, objectCapacity * 2));
        objectBuffer = ByteBuffer
                .allocateDirect(objectCapacity * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
    }

    private void deleteBuffers() {
        deleteBuffer(vboId);
        deleteBuffer(iboId);
        deleteBuffer(passUboId);
        deleteBuffer(objectSsboId);
        vboId = 0;
        iboId = 0;
        passUboId = 0;
        objectSsboId = 0;
    }

    private static void deleteBuffer(int id) {
        if (id != 0) {
            GLES20.glDeleteBuffers(1, new int[]{id}, 0);
        }
    }

    private static int createEmptyBufferObject(int target) {
        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        GLES20.glBindBuffer(target, ids[0]);
        GLES20.glBufferData(target, 0, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(target, 0);
        return ids[0];
    }

    private static int createBufferObject(int target, Buffer data, int byteSize) {
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
                max = Math.max(max, idx);
            }
        }
        return max;
    }

    private static ShortBuffer buildShortIndexBuffer(int[][] faces) {
        short[] out = buildShortIndices(faces);
        ShortBuffer buffer = ByteBuffer
                .allocateDirect(out.length * BYTES_PER_SHORT)
                .order(ByteOrder.nativeOrder())
                .asShortBuffer();
        buffer.put(out).position(0);
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
            int i0 = face[0];
            for (int i = 1; i < face.length - 1; i++) {
                buffer.put(i0).put(face[i]).put(face[i + 1]);
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
        int w = 0;
        for (int[] face : faces) {
            if (face == null || face.length < 3) {
                continue;
            }
            short i0 = (short) face[0];
            for (int i = 1; i < face.length - 1; i++) {
                out[w++] = i0;
                out[w++] = (short) face[i];
                out[w++] = (short) face[i + 1];
            }
        }
        return out;
    }
}
