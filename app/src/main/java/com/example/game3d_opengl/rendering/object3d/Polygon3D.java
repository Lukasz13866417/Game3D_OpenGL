package com.example.game3d_opengl.rendering.object3d;

import android.opengl.GLES20;
import android.util.Pair;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Represents a 3D polygon that can be rendered using OpenGL ES 2.0.
 * Supports both fill and outline rendering.
 * Dont create many such polygons per frame.
 * This is mainly for for placeholders of proof-of-concept situations.
 */
public abstract class Polygon3D<VS extends ShaderArgValues,
        FS extends ShaderArgValues,
        S extends ShaderPair<VS, FS>> {

    // geometry constants
    private static final int COORDS_PER_VERTEX = 3;
    private static final int BYTES_PER_FLOAT = 4;
    private static final int BYTES_PER_SHORT = 2;
    private static final float DEFAULT_LINE_WIDTH = 2.5f;

    // OpenGL constants
    private static final int VBO_GEN_COUNT = 1;
    private static final int IBO_GEN_COUNT = 1;
    private static final int BUFFER_DELETE_COUNT = 1;
    
    // Vertex buffer to store vertex positions
    private int vertexBufferId;
    private final boolean ownsVBO;
    private final FloatBuffer vertexBufferData;
    
    // Index buffer to store vertex indices
    private final int indexBufferId;
    private final ShortBuffer indexBufferData;
    private final int indexCount;

    private final S shaders;

    /**
     * Vertex shader code for transforming vertices by the MVP matrix.
     * Takes vertex positions and applies the model-view-projection transformation.
     */
    private static final String vertexShaderCode =
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "void main() {" +
                    "  gl_Position = uMVPMatrix * vPosition;" +
                    "}";

    /**
     * Fragment shader code for coloring pixels.
     * Applies a uniform color to all fragments of the polygon.
     */
    private static final String fragmentShaderCode =
            "precision mediump float;" +
                    "uniform vec4 vColor;" +
                    "void main() {" +
                    "  gl_FragColor = vColor;" +
                    "}";


    protected Polygon3D(BaseBuilder<?,?> b, S shaders) {
        this.vertexBufferId = b.vboId;
        this.ownsVBO = b.ownsVBO;
        this.vertexBufferData = b.vbData;
        this.indexBufferId = b.iboId;
        this.indexBufferData = b.ibData;
        this.indexCount = b.indexCount;
        this.shaders = shaders;
    }

    // ===== Builder Base Class =====
    public static abstract class BaseBuilder<P extends Polygon3D<?,?,?>, B extends BaseBuilder<P,B>> {

        // Mode selection
        protected boolean useExisting = false;

        // Mode 1: existing VBO
        protected int existingVboId;
        protected int[] indices; // kept as int[] input for convenience
        protected int centerIndex;

        // Mode 2: vertex data
        protected float[] vertexCoords;
        protected boolean centerIncluded;

        // Prepared GPU resources
        protected int vboId;
        protected boolean ownsVBO;
        protected FloatBuffer vbData;
        protected int iboId;
        protected ShortBuffer ibData;
        protected int indexCount;

        protected abstract B self();

        public B fromExistingBuffer(int vboId, int[] indices, int centerIndex) {
            this.useExisting = true;
            this.existingVboId = vboId;
            this.indices = indices;
            this.centerIndex = centerIndex;
            return self();
        }

        public B fromVertexData(float[] coords, boolean centerAlreadyIncluded) {
            this.useExisting = false;
            this.vertexCoords = coords;
            this.centerIncluded = centerAlreadyIncluded;
            return self();
        }

        public final P build() {
            if (useExisting) {
                if (indices == null) throw new IllegalStateException("indices not set");
                // Build index order: [center, perimeter..., perimeter[0]] with closure
                int[] reorderedInt = new int[indices.length + 1];
                reorderedInt[0] = indices[centerIndex];
                int write = 1;
                int ringStart = (centerIndex + 1) % indices.length;
                for (int i = 1; i < indices.length; i++) {
                    int idx = (centerIndex + i) % indices.length;
                    reorderedInt[write++] = indices[idx];
                }
                // duplicate first perimeter vertex to close the fan
                reorderedInt[write] = indices[ringStart];
                short[] reordered = toShortArray(reorderedInt);
                this.ibData = createShortBuffer(reordered);
                this.iboId = createAndUploadIBO(this.ibData, reordered);
                this.vboId = existingVboId;
                this.vbData = null;
                this.ownsVBO = false;
                this.indexCount = reordered.length;
                return create();
            } else {
                if (vertexCoords == null) throw new IllegalStateException("vertexCords not set");
                float[] cords;
                if (centerIncluded) {
                    int vCount = vertexCoords.length / COORDS_PER_VERTEX;
                    boolean needsClosure = !isFirstAndLastVertexSame(vertexCoords, vCount);
                    if (needsClosure) {
                        cords = new float[vertexCoords.length + COORDS_PER_VERTEX];
                        System.arraycopy(vertexCoords, 0, cords, 0, vertexCoords.length);
                        cords[vertexCoords.length] = vertexCoords[3];
                        cords[vertexCoords.length + 1] = vertexCoords[4];
                        cords[vertexCoords.length + 2] = vertexCoords[5];
                    } else {
                        cords = vertexCoords.clone();
                    }
                } else {
                    cords = computeCenterAndBuildTriangleFan(vertexCoords);
                }
                this.vbData = createFloatBuffer(cords);
                this.vboId = createAndUploadVBO(this.vbData, cords);
                int vertexCount = cords.length / COORDS_PER_VERTEX;
                short[] idx = createSequentialIndices(vertexCount);
                this.ibData = createShortBuffer(idx);
                this.iboId = createAndUploadIBO(this.ibData, idx);
                this.indexCount = idx.length;
                this.ownsVBO = true;
                return create();
            }
        }

        protected abstract P create();
    }

    /**
     * Computes the center point from all vertices and builds a complete triangle fan.
     * The center is computed as the average of all perimeter vertices.
     * 
     * @param vertexCoords the perimeter vertex coordinates
     * @return complete polygon coordinates including center and closure
     */
    private static float[] computeCenterAndBuildTriangleFan(float[] vertexCoords) {
        int pCount = vertexCoords.length / COORDS_PER_VERTEX;
        
        // Compute center as average of all perimeter vertices
        float cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < pCount; i++) {
            cx += vertexCoords[3 * i];
            cy += vertexCoords[3 * i + 1];
            cz += vertexCoords[3 * i + 2];
        }
        cx /= pCount;
        cy /= pCount;
        cz /= pCount;

        // Create vertex data: center + perimeter + closure
        float[] polygonCoords = new float[(pCount + 2) * COORDS_PER_VERTEX];
        
        // Set center vertex
        polygonCoords[0] = cx;
        polygonCoords[1] = cy;
        polygonCoords[2] = cz;

        // Copy perimeter vertices
        for (int i = 0; i < pCount; i++) {
            polygonCoords[3 * (i + 1)] = vertexCoords[3 * i];
            polygonCoords[3 * (i + 1) + 1] = vertexCoords[3 * i + 1];
            polygonCoords[3 * (i + 1) + 2] = vertexCoords[3 * i + 2];
        }
        
        // Close the ring by duplicating the first perimeter vertex
        polygonCoords[3 * (pCount + 1)] = vertexCoords[0];
        polygonCoords[3 * (pCount + 1) + 1] = vertexCoords[1];
        polygonCoords[3 * (pCount + 1) + 2] = vertexCoords[2];
        
        return polygonCoords;
    }

    /**
     * Creates a FloatBuffer from the given coordinates.
     * 
     * @param coords the coordinate array
     * @return a FloatBuffer containing the coordinates
     */
    private static FloatBuffer createFloatBuffer(float[] coords) {
        FloatBuffer buffer = ByteBuffer
            .allocateDirect(coords.length * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
        buffer.put(coords).position(0);
        return buffer;
    }

    /**
     * Creates an IntBuffer from the given indices.
     * 
     * @param indices the index array
     * @return an IntBuffer containing the indices
     */
    private static ShortBuffer createShortBuffer(short[] indices) {
        ShortBuffer buffer = ByteBuffer
            .allocateDirect(indices.length * BYTES_PER_SHORT)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer();
        buffer.put(indices).position(0);
        return buffer;
    }

    /**
     * Creates and uploads a VBO (Vertex Buffer Object) to GPU memory.
     * 
     * @param bufferData the vertex data buffer
     * @param coords the coordinate array
     * @return the OpenGL VBO ID
     */
    private static int createAndUploadVBO(FloatBuffer bufferData, float[] coords) {
        int[] bufs = new int[VBO_GEN_COUNT];
        GLES20.glGenBuffers(VBO_GEN_COUNT, bufs, 0);
        int vboId = bufs[0];
        
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                            coords.length * BYTES_PER_FLOAT,
                            bufferData,
                            GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        
        return vboId;
    }

    /**
     * Creates and uploads an IBO (Index Buffer Object) to GPU memory.
     * 
     * @param bufferData the index data buffer
     * @param indices the index array
     * @return the OpenGL IBO ID
     */
    private static int createAndUploadIBO(ShortBuffer bufferData, short[] indices) {
        int[] bufs = new int[IBO_GEN_COUNT];
        GLES20.glGenBuffers(IBO_GEN_COUNT, bufs, 0);
        int iboId = bufs[0];
        
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,
                            indices.length * BYTES_PER_SHORT,
                            bufferData,
                            GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        
        return iboId;
    }

    /**
     * Creates a sequential index array [0, 1, 2, ..., N-1].
     * Used for triangle fan rendering where vertices are accessed in order.
     * 
     * @param vertexCount the number of vertices
     * @return an array of sequential indices
     */
    private static short[] createSequentialIndices(int vertexCount) {
        short[] indices = new short[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            indices[i] = (short) i;
        }
        return indices;
    }

    private static short[] toShortArray(int[] arr) {
        short[] out = new short[arr.length];
        for (int i = 0; i < arr.length; i++) out[i] = (short) arr[i];
        return out;
    }

    /**
     * After a GL context loss, re-upload all owned buffers.
     * This method recreates the VBO and IBO objects that were lost during context recreation.
     */
    public void reload() {
        // Recreate IBO
        GLES20.glDeleteBuffers(BUFFER_DELETE_COUNT, new int[]{indexBufferId}, 0);
        int[] bufs = new int[IBO_GEN_COUNT];
        GLES20.glGenBuffers(IBO_GEN_COUNT, bufs, 0);
        int newIbo = bufs[0];
        
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, newIbo);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,
                            indexCount * BYTES_PER_SHORT,
                            indexBufferData,
                            GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        // Recreate VBO if we own it
        if (ownsVBO) {
            GLES20.glDeleteBuffers(BUFFER_DELETE_COUNT, new int[]{vertexBufferId}, 0);
            GLES20.glGenBuffers(VBO_GEN_COUNT, bufs, 0);
            int newVbo = bufs[0];
            
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, newVbo);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                                vertexBufferData.capacity() * BYTES_PER_FLOAT,
                                vertexBufferData,
                                GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            
            vertexBufferId = newVbo;
        }
    }

    protected abstract Pair<VS,FS> setupShaderArgs(float[] mvp, boolean isFill);

    /**
     * Renders the polygon using OpenGL ES 2.0.
     * First renders the filled interior using GL_TRIANGLE_FAN,
     * then renders the outline using GL_LINE_LOOP.
     * 
     * @param mvpMatrix the combined Model-View-Projection matrix
     */
    public void draw(float[] mvpMatrix) {
        if (mvpMatrix == null) {
            throw new IllegalArgumentException("MVP matrix cannot be null");
        }


        // Bind vertex & index buffers
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);


        shaders.setAsCurrentProgram();
        shaders.enableAndPointVertexAttribs();
        Pair<VS,FS> shaders_args = setupShaderArgs(mvpMatrix, true);
        shaders.setArgValues(shaders_args.first, shaders_args.second);

        shaders.transferArgsToGPU();
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_FAN, indexCount, GLES20.GL_UNSIGNED_SHORT, 0);

        // Outline pass: reuse MVP, pass isFill=false so subclass can select outline args
        shaders_args = setupShaderArgs(mvpMatrix, false);
        shaders.setArgValues(shaders_args.first, shaders_args.second);
        shaders.transferArgsToGPU();
        GLES20.glLineWidth(DEFAULT_LINE_WIDTH);
        int outlineCount = indexCount - 2; // skip center and closure duplicate
        GLES20.glDrawElements(GLES20.GL_LINE_LOOP, outlineCount, GLES20.GL_UNSIGNED_SHORT, 2);

        shaders.cleanupAfterDraw();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }


    /**
     * Cleans up GPU resources used by this polygon.
     * Deletes the VBO and IBO objects from GPU memory.
     * Should be called when the polygon is no longer needed.
     */
    public void cleanup() {
        // Always delete the IBO
        GLES20.glDeleteBuffers(BUFFER_DELETE_COUNT, new int[]{indexBufferId}, 0);
        
        // Delete the VBO only if we own it
        if (ownsVBO) {
            GLES20.glDeleteBuffers(BUFFER_DELETE_COUNT, new int[]{vertexBufferId}, 0);
        }
    }

    /**
     * Updates the VBO ID used by this polygon.
     * Used for context recovery when shared VBOs are recreated.
     * Package-private to allow Object3D to update it after recreating shared VBOs.
     * 
     * @param newVertexBufferId the new VBO ID
     */
    void setVBO(int newVertexBufferId) {
        this.vertexBufferId = newVertexBufferId;
    }

    /**
     * Checks if the first and last vertices in the coordinate array are the same.
     * Used to determine if a polygon needs closure (duplication of first perimeter vertex).
     * 
     * @param coords the coordinate array
     * @param vertexCount the number of vertices
     * @return true if first and last vertices are identical
     */
    private static boolean isFirstAndLastVertexSame(float[] coords, int vertexCount) {
        if (vertexCount < 2) return false;
        
        int lastVertexIndex = (vertexCount - 1) * 3;
        return coords[3] == coords[lastVertexIndex] &&     // x
               coords[4] == coords[lastVertexIndex + 1] && // y
               coords[5] == coords[lastVertexIndex + 2];   // z
    }

}