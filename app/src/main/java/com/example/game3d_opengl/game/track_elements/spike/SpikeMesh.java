package com.example.game3d_opengl.game.track_elements.spike;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.object3d.infill.InfillShaderArgs;
import com.example.game3d_opengl.rendering.util3d.FColor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Shared spike mesh: single VBO/IBOs containing canonical spike vertex attributes
 * (weights for quad corners and t for apex mix). Per-instance uniforms define
 * the actual quad and apex, so we can render arbitrary quads without per-instance VBOs.
 */
public final class SpikeMesh {

    private static final int BYTES_PER_FLOAT = 4;
    private static final int STRIDE = 5 * BYTES_PER_FLOAT; // vec4 weights + float t

    private static int vboId = 0;
    private static int iboFillId = 0;
    private static int indexCount = 0;

    private static final InfillShaderArgs.VS vsArgs = new InfillShaderArgs.VS();
    private static final InfillShaderArgs.FS fsArgs = new InfillShaderArgs.FS();
    private static final SpikeInfillShaderPair shader = SpikeInfillShaderPair.getSharedShader();

    private static void ensureCreated() {
        if (vboId != 0 && iboFillId != 0) return;

        // Canonical spike: 5 vertices (4 base, 1 apex). For robustness we duplicate per-triangle vertices
        // but here we can use indexed triangles: 4 faces: (NL, NR, Apex), (NR, FR, Apex), (FR, FL, Apex), (FL, NL, Apex)
        float[] verts = new float[]{
                // weights (NL, NR, FR, FL), t
                1,0,0,0, 0, // NL (base)
                0,1,0,0, 0, // NR (base)
                0,0,1,0, 0, // FR (base)
                0,0,0,1, 0, // FL (base)
                0,0,0,0, 1  // Apex (t=1)
        };

        short[] indices = new short[]{
                0,1,4,
                1,2,4,
                2,3,4,
                3,0,4
        };
        indexCount = indices.length;

        FloatBuffer vbuf = ByteBuffer.allocateDirect(verts.length * BYTES_PER_FLOAT)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vbuf.put(verts).position(0);

        ShortBuffer ibuf = ByteBuffer.allocateDirect(indices.length * 2)
                .order(ByteOrder.nativeOrder()).asShortBuffer();
        ibuf.put(indices).position(0);

        int[] ids = new int[1];
        GLES20.glGenBuffers(1, ids, 0);
        vboId = ids[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.length * BYTES_PER_FLOAT, vbuf, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glGenBuffers(1, ids, 0);
        iboFillId = ids[0];
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboFillId);
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indices.length * 2, ibuf, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public static void drawInstance(float[] vpMatrix,
                                    float[] nl, float[] nr, float[] fr, float[] fl,
                                    float[] apex, float[] normal,
                                    float baseOffset,
                                    FColor color) {
        ensureCreated();
        shader.setAsCurrentProgram();
        vsArgs.mvp = vpMatrix;
        // pass instance uniforms via VS args
        vsArgs.uNL = nl;
        vsArgs.uNR = nr;
        vsArgs.uFR = fr;
        vsArgs.uFL = fl;
        vsArgs.uApex = apex;
        vsArgs.uNormal = normal;
        vsArgs.uBaseOffset = baseOffset;
        fsArgs.color = color;
        shader.setArgs(vsArgs, fsArgs);
        shader.transferArgsToGPU();

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId);
        shader.enableAndPointVertexAttribs();
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboFillId);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        shader.disableVertexAttribs();
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }
}


