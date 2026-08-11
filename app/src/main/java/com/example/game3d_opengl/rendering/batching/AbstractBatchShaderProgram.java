package com.example.game3d_opengl.rendering.batching;

import android.opengl.GLES20;

import com.example.game3d_opengl.rendering.shader.ShaderPair;

public abstract class AbstractBatchShaderProgram implements BatchShaderProgram {
    private final String vsSource;
    private final String fsSource;
    private int programHandle;

    protected AbstractBatchShaderProgram(String vsSource, String fsSource) {
        this.vsSource = vsSource;
        this.fsSource = fsSource;
    }

    protected final void initProgram() {
        reloadGPUResourcesRecursivelyOnContextLoss();
    }

    protected final int getProgramHandle() {
        return programHandle;
    }

    @Override
    public final void useProgram() {
        GLES20.glUseProgram(programHandle);
    }

    @Override
    public void reloadGPUResourcesRecursivelyOnContextLoss() {
        if (programHandle != 0 && GLES20.glIsProgram(programHandle)) {
            GLES20.glDeleteProgram(programHandle);
        }
        programHandle = createProgram(vsSource, fsSource);
        onProgramLinked();
    }

    @Override
    public void cleanupGPUResourcesRecursively() {
        if (programHandle != 0 && GLES20.glIsProgram(programHandle)) {
            GLES20.glDeleteProgram(programHandle);
        }
        programHandle = 0;
    }

    protected abstract void onProgramLinked();

    private static int createProgram(String vertexShaderCode, String fragmentShaderCode) {
        int vertexShader = ShaderPair.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = ShaderPair.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        int programId = GLES20.glCreateProgram();
        GLES20.glAttachShader(programId, vertexShader);
        GLES20.glAttachShader(programId, fragmentShader);
        GLES20.glLinkProgram(programId);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            String errorMsg = GLES20.glGetProgramInfoLog(programId);
            GLES20.glDeleteProgram(programId);
            throw new RuntimeException("Batch shader link error:\n" + errorMsg);
        }

        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        return programId;
    }
}
