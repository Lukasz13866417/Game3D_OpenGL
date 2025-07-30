package com.example.game3d_opengl.rendering.object3d;

import static com.example.game3d_opengl.rendering.util3d.GLUtil.loadShader;

import android.opengl.GLES20;
import com.example.game3d_opengl.rendering.util3d.FColor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class Polygon3D {

    private static final int COORDS_PER_VERTEX = 3;
    // full fan coords (center + perimeter + closure), kept for reload if needed
    private final float[] polygonCoords;
    // vertex buffer (VBO)
    private int vertexBufferId;
    private final boolean ownsVBO;
    private final FloatBuffer vertexBufferData;
    // index buffer (IBO)
    private final int indexBufferId;
    private final IntBuffer indexBufferData;
    private final int indexCount;

    private static int mProgram;
    private final int positionHandle, colorHandle, vPMatrixHandle;

    private static final String vertexShaderCode =
            "uniform mat4 uMVPMatrix;" +
                    "attribute vec4 vPosition;" +
                    "void main() {" +
                    "  gl_Position = uMVPMatrix * vPosition;" +
                    "}";

    private static final String fragmentShaderCode =
            "precision mediump float;" +
                    "uniform vec4 vColor;" +
                    "void main() {" +
                    "  gl_FragColor = vColor;" +
                    "}";

    private FColor fillColor;
    private FColor outlineColor; // e.g., black

    private Polygon3D(int vertexBufferId,
                      boolean ownsVBO,
                      FloatBuffer vertexBufferData,
                      int indexBufferId,
                      IntBuffer indexBufferData,
                      int indexCount,
                      FColor fillColor,
                      FColor outlineColor) {
        this.fillColor      = fillColor;
        this.outlineColor   = outlineColor;
        this.vertexBufferId   = vertexBufferId;
        this.ownsVBO          = ownsVBO;
        this.vertexBufferData = vertexBufferData;
        this.indexBufferId    = indexBufferId;
        this.indexBufferData  = indexBufferData;
        this.indexCount       = indexCount;
        this.polygonCoords    = null; // only stored if built by createWithVertexData

        if (mProgram == 0) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
            mProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(mProgram, vertexShader);
            GLES20.glAttachShader(mProgram, fragmentShader);
            GLES20.glLinkProgram(mProgram);
        }
        positionHandle  = GLES20.glGetAttribLocation(mProgram, "vPosition");
        colorHandle     = GLES20.glGetUniformLocation(mProgram, "vColor");
        vPMatrixHandle  = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
    }

    /** 
     * Build & own both VBO + IBO from vertex data. 
     * @param vertexCoords vertex coordinates as flat array
     * @param centerAlreadyIncluded if true, assumes center is already at start of array;
     *                              if false, computes center from all vertices and prepends it
     * @param fillColor interior color
     * @param outlineColor edge color
     */
    public static Polygon3D createWithVertexData(float[] vertexCoords,
                                                 boolean centerAlreadyIncluded,
                                                 FColor fillColor,
                                                 FColor outlineColor) {
        float[] polygonCoords;
        
        if (centerAlreadyIncluded) {
            // Use the coords as-is (assume center is first, then perimeter, optionally closed)
            int vertexCount = vertexCoords.length / COORDS_PER_VERTEX;
            boolean needsClosure = !isFirstAndLastVertexSame(vertexCoords, vertexCount);
            
            if (needsClosure) {
                // Add closure by duplicating the second vertex (first perimeter vertex) at the end
                polygonCoords = new float[vertexCoords.length + COORDS_PER_VERTEX];
                System.arraycopy(vertexCoords, 0, polygonCoords, 0, vertexCoords.length);
                // Copy second vertex (index 1) to the end
                polygonCoords[vertexCoords.length] = vertexCoords[3];     // x
                polygonCoords[vertexCoords.length + 1] = vertexCoords[4]; // y  
                polygonCoords[vertexCoords.length + 2] = vertexCoords[5]; // z
            } else {
                polygonCoords = vertexCoords.clone();
            }
        } else {
            // Compute center from all vertices and build triangle fan
            int pCount = vertexCoords.length / COORDS_PER_VERTEX;
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
            polygonCoords = new float[(pCount + 2) * COORDS_PER_VERTEX];
            polygonCoords[0] = cx;
            polygonCoords[1] = cy;
            polygonCoords[2] = cz;

            for (int i = 0; i < pCount; i++) {
                polygonCoords[3 * (i + 1)]     = vertexCoords[3 * i];
                polygonCoords[3 * (i + 1) + 1] = vertexCoords[3 * i + 1];
                polygonCoords[3 * (i + 1) + 2] = vertexCoords[3 * i + 2];
            }
            // Close the ring
            polygonCoords[3 * (pCount + 1)]     = vertexCoords[0];
            polygonCoords[3 * (pCount + 1) + 1] = vertexCoords[1];
            polygonCoords[3 * (pCount + 1) + 2] = vertexCoords[2];
        }

        FloatBuffer vbData = ByteBuffer
            .allocateDirect(polygonCoords.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
        vbData.put(polygonCoords).position(0);
        int[] bufs = new int[1];
        GLES20.glGenBuffers(1, bufs, 0);
        int vboId = bufs[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                            polygonCoords.length * 4,
                            vbData,
                            GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        // build a sequential index array [0,1,2,…,N-1]
        int vertexCount = polygonCoords.length / COORDS_PER_VERTEX;
        int[] indices = new int[vertexCount];
        for (int i = 0; i < vertexCount; i++) indices[i] = i;
        IntBuffer ibData = ByteBuffer
            .allocateDirect(indices.length * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer();
        ibData.put(indices).position(0);
        GLES20.glGenBuffers(1, bufs, 0);
        int iboId = bufs[0];
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,
                            indices.length * 4,
                            ibData,
                            GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        return new Polygon3D(vboId, true, vbData, iboId, ibData, indices.length, fillColor, outlineColor);
    }

    /**
     * Build from an existing VBO. 
     * @param existingVboId VBO handle containing vertex data
     * @param indices triangle fan indices into the VBO
     * @param centerIndex which index in the array points to the center vertex
     * @param fillColor interior color
     * @param outlineColor edge color
     */
    public static Polygon3D createWithExistingBuffer(int existingVboId,
                                                     int[] indices,
                                                     int centerIndex,
                                                     FColor fillColor,
                                                     FColor outlineColor) {
        // Build reordered fan index list: center first, then perimeter vertices in order, then duplicate first perimeter vertex to close ring.
        int perimeterCount = indices.length - 1; // excluding center
        int[] reorderedIndices = new int[indices.length + 1];
        reorderedIndices[0] = indices[centerIndex]; // center first

        int writePos = 1;
        // copy perimeter vertices preserving order starting from next index after centerIndex to keep original order
        for (int i = 1; i < indices.length; i++) {
            int idx = (centerIndex + i) % indices.length;
            reorderedIndices[writePos++] = indices[idx];
        }
        // duplicate first perimeter vertex to close
        reorderedIndices[reorderedIndices.length - 1] = reorderedIndices[1];

        IntBuffer ibData = ByteBuffer
            .allocateDirect(reorderedIndices.length * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer();
        ibData.put(reorderedIndices).position(0);
        int[] bufs = new int[1];
        GLES20.glGenBuffers(1, bufs, 0);
        int iboId = bufs[0];
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,
                            reorderedIndices.length * 4,
                            ibData,
                            GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        return new Polygon3D(existingVboId, false, null, iboId, ibData, reorderedIndices.length, fillColor, outlineColor);
    }

    /**
     * After a GL context loss, re‐upload all owned buffers.
     */
    public void reload() {
        // recreate IBO
        GLES20.glDeleteBuffers(1, new int[]{indexBufferId}, 0);
        int[] bufs = new int[1];
        GLES20.glGenBuffers(1, bufs, 0);
        int newIbo = bufs[0];
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, newIbo);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER,
                            indexCount * 4,
                            indexBufferData,
                            GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        // recreate VBO if we own it
        if (ownsVBO) {
            GLES20.glDeleteBuffers(1, new int[]{vertexBufferId}, 0);
            GLES20.glGenBuffers(1, bufs, 0);
            int newVbo = bufs[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, newVbo);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                                vertexBufferData.capacity() * 4,
                                vertexBufferData,
                                GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
    }

    public void draw(float[] mvpMatrix) {
        GLES20.glUseProgram(mProgram);

        GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, mvpMatrix, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle,
                                     COORDS_PER_VERTEX,
                                     GLES20.GL_FLOAT,
                                     false,
                                     COORDS_PER_VERTEX * 4,
                                     0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);
        GLES20.glUniform4fv(colorHandle, 1, fillColor.rgba, 0);
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_FAN, indexCount, GLES20.GL_UNSIGNED_INT, 0);
        GLES20.glUniform4fv(colorHandle, 1, outlineColor.rgba, 0);
        GLES20.glLineWidth(2.5f);
        GLES20.glDrawElements(GLES20.GL_LINE_LOOP, indexCount - 1, GLES20.GL_UNSIGNED_INT, 4);
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public void setFillAndOutline(FColor newFillColor, FColor newOutlineColor) {
        this.fillColor = newFillColor;
        this.outlineColor = newOutlineColor;
    }

    public void cleanup() {
        // always delete the IBO
        GLES20.glDeleteBuffers(1, new int[]{ indexBufferId }, 0);
        // delete the VBO only if we own it
        if (ownsVBO) {
            GLES20.glDeleteBuffers(1, new int[]{ vertexBufferId }, 0);
        }
    }

    /* Package-private: reset shader program when GL context is recreated */
    public static void resetProgram() {
        if (mProgram != 0) {
            int[] prog = { mProgram };
            if (android.opengl.GLES20.glIsProgram(mProgram)) {
                android.opengl.GLES20.glDeleteProgram(mProgram);
            }
            mProgram = 0;
        }
    }

    /**
     * Updates the VBO ID used by this polygon. Used for context recovery.
     * Package-private to allow Object3D to update it after recreating shared VBOs.
     */
    void setVBO(int newVertexBufferId) {
        this.vertexBufferId = newVertexBufferId;
    }

    private static boolean isFirstAndLastVertexSame(float[] coords, int vertexCount) {
        if (vertexCount < 2) return false;
        int lastVertexIndex = (vertexCount - 1) * 3;
        return coords[3] == coords[lastVertexIndex] &&     // x
               coords[4] == coords[lastVertexIndex + 1] && // y
               coords[5] == coords[lastVertexIndex + 2];   // z
    }
}