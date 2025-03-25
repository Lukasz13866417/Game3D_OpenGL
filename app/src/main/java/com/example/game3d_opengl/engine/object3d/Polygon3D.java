package com.example.game3d_opengl.engine.object3d;

import static com.example.game3d_opengl.engine.util3d.GLUtil.loadShader;

import android.opengl.GLES20;
import com.example.game3d_opengl.engine.util3d.FColor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class Polygon3D {

    private static final int COORDS_PER_VERTEX = 3;
    final float[] polygonCoords;

    // VBO handle
    private int vertexBufferId;

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

    public Polygon3D(float[] perimeterCoords, FColor fillColor, FColor outlineColor) {
        this.fillColor = fillColor;
        this.outlineColor = outlineColor;

        int pCount = perimeterCoords.length / COORDS_PER_VERTEX;
        float cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < pCount; i++) {
            cx += perimeterCoords[3 * i];
            cy += perimeterCoords[3 * i + 1];
            cz += perimeterCoords[3 * i + 2];
        }
        cx /= pCount;
        cy /= pCount;
        cz /= pCount;

        // Create vertex data using triangle fan method
        polygonCoords = new float[(pCount + 2) * COORDS_PER_VERTEX];
        polygonCoords[0] = cx;
        polygonCoords[1] = cy;
        polygonCoords[2] = cz;

        for (int i = 0; i < pCount; i++) {
            polygonCoords[3 * (i + 1)]     = perimeterCoords[3 * i];
            polygonCoords[3 * (i + 1) + 1] = perimeterCoords[3 * i + 1];
            polygonCoords[3 * (i + 1) + 2] = perimeterCoords[3 * i + 2];
        }
        polygonCoords[3 * (pCount + 1)]     = perimeterCoords[0];
        polygonCoords[3 * (pCount + 1) + 1] = perimeterCoords[1];
        polygonCoords[3 * (pCount + 1) + 2] = perimeterCoords[2];

        ByteBuffer bb = ByteBuffer.allocateDirect(polygonCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(polygonCoords);
        vertexBuffer.position(0);

        int[] buffers = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        vertexBufferId = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,
                polygonCoords.length * 4,
                vertexBuffer,
                GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        if (mProgram == 0) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);
            mProgram = GLES20.glCreateProgram();
            GLES20.glAttachShader(mProgram, vertexShader);
            GLES20.glAttachShader(mProgram, fragmentShader);
            GLES20.glLinkProgram(mProgram);
        }
        positionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        colorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");
        vPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
    }

    public void draw(float[] mvpMatrix) {
        GLES20.glUseProgram(mProgram);

        GLES20.glUniformMatrix4fv(vPMatrixHandle, 1, false, mvpMatrix, 0);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(
                positionHandle,
                COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                COORDS_PER_VERTEX * 4,
                0
        );

        int totalCount = polygonCoords.length / COORDS_PER_VERTEX;

        GLES20.glUniform4fv(colorHandle, 1, fillColor.rgba, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, totalCount);

        GLES20.glUniform4fv(colorHandle, 1, outlineColor.rgba, 0);
        GLES20.glLineWidth(2.5f);
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 1, totalCount - 2);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    public float[] getVertexCoords() {
        return polygonCoords;
    }

    public void setFillAndOutline(FColor newFillColor, FColor newOutlineColor) {
        this.fillColor = newFillColor;
        this.outlineColor = newOutlineColor;
    }
}
